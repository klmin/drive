# 앞단 프록시 설정 — 큰 파일 업로드

웹 화면은 32MB 이상 파일을 **청크 업로드**(`/api/v1/uploads`)로 보낸다. 파일을 파트로 잘라 파트마다 `PUT` 요청 하나를 보내므로, 백엔드 앞의 프록시가 **파트 하나 크기의 요청 본문**을 통과시켜야 한다. 막히면 화면에는 "업로드에 실패했습니다"만 보이고 프록시 로그에 `413`이 남는다.

설계·동작은 [업로드 세션 현재 구현](../../../../../docs/design/upload-session-as-built.md) §3.5 참고.

## 필요한 값

| 항목 | 값 | 이유 |
|---|---|---|
| 요청 본문 상한 | **1GiB 권장**(최소 = 가장 큰 파트) | 파트 크기 = `max(16MB, 파일 크기 / 10000)`. 파일 100GB 이하면 16MB, 1TB 면 약 105MB, 5TB(절대 상한) 면 약 525MB. 32MB 미만 파일과 외부 API(API 키) 업로드는 단일 요청(`POST /api/v1/files`)이라 파일 크기 그대로 이 상한을 따른다 |
| 백엔드 응답 대기 | **300초 권장**(기본 60초) | 큰 파일 complete 는 스토리지가 파트를 조립하는 동안 응답이 없다. 끊겨도 화면이 상태 조회로 결과를 받지만 불필요한 재요청이 생긴다 |
| 요청 본문 버퍼링 | 끄기 권장(nginx) | 켜져 있으면 프록시가 파트를 디스크에 다 받은 뒤 넘겨 느리고 디스크를 쓴다 |
| 본문 수신 최소 속도 | 기본값 유지 | 느린 회선에서도 파트 하나는 수십 초 안에 끝난다 |

- 파트 크기·단일 요청 기준은 백엔드 설정 `drive.upload.min-part-size`(16MB)·`drive.upload.single-request-threshold`(32MB)·`drive.upload.absolute-max-size`(5TB)로 바꿀 수 있다. 바꾸면 위 상한도 다시 계산한다.
- 백엔드(Tomcat·Spring)에는 본문 상한이 없다(`spring.servlet.multipart.max-file-size: -1`). 크기 제한은 테넌트 업로드 정책과 절대 상한으로 백엔드가 판단한다.

## 이 설치본(TFD)의 Apache — 적용됨

`vhosts.conf`:

```apache
ProxyTimeout 300
```

- 본문 상한(`LimitRequestBody`)은 두지 않는다. httpd 2.4.71 이미지로 20MB 파트 `PUT` 이 프록시를 통과하는 것을 확인했다(2026-11-20).
- 운영에서 `LimitRequestBody` 를 따로 넣는다면 **1GiB 이상**으로 둔다. 가장 큰 파트보다 작으면 큰 파일 업로드가 413 으로 막힌다.
- `mod_reqtimeout`(본문 수신 최소 500B/s)은 이미지 기본값 그대로 둔다.

## 다른 프록시를 앞에 둘 때

### nginx

기본 `client_max_body_size` 가 **1MB** 라 그대로 두면 청크 업로드가 전부 막힌다.

```nginx
location /api/ {
    client_max_body_size 1g;
    proxy_request_buffering off;   # 파트를 디스크에 모았다 넘기지 않게
    proxy_read_timeout 300s;
    proxy_send_timeout 300s;
    proxy_pass http://drive-backend;
}
```

### Kubernetes ingress-nginx

```yaml
metadata:
  annotations:
    nginx.ingress.kubernetes.io/proxy-body-size: "1g"
    nginx.ingress.kubernetes.io/proxy-request-buffering: "off"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "300"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "300"
```

Kubernetes 배포 매니페스트는 이 저장소가 아니라 Developers 저장소에 있다 — 거기 ingress 에 위 값을 넣는다.

### 그 밖의 프록시·로드밸런서·WAF

본문 상한(1GiB 이상)과 백엔드 응답 대기(300초 이상)만 맞추면 된다. WAF 가 요청 본문을 검사한다면 `/api/v1/uploads/*/parts/*` 의 `application/octet-stream` 본문은 검사 대상에서 빼야 파트 크기 상한에 걸리지 않는다.

## 확인

인증 없이 20MB 본문을 파트 주소로 보내 본다. 프록시가 통과시키면 백엔드가 인증 오류로 답하고, 막으면 프록시가 413 을 준다.

```bash
head -c 20971520 /dev/zero > /tmp/part.bin
curl -s -o /dev/null -w '%{http_code}\n' -X PUT \
  -H 'Content-Type: application/octet-stream' --data-binary @/tmp/part.bin \
  https://<드라이브 주소>/api/v1/uploads/00000000-0000-0000-0000-000000000000/parts/1
```

- `413` — 프록시 본문 상한이 작다(이 요청은 인증 전이라 백엔드가 크기로 거절하지 않는다)
- 그 밖(`401`·`403`·`400` 등) — 프록시를 통과해 백엔드가 인증·테넌트 오류로 답한 것, 정상
- 응답 없이 끊기면 프록시·WAF 의 본문 검사·타임아웃을 본다

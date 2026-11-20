# 스토리지 버킷 lifecycle 설정

Drive 는 대용량 파일을 S3 멀티파트 업로드로 올린다. 업로드가 중간에 끊기면 스토리지에 "미완성 멀티파트 업로드"가 남고, 이 조각들은 목록에 보이지 않지만 용량을 차지한다.

정리는 세 단계로 한다.

1. 업로드 세션 정리 스케줄러(10분마다): 끝난 세션의 멀티파트를 정리 대기열에 넣는다.
2. 스토리지 삭제 워커(30초마다): 대기열의 멀티파트를 abort 한다. 실패하면 백오프하며 최대 10회 다시 시도한다.
3. **버킷 lifecycle 규칙(최종 안전망)**: 위 두 단계가 놓친 조각을 스토리지가 직접 지운다. 이 문서가 다루는 부분이다.

## 1. 기본 동작: Drive 가 기동 때 직접 설정

기본값(`DRIVE_UPLOAD_MANAGEBUCKETLIFECYCLE=true`)이면 Drive 가 기동할 때 버킷 lifecycle 을 읽고 아래 규칙을 보장한다.

| 항목 | 값 |
|---|---|
| 규칙 ID | `drive-abort-incomplete-multipart` |
| 대상 | 버킷 전체(빈 prefix) |
| 동작 | 시작 뒤 N일 지난 미완성 멀티파트 업로드 abort(`AbortIncompleteMultipartUpload`) |
| N | `DRIVE_UPLOAD_ABORTINCOMPLETEMULTIPARTDAYS`, 기본 1 |

기존 규칙은 지우지 않는다. S3 의 lifecycle 설정은 통째로 바꾸는 방식이라, 기존 규칙을 읽은 뒤 Drive 규칙만 더해서 다시 쓴다.

- 운영자가 이미 **버킷 전체**에 **N일 이하**의 미완성 멀티파트 정리 규칙을 두었으면, Drive 규칙을 더하지 않고 그 규칙을 따른다.
- 예전 형식(규칙 바로 아래 `Prefix`)으로 된 규칙은 같은 뜻의 `Filter` 형식으로 바꿔 다시 쓴다. 두 형식이 한 설정에 섞이면 스토리지가 거절할 수 있기 때문이다.
- 객체를 만료(삭제)하는 규칙(`Expiration` 의 `Days`·`Date`)이 있으면 **경고 로그만** 남기고 그대로 둔다. 아래 4절을 참고한다.

필요한 권한(AWS IAM 기준, Ceph RGW 버킷 정책도 같은 이름):

- `s3:GetLifecycleConfiguration`
- `s3:PutLifecycleConfiguration`

권한이 없거나 스토리지가 lifecycle 을 지원하지 않아도 **기동은 계속된다.** 이때 로그에 아래 경고가 남는다.

```
[Storage init] lifecycle rule not set (configure AbortIncompleteMultipartUpload manually). bucket: ..., reason: ...
```

## 2. 직접 관리하는 설치처: 자동 설정 끄기

버킷 정책을 운영팀이 관리하거나, Drive 계정에 lifecycle 권한을 줄 수 없으면 자동 설정을 끄고 3절대로 직접 넣는다.

`template/drive/data/bin/env.sh`(docker-compose 설치) 또는 Drive 컨테이너 환경 변수(k8s)에 아래를 추가한다.

```sh
export DRIVE_UPLOAD_MANAGEBUCKETLIFECYCLE="false"
```

끄면 Drive 는 lifecycle 을 읽지도 쓰지도 않는다. 그래서 4절의 만료 규칙 경고도 나오지 않는다.

## 3. 수동 설정

`lifecycle.json`:

```json
{
  "Rules": [
    {
      "ID": "drive-abort-incomplete-multipart",
      "Status": "Enabled",
      "Filter": { "Prefix": "" },
      "AbortIncompleteMultipartUpload": { "DaysAfterInitiation": 1 }
    }
  ]
}
```

**주의: 아래 명령은 버킷의 lifecycle 전체를 이 파일로 바꾼다.** 기존 규칙이 있으면 먼저 읽어서 `Rules` 배열에 Drive 규칙을 더한 파일을 만든다.

```sh
# 기존 규칙 확인(없으면 NoSuchLifecycleConfiguration)
aws s3api get-bucket-lifecycle-configuration --bucket <버킷> [--endpoint-url <Ceph RGW 주소>]

# 적용
aws s3api put-bucket-lifecycle-configuration --bucket <버킷> --lifecycle-configuration file://lifecycle.json [--endpoint-url <Ceph RGW 주소>]
```

AWS S3 는 `--endpoint-url` 없이, Ceph RGW 는 RGW 주소를 넣는다.

일수는 업로드 세션 TTL(1시간)보다 길어야 한다. 1일이면 충분하다. 너무 짧으면 진행 중인 이어 올리기가 끊길 수 있다.

## 4. 같이 확인할 것

- **객체 만료 규칙**: 버킷 전체나 Drive 객체 key 에 걸리는 `Expiration`(Days·Date) 규칙이 있으면 **사용자 파일이 스토리지에서 지워진다.** DB 의 파일 행은 남으므로 다운로드가 실패한다. Drive 객체 key 는 업로드 파일이 테넌트 번호(테넌트가 없으면 `0`)로 시작하고(예: `1/...`), 프로필 이미지는 `profiles/` 로 시작한다. Drive 전용 버킷에는 만료 규칙을 두지 않는다. Drive 는 기동할 때 이런 규칙을 찾으면 아래 경고를 남긴다.
  ```
  [Storage init] lifecycle rule expires objects — if it matches Drive object keys, user files will be deleted. bucket: ..., rule: ..., scope: ...
  ```
- **버킷 버전 관리(versioning)**: 켜져 있으면 Drive 가 객체를 지워도 이전 버전이 남아 용량이 줄지 않는다. 켤 거라면 `NoncurrentVersionExpiration` 규칙도 함께 둔다.
- **스토리지별 지원**: AWS S3 와 Ceph RGW 는 `AbortIncompleteMultipartUpload` 를 지원한다. MinIO 는 지원하지 않아(400) 1절의 경고가 나오지만, MinIO 자체가 미완성 업로드를 24시간 뒤 정리한다. RustFS 는 아직 확인하지 않았다(지원하지 않으면 같은 경고가 나오고, 정리는 스케줄러·삭제 워커만 한다).

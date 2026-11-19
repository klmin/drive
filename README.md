# Drive

멀티 테넌트 클라우드 드라이브 서비스. 파일·폴더 관리, 공유, 버전, 휴지통, 웹오피스 연동, 관리자/슈퍼관리자 콘솔, MCP 서버(외부 AI 클라이언트 연동)를 제공합니다.

## 기술 스택

| 영역 | 사용 기술 |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Security, Spring Authorization Server(OAuth2), Spring AI MCP Server |
| 데이터 접근 | JPA + jOOQ(조회·upsert·배치), Flyway 마이그레이션 |
| DB / 캐시 | PostgreSQL, Redis |
| 스토리지 | S3 호환(AWS SDK v2) — AWS S3, Ceph RGW, RustFS, MinIO |
| 기타 | ShedLock(분산 스케줄러), Apache Tika(파일 형식 판별), springdoc-openapi |
| Frontend | Next.js 15, React 19, MUI 7, next-intl |
| 관측성 | Micrometer, OpenTelemetry, Grafana Alloy · Prometheus · Loki · Tempo · Grafana, k6 부하 테스트 |
| 배포 | Docker Compose, Kubernetes 매니페스트 |

## 디렉터리 구조

```
DriveAPI/        Spring Boot 백엔드 (빌드 시 DriveFront 정적 산출물을 함께 패키징)
DriveFront/      Next.js 프론트엔드
DriveInstaller/  설치 스크립트 · Docker Compose · Kubernetes 매니페스트
observability/   관측성 스택(Alloy/Prometheus/Loki/Tempo/Grafana) + k6 부하 테스트
docs/openapi/    OpenAPI 스펙(JSON)
```

## 로컬 실행

### 1. 인프라 (PostgreSQL + RustFS)

```bash
cd DriveAPI
docker compose -f docker-compose.local.yml up -d
```

### 2. 백엔드

```bash
cd DriveAPI
cp .env.example .env          # DB·스토리지·JWT 키 등 설정
mvn spring-boot:run -Dfrontend.skip=true
```

- 스키마는 기동 시 Flyway가 적용하고, 버킷은 앱이 기동하면서 생성합니다.
- 관리 포트(Actuator): `8091` (`/actuator/health`, `/actuator/prometheus`)
- jOOQ 코드 재생성: `mvn -Pjooq-codegen -Dfrontend.skip=true generate-sources`

### 3. 프론트엔드

```bash
cd DriveFront
npm install
npm run dev                   # http://localhost:3000
```

### 4. 관측성 스택 (선택)

```bash
cd observability
cp .env.example .env
docker compose up -d          # Grafana http://localhost:3300 (admin / .env 의 GRAFANA_ADMIN_PASSWORD)
```

스택만 띄우면 메트릭만 보입니다. 트레이스(Tempo)와 로그(Loki)까지 보려면 백엔드 `DriveAPI/.env` 에 아래를 넣고 앱을 다시 띄웁니다.

```properties
# 트레이스를 Alloy(localhost:4318)로 전송
DRIVE_OTLP_TRACING_EXPORT_ENABLED=true
# 콘솔 + C:/log/drive/backend/drive-server.log (Alloy 가 읽어 Loki 로). 기존 logging.config 줄을 이 값으로 바꾼다
logging.config=classpath:log/log4j2-server.yml
```

- 로그 폴더는 `observability/.env` 의 `DRIVE_LOG_DIR`(기본 `C:/log/drive`)과 맞춥니다.
- 정상 요청의 트레이스는 tail 샘플링으로 10%만 남고 30초쯤 뒤에 보입니다. 에러·1초 이상 요청은 항상 남습니다.

각 의존성·설정의 의미와 데이터 흐름은 [observability/HOW-IT-WORKS.md](observability/HOW-IT-WORKS.md), 스택 운영·알림은 [observability/README.md](observability/README.md), 부하 테스트는 [observability/k6/README.md](observability/k6/README.md)를 참고하세요.

## 주요 기능

- **파일·폴더**: 업로드/다운로드, 이름 변경·이동·복사, 버전 관리와 복원, 휴지통, 즐겨찾기·고정 폴더·최근 문서
- **공유**: 사용자/링크 공유, 권한 단위 제어
- **멀티 테넌트**: 테넌트별 경로·도메인 주소 지정, 용량 정책, 접근 제한, 브랜딩
- **인증**: 자체 로그인, SSO(OIDC), MFA, API Key, OAuth2 인가 서버(MCP 클라이언트용)
- **웹오피스 연동**: HTTP Storage 프로토콜로 문서 열기·저장
- **관리**: 관리자/슈퍼관리자 콘솔, 감사 로그, 사용자 로그, 시스템 설정
- **MCP 서버**: Streamable HTTP 기반으로 외부 AI 클라이언트에 드라이브 도구 제공

## 문서

- [docs/openapi](docs/openapi) — API 스펙
- [observability/HOW-IT-WORKS.md](observability/HOW-IT-WORKS.md) — 관측성(메트릭·트레이스·로그) 구성 요소, 설정 의미, 데이터 흐름
- [observability/README.md](observability/README.md) — 관측성 스택 띄우기·샘플링·알림·서버 배치

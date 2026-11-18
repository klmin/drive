# Drive OpenAPI 문서 생성

Drive 컨트롤러에서 OpenAPI 3.1 문서를 만들어 이 폴더에 둔다. Developers 플레이그라운드(Management SDK)는 `drive-public-api.json` 을 읽어 API 목록·입력 폼·Limited 표시를 만든다.

## 1. 만들기

```bash
mvn -f DriveAPI/pom.xml -Popenapi-export test
```

- `openapi-export` 프로필이 `OpenApiExportTest` 하나만 돌린다(`openapi.export=true`, 프런트 빌드 생략).
- 웹 계층만 띄운다(`@WebMvcTest`) — DB·Redis 에 붙지 않고, 컨트롤러가 요구하는 서비스는 Mockito mock 으로 채운다. 문서는 컨트롤러 시그니처·어노테이션만으로 만들어지므로 결과가 같다.
- 다른 폴더에 쓰려면 `-Dopenapi.export.dir=<경로>`.
- ⚠️ `target/classes` 를 다시 컴파일하므로 8080 이 떠 있으면 재시작이 필요할 수 있다.

## 2. 결과 파일

| 파일 | springdoc 그룹 | 서버 주소 | 내용 |
|---|---|---|---|
| `drive-api.json` | `drive` | `/api-docs/drive` | 내부 API `/api/v1/**` 전체 |
| `drive-public-api.json` | `public` | `/api-docs/public` | 외부 API `/api/public/v1/**`. 슈퍼관리자 경로·`/office/client-sessions` 제외, `servers: []`, `x-drive` 의 `external`·`anonymous` 제거 |

- `/api/public/v1` 경로는 `@ExternalApi` 메서드에 `ExternalApiHandlerMapping` 이 하나 더 등록하는 주소다. public 그룹은 경로로 고르므로 같은 핸들러가 두 번 나오지 않는다.
- Swagger UI(`/swagger-ui`)는 두 그룹을 드롭다운으로 보여 주고 첫 화면은 `drive` 다(`springdoc.swagger-ui.urls-primary-name`).
- 그룹 없는 `/api-docs` 는 필터 없이 전체 경로가 나온다. 쓰는 곳은 없다.

## 3. 구성 요소

| 클래스 | 하는 일 |
|---|---|
| `global/config/SwaggerConfig` | `drive`·`public` 그룹 정의, public 의 제외 경로·`servers` 비우기·`x-drive` 내부 키 제거 |
| `global/config/DriveOperationExtensionCustomizer` | operation 마다 `x-drive` 를 붙이고, `@ParameterDoc` 을 파라미터 설명·title 로 더한다 |
| `global/config/QueryObjectParameterCustomizer` | `@ModelAttribute` 요청 객체(query 객체 하나)를 필드별 파라미터로 펼친다 — 이름이 URL 템플릿 변수면 path(required), 아니면 query. 선언 안 된 템플릿 변수는 string path 로 추가, 타입 없는 필드(`cv`)는 string, 펼친 뒤 안 쓰이는 요청 스키마 삭제 |
| `global/config/EmptyDefaultCleanupCustomizer` | springdoc 이 `@Schema` 필드에 붙이는 `default: ""` 를 지운다 |
| `test/.../OpenApiExportTest` | 두 그룹 문서를 받아 검증하고 파일로 쓴다 |

customizer 는 모두 `Global*Customizer` 다 — 일반 `OperationCustomizer`·`OpenApiCustomizer` 빈은 그룹 문서에 적용되지 않는다.

## 4. `x-drive` 확장 필드

operation 마다 붙는다(public 문서는 값이 없으면 생략).

| 키 | 출처 | 의미 |
|---|---|---|
| `external` | `@ExternalApi` | `/api/public/v1` 로도 열림 (drive 문서만) |
| `anonymous` | `@AnonymousAccessible` | 익명 허용, `/api/v1` 기준 (drive 문서만) |
| `apiScope` | `@RequiredApiScope` | 필요한 API Key scope (예: `api:write`) |
| `restrictedWhen` | `@TenantRestriction` + 컨트롤러 메서드의 `@TenantFeatureRequired` | 이 조건이면 **서버가 거부**한다 |
| `maskedWhen` | `@TenantRestriction` 의 마스킹 기능(`*_INFO_VIEW`) | 이 조건이면 요청은 성공하고 **응답 일부 값이 가려진다** |

```json
"x-drive": {
  "apiScope": "api:write",
  "restrictedWhen": [
    {"tenantType": "SYSTEM_MANAGED", "userMode": "SINGLE"},
    {"protectedAccount": true}
  ]
}
```

- 조건 = `{tenantType?, userMode?, protectedAccount?}`. 객체 안은 AND, 배열은 OR, 빠진 키는 "상관없음". 더 넓은 조건에 포함되는 조건은 빠진다.
- 조건은 `DefaultTenantPolicy.restrictedWhen(...)` 이 판정에 쓰는 규칙 표에서 만든다 — 규칙 표를 바꾸면 문서도 따라 바뀐다. `DefaultTenantPolicyTest` 가 판정과 일치하는지 검증한다.
- 받는 쪽(플레이그라운드: SYSTEM_MANAGED + SINGLE + 보호 계정)은 하나라도 맞으면 `restrictedWhen` → Limited + 실행 막음, `maskedWhen` → Limited 표시만.

## 5. 컨트롤러·DTO 작성 규칙

**외부 개방·권한**
- 외부에 열 API → `@ExternalApi`, 쓰기 → `@RequiredApiScope(ApiKeyScope.WRITE)`. 목록 `test/resources/external-api-endpoints.txt` 도 같이 고친다(`ExternalApiCoverageTest`).

**테넌트 제한 표시**
- 판정은 UseCase 의 `@TenantFeatureRequired`(HTTP·MCP 가 같이 막힘) 또는 서비스의 `tenantPolicy.require*`.
- 판정이 **컨트롤러 메서드**에 있으면(`@TenantFeatureRequired`) 문서에 자동으로 실린다.
- 판정이 **UseCase·서비스**에 있으면 컨트롤러에 `@TenantRestriction(TenantFeature.X)` 를 단다. 보호 계정 판정은 `@TenantRestriction(protectedAccount = ProtectedAccountRule.MANAGE|UPDATE|SELF_PASSWORD)`.
- 마스킹 조회는 `@TenantRestriction(TenantFeature.XXX_INFO_VIEW)` — `maskedWhen` 으로 나간다.
- `@TenantRestriction` 은 문서용이다. 실제로 거부·마스킹하는 API 에만 단다.

**요청 설명**
- DTO 필드(record 컴포넌트·클래스 필드) → `@Schema(title = "표시 이름", description = "설명")`.
- 목록(`List<...>`) 필드 → `@ArraySchema(arraySchema = @Schema(title = "목록 이름", description = "설명"), schema = @Schema(title = "항목 이름"))` — 목록과 각 항목의 표시 이름이 따로 나간다. 빈 `contains`·`unevaluatedItems` 는 `EmptyDefaultCleanupCustomizer` 가 지운다.
- 직접 받는 `@PathVariable`·`@RequestParam` → `@ParameterDoc(title = ..., description = ...)`. 파라미터에 swagger `@Parameter` 는 쓰지 않는다(아래 §7).
- 예외: 메서드가 받지 않는 URL 템플릿 변수(클래스 레벨 경로에만 있는 `{resourceSeq}` 등)는 **메서드 레벨** `@Parameter(name = ..., in = ParameterIn.PATH, required = true, schema = @Schema(type = "integer", format = "int64", title = ..., description = ...))` 로 선언한다 — 타입을 직접 적으므로 추론 타입이 망가지지 않는다(예: `ResourceVersionController.downloadByToken`). 선언이 없으면 `QueryObjectParameterCustomizer` 가 이름만 있는 string path 로 채운다.
- 계산용 getter(검증 메서드 `isXxxValid()`, 별칭 getter)는 `@Schema(accessMode = Schema.AccessMode.READ_ONLY)` — 입력 폼에서 빠진다.
- 작업 설명은 `@Operation(summary, description)` (영어).

**요청 모양**
- 파일 업로드 → `consumes = MediaType.MULTIPART_FORM_DATA_VALUE` (없으면 요청 객체가 query 로 잘못 나온다). GET 에는 붙이지 않는다.
- GET 조회 조건은 `@ModelAttribute` 요청 객체 — 필드 이름이 URL 템플릿 변수(`{resourceSeq}`)와 같으면 path 로 문서화된다(Spring 바인딩 규칙과 같다).
- 파일 없는 쓰기는 `@RequestBody` JSON.

## 6. 검증

| 검증 | 내용 |
|---|---|
| `OpenApiExportTest` | openapi 3.1.0, paths 있음, URL 템플릿 변수 모두 path 로 선언, 객체 하나짜리 query 없음, `user.*`(인증 주체) 노출 없음, 빈 `default` 없음 |
| `DefaultTenantPolicyTest` | `restrictedWhen`·`isMasked` 가 실제 판정과 일치 |
| `SystemManaged*FeatureRequiredTest` | 차단 기능의 판정 어노테이션 위치 |
| `ExternalApiCoverageTest` | 외부 개방 목록·scope 일치 |
| `pg-migration-check/system_managed_block_probe.mjs` | 실서버에서 SYSTEM_MANAGED 차단 실측 |

## 7. springdoc 2.8.17 주의점

- yml 전역 `springdoc.paths-to-match` 를 두면 그룹의 `pathsToMatch` 가 무시된다 — 경로 필터는 그룹에만 둔다.
- 메서드 파라미터에 `@Parameter` 를 붙이면 description 만 있어도 정수 파라미터가 `type: string, default: ""` 로 바뀐다 → `@ParameterDoc` 사용. 메서드 레벨 `@Parameter` 에 `schema = @Schema(type, format)` 를 직접 적는 경우만 예외.
- `@Schema(title/description)` 필드마다 `default: ""` 가 붙는다 → `EmptyDefaultCleanupCustomizer` 가 지운다(`setDefault(null)` 만 하면 `default: null` 이 나오므로 `setDefaultSetFlag(false)` 까지 한다 — 추출 테스트가 `""`·`null` 둘 다 검사). `@ArraySchema` 는 지정하지 않은 `contains`·`unevaluatedItems` 를 `{default: ""}` 로 만드는데, 같은 클래스가 빈 기본값을 지운 뒤 제약이 없어진 항목은 통째로 뺀다(`contains: {}` 는 "항목 1개 이상"으로 읽힌다).
- `springdoc.default-flat-param-object: true` 는 쓰지 않는다 — `@AuthenticationPrincipal` 필드(`user.*`)가 노출되고 multipart 본문이 query 로 바뀌며 path 변수는 여전히 query 로 남는다.

## 8. Developers 플레이그라운드

- Developers 는 이 폴더의 `drive-public-api.json` 을 **수정 없이 같은 이름으로** 가져다 쓴다: `www/playground/src/drive-public-api.json`. 제목·설명·요청 모델·`x-drive` 는 모두 Drive 어노테이션이 원본이다 — 문구를 고치려면 Drive 에서 고치고 다시 추출한다(그쪽 파일을 손으로 고치지 않는다).
- 반영: Drive 에서 추출한 뒤 Developers 에서 `node scripts/sync-drive-api.mjs <drive 리포>/docs/openapi/drive-public-api.json` (파일을 그대로 복사하고 추가·삭제된 operation 을 알려 준다).
- 플레이그라운드 화면(`src/DriveApiPlayground.jsx`)은 그 파일로 API 목록·입력 폼을 만들고, `src/driveApiPolicy.js` 가 `x-drive.restrictedWhen`(Limited + 실행 막음)·`maskedWhen`(Limited 표시만)을 플레이그라운드 테넌트(SYSTEM_MANAGED + SINGLE + 보호 계정) 조건과 대조한다. 판정 규칙은 문서에 들어 있으므로 Developers 코드는 고치지 않는다.
- `servers` 는 화면이 읽지 않는다(프록시 주소 `/api/drive` 는 코드에 고정).
- 그쪽 테스트 `scripts/drive-request-form.test.mjs` 는 public operation 개수를 고정값으로 비교한다(11-13 기준 152) — API 를 추가·삭제하면 숫자를 맞추고, 새 API 는 `driveApiDocs.json` 문서 링크도 확인한다.

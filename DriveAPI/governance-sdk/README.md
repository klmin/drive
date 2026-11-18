# Governance SDK 파일 기반 Maven repository 사용법

이 문서는 Maven Central이나 사내 Nexus/Artifactory 없이, 전달받은 ZIP의 `repository` 폴더를
Gradle/Maven repository로 등록해 Governance SDK를 사용하는 방법을 설명한다.

SDK를 **어떻게 쓰는지**는 [`../README.md`](../README.md)에 있다. 이 문서는 **어떻게 전달하고 설치하는지**만 다룬다.

## SDK 전달 담당자

Java 8 JDK를 사용해 SDK repository ZIP을 만든다. `sdkVersion`은 전달할 버전으로 바꾸며, 같은 버전의
전달물을 바꿔치기하지 않는다.

```powershell
$env:JAVA_HOME = "C:\path\to\jdk8"
.\gradlew.bat clean packageFileRepository '-PsdkVersion=0.1.0-rc.1'
```

완성 파일은 다음 위치에 생성된다.

```text
build/distributions/governance-sdk-0.1.0-rc.1-maven-repository.zip
```

ZIP에는 다음만 포함된다.

```text
repository/
├─ com/thinkfree/governance/governance-sdk-core/<version>/
├─ com/thinkfree/governance/governance-sdk-json-jackson/<version>/
├─ com/thinkfree/governance/governance-sdk-http-okhttp/<version>/
└─ com/thinkfree/governance/governance-sdk-http-apache/<version>/
```

각 모듈 폴더에는 main JAR, POM, Gradle module metadata, sources JAR, Javadoc JAR가 들어간다.
테스트 코드·테스트 결과·`todos`·전체 개발 문서는 들어가지 않는다.

## Gradle 소비자

ZIP을 예를 들어 `C:/third-party/governance-sdk-0.1.0-rc.1`에 푼다. 소비자 프로젝트의
`build.gradle.kts`에 repository를 추가한다. `mavenCentral()`은 Jackson과 선택 HTTP adapter의 외부
전이 의존성을 받기 위해 유지한다.

```kotlin
repositories {
    maven { url = uri("C:/third-party/governance-sdk-0.1.0-rc.1/repository") }
    mavenCentral()
}
```

기본 사용자는 JDK HTTP transport와 Jackson JSON codec이 포함된 다음 artifact 하나를 추가한다.

```kotlin
dependencies {
    implementation("com.thinkfree.governance:governance-sdk-json-jackson:0.1.0-rc.1")
}
```

### 선택 HTTP transport

기본 `JdkHttpTransport`는 `governance-sdk-core`에 포함되어 별도 artifact가 필요 없다. 기존 애플리케이션의
HTTP 표준 때문에 OkHttp나 Apache HttpClient를 사용해야 할 때만 adapter를 추가한다.

```kotlin
dependencies {
    implementation("com.thinkfree.governance:governance-sdk-json-jackson:0.1.0-rc.1")
    implementation("com.thinkfree.governance:governance-sdk-http-okhttp:0.1.0-rc.1")
}
```

```java
HttpTransport transport = OkHttpTransport.newBuilder()
    .setConnectTimeout(Duration.ofSeconds(3))
    .setReadTimeout(Duration.ofSeconds(15))
    .build();

GovernanceClientSettings settings = GovernanceClientSettings.newBuilder()
    .setEndpoint(URI.create(governanceEndpoint))
    .setCredentials(credentials)
    .setHttpTransport(transport)
    .setJsonCodec(new JacksonJsonCodec())
    .build();
```

Apache adapter를 사용할 때는 의존성만 다음처럼 바꾼다.

```kotlin
implementation("com.thinkfree.governance:governance-sdk-http-apache:0.1.0-rc.1")
```

```java
HttpTransport transport = ApacheHttpTransport.newBuilder()
    .setConnectTimeout(Duration.ofSeconds(3))
    .setReadTimeout(Duration.ofSeconds(15))
    .build();
```

`governance-sdk-json-jackson` POM은 `governance-sdk-core`와 Jackson 의존성을 전달한다. OkHttp/Apache
adapter POM도 각각의 외부 HTTP 라이브러리를 전달하므로, 소비자가 Jackson·OkHttp·Apache JAR를 직접
`files(...)` 의존성으로 추가할 필요는 없다.

## Maven 소비자

`pom.xml`에 ZIP을 푼 repository 경로와 SDK 의존성을 선언한다.

```xml
<repositories>
  <repository>
    <id>governance-sdk-file</id>
    <url>file:///C:/third-party/governance-sdk-0.1.0-rc.1/repository</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>com.thinkfree.governance</groupId>
    <artifactId>governance-sdk-json-jackson</artifactId>
    <version>0.1.0-rc.1</version>
  </dependency>
</dependencies>
```

## 주의사항

- SDK JAR가 아닌 POM 좌표를 사용해야 전이 의존성이 자동으로 해석된다.
- 소비자 환경이 Maven Central에 접근할 수 없다면 Jackson·OkHttp·Apache의 사내 mirror를 별도로 제공해야 한다.
- HMAC secret, DB 비밀번호, encryption key는 ZIP·소비자 프로젝트·로그에 넣지 말고 환경변수 또는 secret store에서만 공급한다.
- 새 SDK를 전달할 때는 새 버전(`0.1.0-rc.2` 등)을 부여하고, 기존 전달 ZIP을 같은 버전으로 덮어쓰지 않는다.

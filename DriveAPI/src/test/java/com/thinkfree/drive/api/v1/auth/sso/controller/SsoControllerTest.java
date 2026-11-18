package com.thinkfree.drive.api.v1.auth.sso.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.auth.sso.request.SsoJwtLoginRequest;
import com.thinkfree.drive.api.v1.auth.sso.request.SsoOidcCallbackRequest;
import com.thinkfree.drive.api.v1.auth.sso.request.SsoSignupRequest;
import com.thinkfree.drive.api.v1.auth.sso.response.SsoSessionResponse;
import com.thinkfree.drive.api.v1.auth.sso.response.SsoSignupResponse;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.fixture.ExternalJwtTestHelper;
import com.thinkfree.drive.fixture.RsaKeyTestHelper;
import com.thinkfree.drive.sso.constants.SsoConstants;
import com.thinkfree.drive.sso.enums.SsoSessionStatus;
import com.thinkfree.drive.util.StringUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Disabled("실제 IdP 연동 필요. 클래스 주석의 준비 단계 참조.")
@Slf4j
class SsoControllerTest extends AbstractMvcTest {

    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/auth/sso");
    }

    @Test
    void ssoLogin() throws Exception {
        
        Integer ssoProviderSeq = 25;
        String url = StringUtil.buildUrl(String.valueOf(ssoProviderSeq), "login");
        MvcResult result = mockMvcPerform(get(url)).andExpect(status().is3xxRedirection()).andReturn();
        String location = result.getResponse().getHeader("Location");
        
        Assertions.assertNotNull(location);
        Assertions.assertTrue(location.contains("client_id="));
        Assertions.assertTrue(location.contains("scope=openid"));
        Assertions.assertTrue(location.contains("state="));
        Assertions.assertTrue(location.contains("redirect_uri="));
        
    }
    
    @Test
    void callBack() throws Exception{
        Integer ssoProviderSeq = 24;
        String url = StringUtil.buildUrl(String.valueOf(ssoProviderSeq), "callback", "oidc");
        String code = "4/0AfrIepAVm2ZG1sSgLixX2pBxUQ9hoCLn3l99LQxInIHs6fNMs1RMN4kusqep-38AbfOfQw";
        String state = "eyJhbGciOiJIUzI1NiJ9.eyJwaWQiOjI0LCJ0eXBlIjoic3NvX3N0YXRlIiwidGlkIjoxMywiaWF0IjoxNzczMzkyODgzLCJleHAiOjE3NzMzOTMxODN9.4MNVTex6t3gfcpaL7CkUAH8oOpAYmAYR07gJ-B6YmuE";
        SsoOidcCallbackRequest request = new SsoOidcCallbackRequest(code, state);
        var param = multiValueMap(request);
        MvcResult result = mockMvcPerform(get(url).params(param)).andExpect(status().is3xxRedirection())
                                                                 .andReturn();
        String location = result.getResponse().getHeader("Location");
        
        log.info("Set-Cookie: {}", result.getResponse().getHeaders("Set-Cookie"));
        
        Arrays.stream(result.getResponse().getCookies())
                .forEach(c -> log.info("cookie - name={}, value={}, maxAge={}, httpOnly={}",
                    c.getName(), c.getValue(), c.getMaxAge(), c.isHttpOnly()));
        
        log.info("location : {}", location);
    }
    
    @Test
    void ssoSession() throws Exception{
        
        String url = StringUtil.buildUrl("session");
        String ssoSessionToken = "eyJhbGciOiJIUzI1NiJ9.eyJwcm90b2NvbCI6Im9pZGMiLCJlcnJvcl9jb2RlIjoiU1NPXzAxMCIsImVycm9yX3N0YXR1cyI6NDA5LCJ0eXBlIjoic3NvX2Vycm9yIiwiaWF0IjoxNzczODEwOTcxLCJleHAiOjE3NzM4MTQ1NzF9.maDoFS3FOdLIvq9qi0D7J1YB8UIDR-zmxZmWuNxGWNU";
        Cookie cookie = new Cookie(SsoConstants.SSO_SESSION_TOKEN, ssoSessionToken);
        
        ApiResponse<JsonNode> response = readValue(
            contentAsString(get(url).cookie(cookie), null, null, status().isOk()),
            new TypeReference<>() {}
        );
        
        JsonNode data = response.getData();
        System.out.println("data : "+data);
        String status = data.get("status").asText();
        SsoSessionStatus ssoSessionStatus = SsoSessionStatus.valueOf(status.toUpperCase());
        
        SsoSessionResponse sessionResponse = switch (ssoSessionStatus) {
            case LOGIN -> objectMapper.treeToValue(data, SsoSessionResponse.Login.class);
            case MFA_VERIFY -> objectMapper.treeToValue(data.get("config"), SsoSessionResponse.Mfa.class);
            case MFA_SETUP -> objectMapper.treeToValue(data.get("config"), SsoSessionResponse.MfaSetup.class);
            case SIGN_UP  -> objectMapper.treeToValue(data.get("config"), SsoSessionResponse.Signup.class);
            case ERROR  -> objectMapper.treeToValue(data.get("config"), SsoSessionResponse.Error.class);
        };
        
        log.info("sessionResponse : {}", sessionResponse);
        assertNotNull(data);
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }
    
    @Test
    void signup(){
        
        String url = StringUtil.buildUrl("signup");
        String e = "testEmail";
        String n = "testName";
        String st = "eyJhbGciOiJIUzI1NiJ9.eyJwcm90b2NvbCI6Im9pZGMiLCJuYW1lIjoi7J6E6rK966-8IiwicGlkIjoyNCwidHlwZSI6InNpZ25fdXAiLCJ0aWQiOjEzLCJlbWFpbCI6ImtsbWluNjM5NEBnbWFpbC5jb20iLCJpYXQiOjE3NzM4Mjk2MTQsImV4cCI6MTc3MzgzMDUxNCwic3ViIjoiMTEwNTI5NjI1NzA1NDYwOTI1NjMxIn0.zpcOm8fGMoartYWJq64I7ILAT8hl109hYF-kAv0-Mp4";
        var request = new SsoSignupRequest(e, n, st);

        ApiResponse<SsoSignupResponse> response = postForObject(url, request, new TypeReference<>() {}, status().isCreated());
        assertEquals(CREATED.value(), response.getCode());
        assertEquals(CREATED.getReasonPhrase(), response.getMessage());
    }
    
    @Test
    void generateRsaKey() {
        RsaKeyTestHelper.RsaKeyPair keyPair = RsaKeyTestHelper.generate();
        log.info("publicKeyPem : {}", keyPair.publicKeyPem());
        log.info("publicKeyBase64 : {}", keyPair.publicKeyBase64());
        log.info("privateKeyBase64 : {}", keyPair.privateKeyBase64());
    }
    
    @Test
    void testJwtLoginToken(){
        RsaKeyTestHelper.RsaKeyPair keyPair = RsaKeyTestHelper.generate();
        String privateKeyBase64 = keyPair.privateKeyBase64();
        String publicKeyBase64 = keyPair.publicKeyBase64();
//        // 슈퍼어드민 jwt 설정 key
//        String privateKeyBase64 = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCy1+U/8gxdcTVRdCWUilgZkiSxOKpK7hMhOgiwSXBU7yI/h9ePtU1YUip+2MPbGpUD27h3Xydx07FShynwa9UMReLyYyIHNfyP5+cB96vhe91Aycw57kF9eyV4+dN6QMAz6T0QzHkj4O0dJsPtyGeAl1n0H93OqyqDCg1eeqGxYjz8HHMIaVueyfwqehwcXjI8ushEVqMIYUVXlJBDePPB4BQIiRKSIc3WSYm34etpZbZHKnlykUXIqgkkpJaNkRlOrriawp1ct84kz3SmaH4Ii9fQaagSnbhzvK0/+91HMObR0DPyPsWEyV9ncnMGtk2BDpM04BbDB9FskbHDAKXPAgMBAAECggEAMVSFwo6kRH3+g/oyh3dld6AtCMklh8eFdUvJq76GApd3beitV9FdhTgcyqJF28DIS6kmh56yhZ4rsiTgN5W2zUzVnaAkebK1eIgx2wQS3/5HeCBxeaUzYUY/ftACOh10Jp12H5UW8QOQw9K2hncZvYmB4WmMx/3EmyKBr4bJ5OBJZ4xChvgGrYCUy19Keg4pbiP9+5FbsQav5GCqtNvk7PFqZfM7n2yNbXvIU7lkOts5rHFSgVwofx3doBKkJN8jctQxmpkWxYqHyw3+cfM5k4pXG5qJ3UpsNM+FjeLMbYu8CBFWWkixtt0GPZ7Y1oorphEXPOMVYSUGsM53MEtZGQKBgQD8PgOvQEFnlm4JW/E545OEdQkKKys/qAopQjaqOMVK1KWqTXgY57i/ed9MWqBqSJpno12HpdxrJdgobCB1loXWtxcm1UtyvUluMC2gvW6qHGsNO8UfD3Fj3LWjSlujyV6Ak2C+EYmDBHMmnrg8N5vbvt0Fv2Lh9jH7z7UXEMjLzQKBgQC1gfT9zIKQf0VT/7BE87AiDph9mShW+r1CP4hBP97I/a3eOB5rszdoULNJw0CDhJW2R4mbTgkMCsZ7OaNhZKpOE+TURwGiQFwaAMHs1dkiotfAJPDRPTtZUQ8FWeZDfBeDodEK2XTlJ28IErTiAOyAV3mhilvaEDJMUWgC9SN0CwKBgCEB/HlybxyfEscmwOpSXXt31Tb/byllU+8SOxULWMXyDbu7Z3va1tI3oIDdWyCNX6cwOY0Gp1FeImsnuxeoGtt+Ggl63+Sj4HgK1e2y3Ehmb5vLtn/aBXyL5Tj5AgTs1PD+WCqHIPeEHY1wN6upmzumPkYCRW5MST4qcAgUqjIBAoGACLwyYCyBdBbL7Vj20LqxlVICgLjoOhdjGFcaZZl/TUj4HyOwQxqi5gBMQJaG+k1dLyKOOs0snkm+d5u0gzFnCM7oSlcIexiB1jaHHrDD/Gi93V9pv4gk3Q2LnYRQEptcPleBSfZhK84UB91umxnUR/Dtx5pzBTGWWhno2YuWozUCgYEAgafcyxbMz+7LnKGdlIGh5fgdM8hdjHq4Bh8Uzxwgk3t0M81AMEW/Dk2VuZ7V7spz3db5rfL4DJZ9M69Pbu7Tbbx7Y02UM0oZKsOfEwy96DPJ+WE239M5uoJOoCMdAwGwIKV4nkRi02dD049qns526iARJP4dB4gTAdTmDLi1lng=";
//        String publicKeyBase64 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAstflP/IMXXE1UXQllIpYGZIksTiqSu4TIToIsElwVO8iP4fXj7VNWFIqftjD2xqVA9u4d18ncdOxUocp8GvVDEXi8mMiBzX8j+fnAfer4XvdQMnMOe5BfXslePnTekDAM+k9EMx5I+DtHSbD7chngJdZ9B/dzqsqgwoNXnqhsWI8/BxzCGlbnsn8KnocHF4yPLrIRFajCGFFV5SQQ3jzweAUCIkSkiHN1kmJt+HraWW2Ryp5cpFFyKoJJKSWjZEZTq64msKdXLfOJM90pmh+CIvX0GmoEp24c7ytP/vdRzDm0dAz8j7FhMlfZ3JzBrZNgQ6TNOAWwwfRbJGxwwClzwIDAQAB";
        log.info("privateKeyBase64 : {}", privateKeyBase64);
        log.info("publicKeyBase64 : {}", publicKeyBase64);

        /*String privateKeyBase64 = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC/5WjtXHCwkqhUsDShMhcyW0sif2lXuOD2H6985AX8ltsVPAC7OBLtGP5TLv/xUhza0jna/d2jJi1zTTXT6mYZue1OlcHpLhlyr9cn0b1r6Py3i43WsQw0xKyQtgCKr5ISHZnNMeuYrC6qgY72ajrPXw/2NMMmMhoxwzXM20dQy/XhLB2mP6zvP8Ojd+rA18ZDBNSA+B5EUGPVB1yz5CoS5crFwBjaBX7C2ddrccaYDA4IB/fgTwqb7l6Fzm/Ijc+UXqfZ/+D8Rykzl4+ipajtRo84E7e+Yc15FD3vigw2fMA03ESq4+uYORb3338v3w1hsUJKWGfBHNpzPbG4JwGBAgMBAAECggEAAJm6GRS4Y+O67p7pieqYoNH2e3pnbOdEHvO3mR4JOG02wiDWBhVKwTQE5w48g00PkDi8W8d+bXpbBeNt4cailf3bX1WqeG1qQM4KOpKWXj4pp5+3wdOmgeDEAyTqqK8cIiiLuL++qL47MXyRIwtvOS28j0ztd7225W/NXTbUyOIvyj/cTwFylmTSs1jpUxZXIsiNPXNh8OOUZTKTTzPO5M6+4gHputL+JfJCNyHXUq8NVUYZeTJWqI6x6xyMOIyxAc/t21E2W/GK/qvZp3ODVhVGbThwY44BVAVJRkxu+TBEqQZFe7ZU2LgAgF/s82WaOL7DABT1iOB4GI9/ti/7lQKBgQDcjM3XgVVj/CNIQez6T4qDEnHiZKp59KNqn+MzRuM1J1kX9Sdte+4a9+CPZMRb6BxNX9Mjp4yk+/HEqSG5y/hqyHm3mEle2m41WRnf59qTANbC8z7llvCcxczhFZk3gkQdUJoN1zRCAOxni/dQVA6TLnrPyuhP58LI4gSByTbPNQKBgQDevY4n78B0oQuDLZl+9JSn1VHm+iKA6qTRH/UH5VC55hIGpz36O5cPZwUlazJp7ZHnJ51FceUCacQK993qMEnNcFRytK0Htf56S6czkPm+vI0ew7LghFzYCmk3tqAU8RXE9TIUulPx7Z6WnCkaYTWLI1FlEOmM3xF+pm4AyVn2nQKBgDZ5RKI03J1sel5vghO1L7X+CUjsqjqjFPXR9fEFUjsfs965+3oMQRxCGyHvtpwosUzfHSxSRue9HtL/H0hfu8TMLVwObhjShKQClOR7xL3IWEMgh2l3pYhjRp5VkllmgGJpNRO0EsE2ry1IIdaiXFM4CLdoG4cY7EBnqstPooz9AoGARCI5rM+y0Do++kZlkKciHeCr5/qVuVeNSQb8fgjQU70jwHNCCxxVTfnWYibTSgHIwqukeQyo378Yn75IXI2e1b/JpTjNQPGTwTcJE0YCjyzAAiwcuH7ko5UzsD4l46IHZbMXOa3xZayo+SsVi6M2rLQF/uNYfaXyhQpF1enBoiUCgYEAik99uy+j7XVz4KHZSEIxc5/d3Wn9uq5d29JT5oL/bpyT0ETXwUQI62AaJWkV5zf4WuSwArZy+7cFRbK+Xl5hbpINpAg045dESwGzwvkwX0ECNQKjX0q5Db2zEQ4mgXyYJEmh7xd09Q5j6TpVKcxRjlgQ9WAJuS5S1Fsks8YRY1k=";
        String publicKeyBase64 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAv+Vo7VxwsJKoVLA0oTIXMltLIn9pV7jg9h+vfOQF/JbbFTwAuzgS7Rj+Uy7/8VIc2tI52v3doyYtc0010+pmGbntTpXB6S4Zcq/XJ9G9a+j8t4uN1rEMNMSskLYAiq+SEh2ZzTHrmKwuqoGO9mo6z18P9jTDJjIaMcM1zNtHUMv14Swdpj+s7z/Do3fqwNfGQwTUgPgeRFBj1Qdcs+QqEuXKxcAY2gV+wtnXa3HGmAwOCAf34E8Km+5ehc5vyI3PlF6n2f/g/EcpM5ePoqWo7UaPOBO3vmHNeRQ974oMNnzANNxEquPrmDkW999/L98NYbFCSlhnwRzacz2xuCcBgQIDAQAB";*/
//        String issuer     = "test_issuer";
//        String audience   = "test_aud";
//        String subject    = "test_subject";
//        String email      = "test@example.com";
//        String emailTest  = "test_email_test";
//        Map<String, Object> claimsMap = Map.of("email", email, "email_test", emailTest);

        String issuer     = "https://superadmin.example.com";
        String audience   = "";
        String subject    = "2026070777";
        String email      = "qaqa1@thinkfree.com";
        String emailTest  = "";
        Map<String, Object> claimsMap = Map.of("email", email, "email_test", emailTest);

        String token = ExternalJwtTestHelper.create(
                privateKeyBase64,
                issuer,
                subject,
                audience,
                null,
                claimsMap,
                1
        );
        
        log.info("token : {}", token);

        Claims claims = ExternalJwtTestHelper.verify(token, publicKeyBase64);
        claims.forEach((key, value) -> log.info("{} : {}", key, value));

        Assertions.assertEquals(issuer,  claims.getIssuer());
        Assertions.assertEquals(subject, claims.getSubject());
        Assertions.assertEquals(email,   claims.get(DriveConstants.EMAIL));
    }
    
    @Test
    @DisplayName("공개키로 검증")
    void jwtLoginPublicKey() {
       
        setTestHost("test5.drive.thinkfree.com");
       
        //String driveAccessToken = "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VyX3NlcSI6Njk2LCJ0eXBlIjoiYWNjZXNzIiwiaWF0IjoxNzc0MTc1MTEwLCJleHAiOjE3NzQxNzg3MTB9.EzGr4moLyKqdL2XycLTu_uCp2drKojds2lMT3FtWgzM";
        String driveAccessToken = null;
        
        String externalToken = buildPublicKeyToken();
        log.info("externalToken : {}", externalToken);

        String url = StringUtil.buildUrl("jwt");
        
        var requestBody = new SsoJwtLoginRequest(externalToken);

        var requestBuilder = post(url)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(writeValueAsString(requestBody));

        if (driveAccessToken != null && !driveAccessToken.isBlank()) {
            requestBuilder.header(HttpHeaders.AUTHORIZATION, driveAccessToken);
        }

        ApiResponse<JsonNode> response = readValue(
                contentAsString(requestBuilder, null, null, status().isOk()),
                new TypeReference<>() {}
        );

        log.info("response.data : {}", response.getData());
        Assertions.assertNotNull(response.getData());
    }
    
    @Test
    @DisplayName("JWKS_URL로 검증")
    void jwtLoginJwks() {
       
        //String driveAccessToken = "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VyX3NlcSI6Njk2LCJ0eXBlIjoiYWNjZXNzIiwiaWF0IjoxNzc0MTc1MTEwLCJleHAiOjE3NzQxNzg3MTB9.EzGr4moLyKqdL2XycLTu_uCp2drKojds2lMT3FtWgzM";
        String driveAccessToken = null;
        
        String externalToken = buildJwksToken();
        log.info("externalToken : {}", externalToken);

        String url = StringUtil.buildUrl("jwt");
        
        var requestBody = new SsoJwtLoginRequest(externalToken);

        var requestBuilder = post(url)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(writeValueAsString(requestBody));

        if (driveAccessToken != null && !driveAccessToken.isBlank()) {
            requestBuilder.header(HttpHeaders.AUTHORIZATION, driveAccessToken);
        }

        ApiResponse<JsonNode> response = readValue(
                contentAsString(requestBuilder, null, null, status().isOk()),
                new TypeReference<>() {}
        );

        log.info("response.data : {}", response.getData());
        Assertions.assertNotNull(response.getData());
    }
    
    private String buildPublicKeyToken(){
        //33
        //String privateKey = "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQDEnHhJt/+SdAwHD1wAQsIbDDwWQLRJHkY5Jeh89DT8sasRmJKLFslhcgx27C5OmAorcMeiNqe/yJt3Q2REIguA+tbgwxxfrccMKKuDNvMmBz5wmmhlmutzyqSOdLotu/uqEMJDn04C8LKmHZKgIzFGBu2OnSTdt8IBPUMzTH89iu57XGbEUIQQBbNNuEFjK2j6cSv2kradStF4jJ4RyOEigrQFeA27ru+U3m4v4YNsTR5Mm8cEh1tMDWqF4TJWFtGUi90cCNsqH5mSoe9P1H2l/yZDZdDYbjFE3Pt4z8AT7cNfOuIVpnBbSI586xWf73fCPAYYQW63TuPhw4tnl+xdAgMBAAECggEATXvs3ACrtCgP5WhkNaYZoVc9BvgRFQ/dVQBfk38assvCQ3kBp9cfHkw4JVYHGepJd71Gr+MiUS7aNjJU/ZT9O6Ofs4yxTfPKkjwk+91YDhCkxvPTWNepG0UtfQb7qLKBCBD6vnO15uq9PUJCWuIDtTldveqocMjOqHZgWYM4UiQR5/w2GWFrhArogU2csgdx3j68RR83ghdB2uef5sYSw9SG5fZwj32N+kWeyuesRkgcTF2ji5b99aQKcSVKA3itaSa/YLjIZ1l45Qh+gd3NzTWi3ktNBtNhyn5kZza0B2ofgrsqOd/wa4z3E38vnTKZ1OyJP0MGtzv0pRoumnjf8QKBgQDi/pnt9wPA0Y9cldzBnpSEC9gQaUAM7zZFJ9UF1uGr1IHycxjZNpsYw0bBi33aoSokjm4gF5jOjQ6pyoTkpR+lBE2xWqxwVAa/rYAg0uv6dap3H/TlxqmCyblVQbBLhyQh6c7/+3boiJiXkzxVo7SeyDZBVY6IPagOV2/3pj77OwKBgQDdu/m4EAfEqCMJAhhWVymg4ZaBcit8RXbfeRpJoEzjIzD6t8kJkChclszUj7V1grzR0FFBfx/a7Gkf6Y1GlkGpbtvNPgyEhrSU7JyO1M1ls4cnY166H8J0ic6Jz+5M9huruABuPmbGjKg/OWPdoyk23X4xwHnULPK4MO8xs7bNRwKBgQC8lUbvA0vz8mYHRl374bKDLY/YuohC9q2AsMHysv4Tkl5PPb4EH7Ck1v8+ppPtgf2QF2B4ewIxf9ER0LWte12Z68RcNveR7s0Yi50qJ/FyyDTR+ROtnemQReUaZpN/fAvRvPyDgLfeIjWbn+/9Oh9B1xX0G49pssee+peECHLEpwKBgQDPqpSneYP8/8fIUQkAHCQlxAGUfJwTiG9bvu0K1bIM7NGbjvMfHL9gb++oiGtXgjlbe/IOGIR6If4W4ELrQCCBEg2T/K/2kr6f17aM4ur3fBtxnVSDluklAkfItFAoDjs+KVdl6NWS0PtmM1a/pmccNndRG0Y+nFY6gcIT14+2UwKBgQC38FxxxFcHT1jhTuRNGm4VNisCRT0A8SaT64up5iTP0PMThUKh6A8Jn8LYMu5IHmQR0o/pvsrI1Y5721wJjpC+wBRmvHdPkka3b3pqZ5tb+ZaER9VvKa8RTPFJ79VLbEDiY8cgVqpRpMcsL488Vxyl43c/GXAwcyaxpGAeD55KHg==";
        //28
        String privateKey = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCrZwAZCba4QA5S2b02GLKWuur5b5HIoGQqc/nz9tujP4CdFAUitgjrdy/J5M/+3r8Ukgo/mnx80twigqD+doqBpXR4PD1G6LR5oCCJZciQxR9/ah+pxa1akmxnU5Ti2Vb86RhBP6dOZ3Vt+7mHPY5WDUC4c0uZ1JuRz77usvjhILvJJxk4KIe8IvcCuBec3zNQ5Pif4WxOFCEdyEnj4SIm7+uPrZWoPzgyhz5KnVzL+WBLcG6eKz6CcEtcnxdK4rx5Mv/rYl+gUJNeKYI6MFu47hPR5tqZxbDknL8gNgAo/Fw9L/zEyoTp9hqeGwKgmGM04KSsOv+U/9jbasaV+JvhAgMBAAECggEAARlum1lRwhRPnd/iQZTMuwdyrfIDvQOnv+ypCqIHlBID5IZR/IUEZLitRApWmLOMWtjIPepu9T+XKTbysvjlzCLMnkz9IOwNJwXZNJzXOZnQvA9kXfMWThTKW/arYE4s8o22EX55lmlmL5eF42UIX1qbRu5AFW4L7sNmsLkm+dA4it8V0GBq15PbR8vUrteVrJNXqv0VXhHf1qOITwUCzmf6E1UzyAWFvXd0+ZA+3Qhf74oyn3lhD1EdP3qgihWx770TMDcEW7aAhxZg793VUFQlbGaDkTdygmoFS3ulrj5k13Q4eyJwMUMd2OjqOTymuKzQCQIzVLnLzdcMhGFl9QKBgQDAPh5cFsG6wwVNGNdrmdjtYWpEPiwKLwes6DCBQivIgJ+NXzSE9ceDSAgvCUe3zGtBRl411iiOWdXkb8Cy6uyy82J32yc9g/9dqmJ1mvSgFkpbfK6IrVuwmOl9gNtt7AnaeWqKZtcWEsl33AU1tZ3qtv7oVCHJSIon1eMpW4OGvQKBgQDkP3ze//S3piOWMkLzxY2DLL/vRchcsAqhycLO+lSGbJUy+tEFzSRO2T6feZ0/7u1KI0/CKhogUfTAmTQeTEXlpE3YBlD9uOH9N4aACRn64wMa022hKk5YMVjbkwoUe+njmb6M2h4XX7OR8WyFwV1OGGbTorDBKYnBGbeZmx5d9QKBgHa05OJQWwgL7HYS5NJYiY7rHEdPbLpNGBw4s9rdFVQVuG//XdrMOYpjLEp/HH3uCDyiSwJLX3enGWCZcRAJ6GcHysHf3XcqzsDzuSCNsog3u2qKrutOwNNnWKZaryRNIDh7yMH1htLmfuk4Py9PGlgY74ZejQ5RcJ3wgPjEvY6xAoGAcU+PV0msHGvPKOZE1K4TRba2hjuXb2Q5sv3tx0EBK0bGfuuDTyT7TGWPMC54wV+Ez5ReKR8rnzXwkpi8BfqJjKqXtpHULVFWZ5XkILXj/ugqB2xE9YiCIkhFO6RwkaC+iS4vKc+ytpndFKvM204WWwnEKnaO1yYe7A0vV4NpfO0CgYEAkTzYEJgbD8USwrQcHPWqeVu3MCgHudRawaTANKTwadRFvvhgBcLg0j+2uZe2eC3OorbBDAvNcJVTx3+EiO1s90XnLTbT8vHlCxA4ePIqfeHbSswr2EQPKHjIxB5oyFOw2g9hU8vESflOJOQ9nbwIGl04sLfvu/Dys+fWT+hDp0w=";
        String issuer    = "https://issuer.example.com";
        String subject = "aaaaaaaa";
        String aud  = null;
        String email = "klmin6394@gmail.com";
        String name = "testname";
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("email11", email);
        claims.put("name", name);
     
        return ExternalJwtTestHelper.create(
                privateKey,
                issuer,
                subject,
                aud,
                null,
                claims,
                3600
        );
    }
    
    private String buildJwksToken(){
        //27
        String privateKey = "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCK0LwqkRt1JuNi9Sng+JeV3mb4G3yWrkWsGXG2G5Jj9pVxbA7Xg0KgLJ34Ga7RdKI7yWp7RJIjaT5VbHb0coaBtlPqu++5Yzrsyj++MajF2hnB953dRB38WSNEtpCrEQ/Zhx3YqYCtNKZNmCXSqGrbDnoH0x+1lm9a2krC+wauEyQMyY2wSZPPgxO/CKBvQfMKxoWVYVAUzBMNO79EQ32lC36PPS1CAOzxVw6h4Q8YO0Mlnc0MeuaKct0vC/F3K1H9jSreu8/+HMDx11civkjrOkfA6xcu2lnDYGaFFXoVLRAGgevfBeBKIl+dQiQl4i9XEGSVhEdGtT1pibqA6QNTAgMBAAECggEAKbmSRa02NqGIya7tdpziW37tLnh8m4IomRFQ168z8m4k/2G+mFLxDNo8ZV266WSIezYQlprG3nP884pVuyZNfeHDGZSB+xRzmsc0H8QneckT4vS988tMxBQ7AKVrmK/ZNsZhV0Wj0uw3bK1l+Qr8i3mvfnicWGWudP2WaUMw/9N5qxCi/idH6etFvZbCOXIfmLY+dHxXddmpdvubNFx22Ynca3wUfrocrLobKNp56zzW0T/F8ckraLiHEkCitmtciR/aXkl+iCRwBq5AK0wcMgVMiD8s8kq8fp/WbRzJCKv7sr1j3jhOw1227NIxHJVWfblVedLMz0OZr6Kow3nLKQKBgQC2/zrfPUpFdFYHHAVoAQIHCzXSZzPkKU3uvfd48F7lPI6Z80geRvH37tb8Rmll14QN8q8c9ZsxYxxcy62jreLkBs9NAW09dMLwDhIVS+g7SO0tLFCO16IzsnJltSUOlgnorexlH9w/BprOfwM8RWX4OwNMI0hTh4YRz+rDt2u/9QKBgQDCMWh/6i0dWOLmpwjjkRzBWVPq22Kc+clKjBkdK85OIBfdqkJHTlorl+MqHR6NqrlJUKHySBYuveR+dGV6VZu4BPQrNl4QxHExXg4tZyyplExrvmoLJe5jdzAId6GjhxPQaL/bqjl1cmlShkLHggBhH509aqd4KHFyzMEL1pGRJwKBgAsYsvMyfjxz73uC4XkLBppyvZLCm0zsjEM71bPJr5dq8qK3M9pSJv4Hx5WIl3+LcACAhMnWwtBd2CxwVVklhCqblxpuSbd+SwvPbp1H+w8NPwSMOV5QCQ/ldDZio5qc0oD/euQu6GYR+nUnEoUnGCXTPkHxqcMW5rRRzUSM7MW5AoGAGe7fNbXgMHseUg8ccMeAP5HeUw7ZLH1vFDWBZRPkiSpgBYonL76W7BtsisM4wVJ7S/8ooWJfE/7n9Ls2kg+8q+Z9dQfdp/+T3jPhUM7md/8F9Z2IK9T08POJxpezTlNLohlbBcrFwZl11z92y43g3xeJuBn2I2XDQMF3Rk1xQyECgYA+xE4fj3Zw7O+CiPqPkhD6mR4QrM4qtfzkm5jMkv6KkARwqWx+/GOi82556rEwAe812rngYCBLhErShvWDmpa8oVjJtrhUJOofPbK7uxY7lrtaGD3h80nI4TAgEFgptXoln1QYWMOeo2IMpZvNHsl95XFwSe25i0M/8MXBfcgRQg==";
        String issuer    = "http://localhost:8080/testJwks11";
        String subject = "test-subject";
        String aud  = "testJwksAud11";
        String email = "haemmas6394@gmail.com";
        String name = "testname";
        String kid = "test-kid-1";
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("test_claim11", email);
        claims.put("name", name);
     
        return ExternalJwtTestHelper.create(
                privateKey,
                issuer,
                subject,
                aud,
                kid,
                claims,
                3600
        );
    }
    

}

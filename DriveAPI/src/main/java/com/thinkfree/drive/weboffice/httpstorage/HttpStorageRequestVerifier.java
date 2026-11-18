package com.thinkfree.drive.weboffice.httpstorage;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HttpStorageRequestVerifier {
    public static final String HEADER = "X-TFO-Storage-Request-JWT";
    private final com.thinkfree.drive.systemconfig.service.SystemConfigFindService officeSettings;
    private final HttpStorageAccessGrant grants;

    public VerifiedRequest verifyHeaders(HttpServletRequest request) {
        try {
            List<String> headers = Collections.list(request.getHeaders(HEADER));
            require(headers.size() == 1);
            String token = headers.get(0);
            require(token.getBytes(StandardCharsets.UTF_8).length <= 8192);
            SignedJWT jwt = SignedJWT.parse(token);
            Map<String, Object> candidate = jwt.getJWTClaimsSet().getJSONObjectClaim("request");
            require(candidate != null && candidate.get("adapter") instanceof String);
            String adapter = (String) candidate.get("adapter");
            Integer tenant = com.thinkfree.drive.global.security.TenantContext.get();
            require(tenant != null);
            var configuration = officeSettings.findOfficeConfig(tenant);
            require(configuration != null && adapter.equals(configuration.getOfficeHttpStorageAdapterName()));
            String signingKey = configuration.getOfficeHttpStorageRequestJwtSecret();
            require(signingKey != null && signingKey.getBytes(StandardCharsets.UTF_8).length >= 32);
            require(JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()));
            require(jwt.getHeader().getType() != null && "tfo-storage-request+jwt".equals(jwt.getHeader().getType().toString()));
            require(jwt.getHeader().getCriticalParams() == null || jwt.getHeader().getCriticalParams().isEmpty());
            require(jwt.verify(new MACVerifier(signingKey.getBytes(StandardCharsets.UTF_8))));
            var claims = jwt.getJWTClaimsSet();
            require("thinkfree-office".equals(claims.getIssuer()));
            require(List.of("tfo-http-storage-provider").equals(claims.getAudience()));
            Map<String, Object> raw = jwt.getPayload().toJSONObject();
            require(integer(raw.get("iat")) && integer(raw.get("exp")));
            Instant now = Instant.now();
            Instant issued = claims.getIssueTime().toInstant();
            Instant expires = claims.getExpirationTime().toInstant();
            require(!issued.isAfter(now) && expires.isAfter(now) && expires.isAfter(issued)
                    && !expires.isAfter(issued.plusSeconds(60)));
            require(claims.getJWTID() != null && !claims.getJWTID().isBlank() && claims.getJWTID().length() <= 64);
            Map<String, Object> signed = claims.getJSONObjectClaim("request");
            require(adapter.equals(signed.get("adapter")));
            require(request.getMethod().equals(signed.get("method")));
            require(request.getRequestURI().equals(signed.get("path")));
            require(request.getQueryString() == null);
            require(integer(signed.get("content_length")));
            long size = ((Number) signed.get("content_length")).longValue();
            require(size >= 0);
            Object sha = signed.get("content_sha256");
            require(sha instanceof String && ((String) sha).matches("[a-f0-9]{64}"));
            require((request.getContentType() == null ? "" : request.getContentType())
                    .equals(signed.getOrDefault("content_type", "")));
            require(request.getHeader("Transfer-Encoding") == null && request.getHeader("Content-Encoding") == null);
            List<String> lengths = Collections.list(request.getHeaders("Content-Length"));
            require(lengths.size() <= 1);
            if (size > 0 || "PUT".equals(request.getMethod()) || "POST".equals(request.getMethod())) require(lengths.size() == 1);
            if (!lengths.isEmpty()) require(lengths.get(0).matches("[0-9]+") && Long.parseLong(lengths.get(0)) == size);
            ClientMetadata metadata = clientMetadata(signed.get("client_metadata"));
            Object conn = signed.get("office_connection_id");
            require(conn == null || (conn instanceof String && ((String) conn).length() <= 255));
            String saveType = null;
            if (signed.containsKey("arguments")) {
                require(signed.get("arguments") instanceof Map);
                Object value = ((Map<?, ?>) signed.get("arguments")).get("save_type");
                require(value == null || value instanceof String);
                saveType = (String) value;
            }
            return new VerifiedRequest(adapter, claims.getJWTID(), expires, size, (String) sha,
                    metadata, (String) conn, saveType);
        } catch (HttpStorageException exception) {
            throw exception;
        } catch (Exception exception) {
            throw HttpStorageException.unauthorized();
        }
    }

    public void verifyBody(VerifiedRequest request, long length, String sha256) {
        require(request.length() == length && request.sha256().equals(sha256));
        // Stateless validation deliberately does not record jti: replay within JWT validity is not deduplicated.
    }

    private static boolean integer(Object value) { return value instanceof Long || value instanceof Integer; }
    private static void require(boolean valid) { if (!valid) throw HttpStorageException.unauthorized(); }

    private ClientMetadata clientMetadata(Object value) {
        // Unsupported operations may omit metadata; document and LIST authorization require it.
        if (value == null) return null;
        require(value instanceof Map);
        validateMetadata(value, 0);
        Map<?, ?> metadata = (Map<?, ?>) value;
        return new ClientMetadata(grants.verify(metadata));
    }

    private static void validateMetadata(Object value, int depth) {
        require(depth <= 8);
        if (value instanceof String text) require(text.length() <= 512);
        else if (value instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                require(entry.getKey() instanceof String);
                String key = (String) entry.getKey();
                require(!key.isBlank() && key.length() <= 64);
                validateMetadata(entry.getValue(), depth + 1);
            }
        } else if (value instanceof List<?> list) for (Object item : list) validateMetadata(item, depth + 1);
    }

    public record ClientMetadata(HttpStorageSession session) {}

    public record VerifiedRequest(String adapter, String jti, Instant expiresAt, long length,
                                  String sha256, ClientMetadata metadata, String connectionId, String saveType) {}
}

package com.thinkfree.drive.weboffice.httpstorage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stateless, document-scoped authorization created by Drive, not by browser-supplied TFO parameters. */
@Service
@RequiredArgsConstructor
public class HttpStorageAccessGrant {
    private final SystemConfigFindService settings;
    private final ObjectMapper mapper;

    public Map<String, Object> issue(HttpStorageSession session) {
        Map<String, Object> metadata = mapper.convertValue(session, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
        metadata.put("proof", HexFormat.of().formatHex(sign(session)));
        return metadata;
    }

    public HttpStorageSession verify(Map<?, ?> metadata) {
        try {
            Map<String, Object> fields = new LinkedHashMap<>();
            for (String field : new String[]{"tenantSeq", "userSeq", "ownerUserSeq", "resourceSeq", "adapter", "path", "app", "expiresAt"}) {
                if (!metadata.containsKey(field)) throw HttpStorageException.unauthorized();
                fields.put(field, metadata.get(field));
            }
            for (String field : new String[]{"tenantSeq", "userSeq", "ownerUserSeq", "resourceSeq", "expiresAt"}) {
                Object value = fields.get(field);
                if (!(value instanceof Integer || value instanceof Long) || ((Number) value).longValue() <= 0)
                    throw HttpStorageException.unauthorized();
            }
            for (String field : new String[]{"adapter", "path", "app"}) {
                if (!(fields.get(field) instanceof String text) || text.isBlank()) throw HttpStorageException.unauthorized();
            }
            HttpStorageSession session = mapper.convertValue(fields, HttpStorageSession.class);
            if (!session.tenantSeq().equals(TenantContext.get()) || session.expiresAt() <= Instant.now().getEpochSecond())
                throw HttpStorageException.unauthorized();
            Object proof = metadata.get("proof");
            if (!(proof instanceof String text) || !text.matches("[a-f0-9]{64}")
                    || !MessageDigest.isEqual(sign(session), HexFormat.of().parseHex(text)))
                throw HttpStorageException.unauthorized();
            return session;
        } catch (HttpStorageException exception) { throw exception; }
        catch (Exception exception) { throw HttpStorageException.unauthorized(); }
    }

    private byte[] sign(HttpStorageSession session) {
        try {
            // Route tenant selects the key. Never look up a different tenant's secret from untrusted metadata.
            Integer tenant = TenantContext.get();
            if (tenant == null || !tenant.equals(session.tenantSeq())) throw HttpStorageException.unauthorized();
            var config = settings.findOfficeConfig(tenant);
            if (!session.adapter().equals(config.getOfficeHttpStorageAdapterName())) throw HttpStorageException.unauthorized();
            String secret = config.getOfficeHttpStorageRequestJwtSecret();
            if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) throw HttpStorageException.unauthorized();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            // Domain separation: TFO callback JWTs cannot be reused as Drive access-grant proofs.
            mac.update("drive-http-storage-access-v1\n".getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(mapper.writeValueAsBytes(java.util.List.of(session.tenantSeq(), session.userSeq(),
                    session.ownerUserSeq(), session.resourceSeq(), session.adapter(), session.path(), session.app().name(), session.expiresAt())));
        } catch (HttpStorageException exception) { throw exception; }
        catch (Exception exception) { throw HttpStorageException.unauthorized(); }
    }
}

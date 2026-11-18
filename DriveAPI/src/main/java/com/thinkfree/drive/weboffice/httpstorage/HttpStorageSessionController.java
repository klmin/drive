package com.thinkfree.drive.weboffice.httpstorage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.global.annotation.AnonymousAccessible;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.permission.enums.SharePermissionType;
import com.thinkfree.drive.permission.service.ResourcePermissionService;
import com.thinkfree.drive.permission.support.RequestActor;
import com.thinkfree.drive.systemconfig.service.SystemConfigFindService;
import com.thinkfree.drive.weboffice.enums.WebOfficeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/** This endpoint uses normal Drive authentication; it is deliberately outside web-office's callback whitelist. */
@RestController
@RequiredArgsConstructor
public class HttpStorageSessionController {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final HttpStorageProperties properties;
    private final HttpStorageAccessGrant grants;
    private final HttpStorageService service;
    private final SystemConfigFindService configuration;
    private final ResourcePermissionService permissions;
    private final ObjectMapper mapper;
    private final OfficeTicketClient tickets;

    /** Mirrors TFO DOCUMENT_OPEN_AUTH_MODE / EnvVar.AuthMode values. */
    public enum AuthMode {
        TICKET, LEGACY;
        @com.fasterxml.jackson.annotation.JsonCreator
        public static AuthMode parse(String value) {
            return value == null ? TICKET : valueOf(value.toUpperCase(java.util.Locale.ROOT));
        }
        @com.fasterxml.jackson.annotation.JsonValue
        public String value() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    public record OpenRequest(@Positive Integer ownerUserSeq, @NotNull @Positive Long resourceSeq,
                              @NotNull WebOfficeType app, @Size(max = 20) String lang, AuthMode authMode) {
        public OpenRequest(Integer ownerUserSeq, Long resourceSeq, WebOfficeType app, String lang) {
            this(ownerUserSeq, resourceSeq, app, lang, null);
        }
    }
    public record OpenResponse(String url, Long expiresAt) {}
    @com.thinkfree.drive.global.config.externalapi.ExternalApi
    @com.thinkfree.drive.global.security.RequiredApiScope(com.thinkfree.drive.apikey.enums.ApiKeyScope.WRITE)
    @PostMapping("/api/v1/office/client-sessions")
    @AnonymousAccessible
    public ResponseEntity<ApiResponse<OpenResponse>> open(@RequestBody @Valid OpenRequest request,
                                                          @AuthenticationPrincipal CustomUserDetails user) throws Exception {
        if (user == null) throw HttpStorageException.unauthorized();
        int tenant = TenantContext.getOrThrow();
        if (!user.isAnonymous() && !Integer.valueOf(tenant).equals(user.getTenantSeq())) throw HttpStorageException.forbidden();
        var office = configuration.findOfficeConfig(tenant);
        if (!office.isOfficeEnabled()) throw HttpStorageException.forbidden();
        String signingKey = office.getOfficeHttpStorageRequestJwtSecret();
        String adapter = office.getOfficeHttpStorageAdapterName();
        if (signingKey == null || signingKey.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32)
            throw new HttpStorageException(HttpStatus.SERVICE_UNAVAILABLE);
        if (adapter == null || adapter.isBlank()) throw new HttpStorageException(HttpStatus.SERVICE_UNAVAILABLE);
        long expires = expiresAt();
        var provisional = new HttpStorageSession(tenant, user.getUserSeq(), (request.ownerUserSeq() == null ? user.getUserSeq() : request.ownerUserSeq()), request.resourceSeq(),
                adapter, "", request.app(), expires);
        service.requirePermission(provisional, request.app().isEditer());
        var resource = service.resource(provisional);
        String name = resource.getResourceName();
        if (name == null || name.isBlank() || name.length() > 255 || name.equals(".") || name.equals("..")
                || name.contains("/") || name.contains("\\") || name.chars().anyMatch(Character::isISOControl)) {
            throw HttpStorageException.badRequest();
        }
        String path = "documents/" + provisional.docId() + "/" + name;
        var session = new HttpStorageSession(tenant, user.getUserSeq(), (request.ownerUserSeq() == null ? user.getUserSeq() : request.ownerUserSeq()), request.resourceSeq(),
                adapter, path, request.app(), expires);
        boolean download = true;
        try {
            permissions.checkPermission(SharePermissionType.DOWNLOAD, request.resourceSeq(), provisional.ownerUserSeq(),
                    RequestActor.of(user.getUserSeq(), user.isAnonymous()), false);
        } catch (ApiRuntimeException exception) {
            if (exception.getCode().getStatus() != HttpStatus.FORBIDDEN) throw exception;
            download = false;
        }
        if (office.getOfficeDomain() == null || office.getOfficeDomain().isBlank())
            throw new HttpStorageException(HttpStatus.SERVICE_UNAVAILABLE);
        URI domain = URI.create(office.getOfficeDomain());
        if (!("https".equals(domain.getScheme()) || "http".equals(domain.getScheme())) || domain.getHost() == null
                || domain.getRawQuery() != null || domain.getRawFragment() != null || domain.getUserInfo() != null) {
            throw new HttpStorageException(HttpStatus.SERVICE_UNAVAILABLE);
        }
        String id = nextId();
        Map<String, String> params = new java.util.LinkedHashMap<>();
        params.put("docId", session.docId());
        params.put("app", request.app().name().replace("WRITE_", "WORD_").replace("CALC_", "CELL_"));
        params.put("lang", request.lang() == null ? "en_US" : request.lang());
        params.put("upgid", download ? "saveas" : "download,pdf_download,saveas");
        String documentPath = path;
        params.put("__tfo_adapter_http_storage_client_metadata", mapper.writeValueAsString(grants.issue(session)));
        Map<String, Object> identity = new java.util.LinkedHashMap<>();
        identity.put("tenantId", "drive:" + tenant);
        identity.put("userId", String.valueOf(user.getUserSeq()));
        if (user.getUsername() != null && !user.getUsername().isBlank()) identity.put("displayName", user.getUsername());
        String publicBase = office.getOfficeDomain().replaceAll("/+$", "");
        if (!publicBase.endsWith("/cloud-office")) publicBase += "/cloud-office";
        OpenResponse result;
        AuthMode mode = request.authMode() == null ? AuthMode.parse(office.getOfficeAuthMode()) : request.authMode();
        if (mode == AuthMode.LEGACY) {
            // Legacy is explicit, never a fallback after ticket issuance fails.
            var url = org.springframework.web.util.UriComponentsBuilder.fromUriString(publicBase)
                    .path("/api").pathSegment(adapter).pathSegment(documentPath.split("/")).path("/open");
            params.forEach(url::queryParam);
            url.queryParam("user_id", user.getUsername() == null ? String.valueOf(user.getUserSeq()) : user.getUsername());
            result = new OpenResponse(url.build().encode().toUriString(), expires);
        } else {
            var ticket = tickets.issue(publicBase, adapter, documentPath, params, identity, id, office.getOfficeTicketServiceToken());
            result = new OpenResponse(ticket.url(), ticket.expiresAt());
        }
        var response = ApiResponse.success(result);
        return ResponseEntity.status(response.getStatusCode()).cacheControl(CacheControl.noStore()).body(response.getBody());
    }

    private long expiresAt() {
        long ttl = properties.getSessionTtl().toSeconds();
        if (ttl <= 0) throw new HttpStorageException(HttpStatus.SERVICE_UNAVAILABLE);
        return Instant.now().plusSeconds(ttl).getEpochSecond();
    }

    private static String nextId() {
        byte[] random = new byte[32]; RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    @ExceptionHandler(HttpStorageException.class)
    public ResponseEntity<?> error(HttpStorageException exception) { return ResponseEntity.status(exception.status()).build(); }
}

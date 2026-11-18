package com.thinkfree.drive.weboffice.httpstorage;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.global.exception.ApiException;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class HttpStorageController {
    public static final String BASE = "/api/v1/tfo-http-storage/v1";
    private static final Map<String, String> METHODS = Map.of("info", "GET", "get", "GET", "put", "PUT",
            "lock", "POST", "unlock", "POST", "list", "GET", "mkdir", "POST", "rename", "POST", "delete", "DELETE");
    private static final Set<String> UNSUPPORTED = Set.of("mkdir", "rename", "delete");
    private final HttpStorageRequestVerifier verifier;
    private final HttpStorageService service;
    private final ObjectMapper mapper;

    // One boundary retains the raw encoded path for JWT verification, before decoding document segments.
    @RequestMapping(value = BASE + "/**", method = {RequestMethod.GET, RequestMethod.PUT, RequestMethod.POST, RequestMethod.DELETE})
    public ResponseEntity<?> handle(HttpServletRequest request) throws Exception {
        var verified = verifier.verifyHeaders(request);
        Route route = route(request.getRequestURI().substring(request.getContextPath().length()));
        if (!request.getMethod().equals(METHODS.get(route.operation()))) throw new HttpStorageException(HttpStatus.METHOD_NOT_ALLOWED);
        boolean put = "put".equals(route.operation());
        if (put) requireContentType(request, MediaType.APPLICATION_OCTET_STREAM);
        else if ("POST".equals(request.getMethod())) requireContentType(request, MediaType.APPLICATION_JSON);
        else if (verified.length() != 0) throw HttpStorageException.badRequest();

        // Reject unauthorized uploads before reading/staging up to 300 MiB; recheck after staging below.
        if (put) service.authorize(verified, route.path(), true);

        try (var body = HttpStorageBody.read(request.getInputStream(), verified.length(), put)) {
            verifier.verifyBody(verified, body.length(), body.sha256());
            // Unsupported operations do not access user data.
            if (UNSUPPORTED.contains(route.operation())) return json(HttpStatus.NOT_IMPLEMENTED,
                    Map.of("code", route.operation().toUpperCase(Locale.ROOT) + "_NOT_SUPPORTED"));
            // TFO registration probes the root without a user session. Authenticate the signed
            // request above, but never expose tenant files without a document session.
            if ("list".equals(route.operation()) && route.path().isEmpty() && verified.metadata() == null)
                return json(HttpStatus.NOT_IMPLEMENTED, Map.of("code", "LIST_NOT_SUPPORTED"));
            if ("list".equals(route.operation())) return json(HttpStatus.OK, service.list(verified, route.path()));
            var session = service.authorize(verified, route.path(), put);
            return switch (route.operation()) {
                case "info" -> json(HttpStatus.OK, service.info(session));
                case "get" -> {
                    var file = service.get(session);
                    yield ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .contentLength(file.contentLength()).body(new InputStreamResource(file.content()));
                }
                case "put" -> {
                    try (var input = body.open()) {
                        String id = service.put(session, verified.connectionId(), verified.saveType(), input, body.length());
                        yield json(HttpStatus.OK, Map.of("docId", id));
                    }
                }
                case "lock", "unlock" -> {
                    String owner = owner(body.json());
                    if (!owner.equals(verified.connectionId())) throw HttpStorageException.forbidden();
                    if ("lock".equals(route.operation())) service.lock(session, owner); else service.unlock(session, owner);
                    yield ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
                }
                default -> throw HttpStorageException.badRequest();
            };
        }
    }

    private String owner(byte[] bytes) throws Exception {
        var json = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(bytes);
        if (json == null || !json.isObject() || json.size() != 1 || !json.has("owner")
                || !json.get("owner").isTextual() || json.get("owner").asText().isBlank()
                || json.get("owner").asText().length() > 255) throw HttpStorageException.badRequest();
        return json.get("owner").asText();
    }

    static Route route(String raw) {
        if (!raw.startsWith(BASE + "/")) throw HttpStorageException.badRequest();
        String suffix = raw.substring(BASE.length() + 1);
        String[] parts = suffix.split("/", -1);
        String operation = parts[parts.length - 1];
        if (!METHODS.containsKey(operation)) throw new HttpStorageException(HttpStatus.NOT_FOUND);
        List<String> path = new ArrayList<>();
        for (int i = 0; i < parts.length - 1; i++) {
            String value;
            try { value = URLDecoder.decode(parts[i].replace("+", "%2B"), StandardCharsets.UTF_8); }
            catch (IllegalArgumentException exception) { throw HttpStorageException.badRequest(); }
            if (value.isEmpty() || value.equals(".") || value.equals("..") || value.contains("/") || value.contains("\\")
                    || value.chars().anyMatch(Character::isISOControl)) throw HttpStorageException.badRequest();
            path.add(value);
        }
        String decoded = String.join("/", path);
        if (decoded.length() > 4096) throw HttpStorageException.badRequest();
        return new Route(decoded, operation);
    }

    private static void requireContentType(HttpServletRequest request, MediaType expected) {
        if (request.getContentType() == null) throw HttpStorageException.badRequest();
        MediaType actual = MediaType.parseMediaType(request.getContentType());
        if (!expected.getType().equals(actual.getType()) || !expected.getSubtype().equals(actual.getSubtype())) {
            throw new HttpStorageException(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
    }

    private ResponseEntity<byte[]> json(HttpStatus status, Object value) throws Exception {
        byte[] bytes = mapper.writeValueAsBytes(value);
        if (bytes.length > 5 * 1024 * 1024) throw new HttpStorageException(HttpStatus.PAYLOAD_TOO_LARGE);
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_JSON)
                .contentLength(bytes.length).body(bytes);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<byte[]> error(Exception exception) throws Exception {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (exception instanceof HttpStorageException e) status = e.status();
        else if (exception instanceof ApiRuntimeException e) status = e.getCode().getStatus();
        else if (exception instanceof ApiException e) status = e.getStatus();
        else if (exception instanceof com.fasterxml.jackson.core.JsonProcessingException || exception instanceof IllegalArgumentException) status = HttpStatus.BAD_REQUEST;
        return json(status, Map.of("code", "HTTP_STORAGE_" + status.value()));
    }

    record Route(String path, String operation) {}
}

package com.thinkfree.drive.weboffice.httpstorage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.common.file.model.FileData;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.permission.service.ResourcePermissionService;
import com.thinkfree.drive.permission.enums.SharePermissionType;
import com.thinkfree.drive.permission.support.RequestActor;
import com.thinkfree.drive.resource.data.ResourceDetailData;
import com.thinkfree.drive.resource.entity.Resource;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.tenant.data.TenantData;
import com.thinkfree.drive.tenant.enums.TenantStatus;
import com.thinkfree.drive.tenant.service.TenantFindService;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.data.UserDetailWithRolesData;
import com.thinkfree.drive.user.enums.*;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.weboffice.dto.WebOfficeSaveDto;
import com.thinkfree.drive.weboffice.enums.*;
import com.thinkfree.drive.weboffice.service.WebOfficeService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/** Real JWTs, controller binding, body staging, H2 locks and Drive service dispatch. Only infrastructure is substituted. */
class HttpStorageContractTest {
    static final String SECRET = "test-only-http-storage-secret-32-bytes";
    static final String ID = "A".repeat(43);
    static final String PATH = "documents/4-123/report.docx";
    static final String URL = HttpStorageController.BASE + "/" + PATH;
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    HttpStorageProperties properties;
    com.thinkfree.drive.systemconfig.service.SystemConfigFindService settings;
    HttpStorageAccessGrant grants;
    Map<String, HttpStorageSession> sessions = new HashMap<>();
    Map<String, Map<String, Object>> proofs = new HashMap<>();
    com.thinkfree.drive.resource.service.ResourceLockUnlockService fileLocks;
    HttpStorageService service;
    HttpStorageLocks locks;
    JdbcTemplate jdbc;
    ResourceFindService resources;
    UserFindService users;
    TenantFindService tenants;
    ResourcePermissionService permissions;
    StorageService storage;
    WebOfficeService office;
    MockMvc mvc;
    AtomicReference<byte[]> file = new AtomicReference<>("initial document".getBytes(StandardCharsets.UTF_8));

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        TenantContext.set(10);
        properties = new HttpStorageProperties();
        settings = mock(com.thinkfree.drive.systemconfig.service.SystemConfigFindService.class);
        when(settings.findOfficeConfig(10)).thenReturn(com.thinkfree.drive.systemconfig.data.OfficeData.builder()
                .officeDomain("https://office.example.test")
                .officeHttpStorageAdapterName("10").officeHttpStorageRequestJwtSecret(SECRET)
                .officeTicketServiceToken("tenant-test-token").build());
        grants = new HttpStorageAccessGrant(settings, mapper);
        var datasource = new JdbcDataSource(); datasource.setURL("jdbc:h2:mem:tfo-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(datasource);
        jdbc.execute("CREATE TABLE TB_RESOURCE(user_seq INT, resource_seq BIGINT, is_locked CHAR(1), locked_date TIMESTAMP(0), update_user_seq INT, update_date TIMESTAMP, PRIMARY KEY(user_seq, resource_seq))");
        jdbc.update("INSERT INTO TB_RESOURCE(user_seq,resource_seq,is_locked) VALUES (4,123,'N')");
        resources = mock(ResourceFindService.class); users = mock(UserFindService.class);
        fileLocks = mock(com.thinkfree.drive.resource.service.ResourceLockUnlockService.class);
        doAnswer(call -> { jdbc.update("UPDATE TB_RESOURCE SET is_locked='Y' WHERE user_seq=? AND resource_seq=?", call.getArgument(1, Integer.class), call.getArgument(0, Long.class)); return null; })
                .when(fileLocks).lockResource(anyLong(), anyInt(), anyInt(), any());
        doAnswer(call -> { jdbc.update("UPDATE TB_RESOURCE SET is_locked='N' WHERE user_seq=? AND resource_seq=?", call.getArgument(1, Integer.class), call.getArgument(0, Long.class)); return null; })
                .when(fileLocks).unlockResource(anyLong(), anyInt(), anyInt(), any());
        locks = new HttpStorageLocks(resources, fileLocks);
        tenants = mock(TenantFindService.class);
        TenantData tenant = mock(TenantData.class);
        when(tenant.getStatus()).thenReturn(TenantStatus.ACTIVE);
        when(tenants.findByTenantSeq(10)).thenReturn(tenant);
        permissions = mock(ResourcePermissionService.class); storage = mock(StorageService.class); office = mock(WebOfficeService.class);
        UserDetailWithRolesData user = mock(UserDetailWithRolesData.class);
        when(user.getUserSeq()).thenReturn(4); when(user.getUserName()).thenReturn("Test user");
        when(user.getRoles()).thenReturn(List.of(RoleType.USER)); when(user.getTenantSeq()).thenReturn(10); when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(users.findDetailWithRoles(4)).thenReturn(user);
        when(users.findDetailByUserSeq(4)).thenReturn(user);
        when(resources.findDetailOrNull(any())).thenAnswer(call -> document(123L, 4, "report.docx", "4/123"));
        when(storage.getFileData("4/123")).thenAnswer(call -> new FileData("application/octet-stream", new ByteArrayInputStream(file.get()), file.get().length));
        when(office.save(any())).thenAnswer(call -> { WebOfficeSaveDto dto = call.getArgument(0); file.set(dto.getInputStream().readAllBytes()); return null; });
        service = new HttpStorageService(resources, users, tenants, permissions, storage, office, mock(ResourceAuditService.class), locks);
        mvc = MockMvcBuilders.standaloneSetup(new HttpStorageController(new HttpStorageRequestVerifier(settings, grants), service, mapper)).build();
        session(WebOfficeType.WRITE_EDITOR, Instant.now().plusSeconds(3600).getEpochSecond(), PATH, 10);
    }

    @AfterEach void clearTenant() { TenantContext.clear(); }

    ResourceDetailData document(long resourceSeq, int ownerSeq, String name, String storageKey) {
        ResourceDetailData resource = mock(ResourceDetailData.class);
        when(resource.getResourceSeq()).thenReturn(resourceSeq);
        when(resource.getUserSeq()).thenReturn(ownerSeq);
        when(resource.getResourceName()).thenReturn(name);
        when(resource.getResourceSaveName()).thenReturn(storageKey);
        when(resource.getSizeByte()).thenAnswer(call -> (long) file.get().length);
        when(resource.getCurrentVersionNumber()).thenReturn(1);
        return resource;
    }

    @Test
    void configuredStorageIdReplacesTenantNumberWithoutGrace() throws Exception {
        String adapter = "330e50fe-2c9e-4dbd-bea7-da552046ccf9";
        when(settings.findOfficeConfig(10)).thenReturn(com.thinkfree.drive.systemconfig.data.OfficeData.builder()
                .officeDomain("https://office.example.test").officeHttpStorageAdapterName(adapter)
                .officeHttpStorageRequestJwtSecret(SECRET).officeTicketServiceToken("tenant-test-token").build());
        assertEquals(401, signed("GET", URL + "/info", new byte[0]).getResponse().getStatus());
        remember(ID, new HttpStorageSession(10, 4, 4, 123L, adapter, PATH, WebOfficeType.WRITE_EDITOR,
                Instant.now().plusSeconds(300).getEpochSecond()));
        var request = claim("GET", URL + "/info", new byte[0]);
        request.put("adapter", adapter);
        assertEquals(200, mvc.perform(call("GET", URL + "/info", new byte[0], token(request)))
                .andReturn().getResponse().getStatus());
    }

    void session(WebOfficeType app, long expires, String path, int tenant) {
        remember(ID, new HttpStorageSession(tenant, 4, 4, 123L, "10", path, app, expires));
    }

    void remember(String id, HttpStorageSession session) {
        sessions.put(id, session);
        Integer route = TenantContext.get();
        try {
            TenantContext.set(session.tenantSeq());
            proofs.put(id, grants.issue(session));
        } finally { if (route == null) TenantContext.clear(); else TenantContext.set(route); }
    }

    Map<String, Object> metadata(int tenant, int user, String id) {
        var proof = new HashMap<>(proofs.getOrDefault(id, Map.of()));
        proof.put("tenantSeq", tenant); proof.put("userSeq", user);
        return proof;
    }

    Map<String, Object> claim(String method, String url, byte[] bytes) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("adapter", "10"); result.put("method", method); result.put("path", url);
        result.put("content_length", bytes.length);
        result.put("content_sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        result.put("office_connection_id", "connection-1"); result.put("client_metadata", metadata(10, 4, ID));
        if (method.equals("PUT")) result.put("content_type", "application/octet-stream");
        else if (method.equals("POST")) result.put("content_type", "application/json");
        return result;
    }

    String token(Map<String, Object> claim) throws Exception {
        return token(claim, Instant.now().minusSeconds(1), Instant.now().plusSeconds(50), SECRET, "thinkfree-office", "tfo-storage-request+jwt");
    }

    String token(Map<String, Object> claim, Instant issued, Instant expires, String key, String issuer, String type) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).audience("tfo-http-storage-provider")
                .issueTime(Date.from(issued)).expirationTime(Date.from(expires)).jwtID(UUID.randomUUID().toString()).claim("request", claim).build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(new JOSEObjectType(type)).build(), claims);
        jwt.sign(new MACSigner(key.getBytes(StandardCharsets.UTF_8))); return jwt.serialize();
    }

    MockHttpServletRequestBuilder call(String method, String url, byte[] bytes, String token) {
        var builder = request(org.springframework.http.HttpMethod.valueOf(method), URI.create(url)).content(bytes);
        if (token != null) builder.header(HttpStorageRequestVerifier.HEADER, token);
        if (method.equals("PUT") || method.equals("POST")) {
            builder.header("Content-Length", bytes.length).contentType(method.equals("PUT") ? "application/octet-stream" : "application/json");
        }
        return builder;
    }

    MvcResult signed(String method, String url, byte[] bytes) throws Exception {
        return mvc.perform(call(method, url, bytes, token(claim(method, url, bytes)))).andReturn();
    }

    @Test void accessGrantSurvivesNewVerifierInstanceWithoutStoredSession() throws Exception {
        var metadata = metadata(10, 4, ID);
        var second = new HttpStorageAccessGrant(settings, new ObjectMapper().findAndRegisterModules());
        sessions.clear(); proofs.clear();
        assertEquals(123L, second.verify(metadata).resourceSeq());
        var verifier = new HttpStorageRequestVerifier(settings, second);
        var claims = new LinkedHashMap<String, Object>();
        claims.put("adapter", "10"); claims.put("method", "GET"); claims.put("path", URL + "/get");
        claims.put("content_length", 0); claims.put("content_sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new byte[0])));
        claims.put("client_metadata", metadata);
        var verified = verifier.verifyHeaders(call("GET", URL + "/get", new byte[0], token(claims)).buildRequest(null));
        assertEquals(123L, service.authorize(verified, PATH, false).resourceSeq());
    }

    @Test void rotatingTenantSigningKeyInvalidatesPreviouslyIssuedAccessGrant() {
        var metadata = metadata(10, 4, ID);
        when(settings.findOfficeConfig(10)).thenReturn(com.thinkfree.drive.systemconfig.data.OfficeData.builder()
                .officeHttpStorageAdapterName("10").officeHttpStorageRequestJwtSecret("replacement-test-only-secret-32-bytes").build());
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(HttpStorageException.class, () -> grants.verify(metadata)).status());
    }

    @Test void infoGetPutRoundTripHasProtocolFramingAndPersistsAutosave() throws Exception {
        var info = signed("GET", URL + "/info", new byte[0]).getResponse();
        assertEquals(200, info.getStatus()); assertEquals(info.getContentAsByteArray().length, info.getContentLength());
        assertEquals(PATH, mapper.readTree(info.getContentAsByteArray()).get("path").asText());
        byte[] saved = "changed document\n한글".getBytes(StandardCharsets.UTF_8);
        var claims = claim("PUT", URL + "/put", saved); claims.put("arguments", Map.of("save_type", "autosave"));
        var put = mvc.perform(call("PUT", URL + "/put", saved, token(claims))).andReturn().getResponse();
        assertEquals(200, put.getStatus()); assertEquals("4-123", mapper.readTree(put.getContentAsByteArray()).get("docId").asText());
        assertEquals(put.getContentAsByteArray().length, put.getContentLength());
        var get = signed("GET", URL + "/get", new byte[0]).getResponse();
        assertEquals(200, get.getStatus()); assertArrayEquals(saved, get.getContentAsByteArray());
        assertEquals(saved.length, get.getContentLength()); assertEquals("application/octet-stream", get.getContentType());
        verify(office).save(argThat(dto -> dto.getWebOfficeSaveType() == WebOfficeSaveType.SAVE && dto.getActor().getUserSeq() == 4));
    }

    @Test void readOnlySessionCannotSaveEvenWithValidTfoSignature() throws Exception {
        session(WebOfficeType.WRITE_VIEWER, Instant.now().plusSeconds(3600).getEpochSecond(), PATH, 10);
        assertEquals(403, signed("PUT", URL + "/put", new byte[]{1}).getResponse().getStatus());
        verifyNoInteractions(office);
        assertFalse(mapper.readTree(signed("GET", URL + "/info", new byte[0]).getResponse().getContentAsByteArray()).get("writable").asBoolean());
    }

    @Test void removedPermissionIsCheckedAgainOnEveryRequest() throws Exception {
        assertEquals(200, signed("GET", URL + "/get", new byte[0]).getResponse().getStatus());
        doThrow(HttpStorageException.forbidden()).when(permissions).checkPermission(any(), anyLong(), anyInt(), any(), any());
        assertEquals(403, signed("GET", URL + "/get", new byte[0]).getResponse().getStatus());
        assertEquals(403, signed("PUT", URL + "/put", new byte[]{1}).getResponse().getStatus());
        verifyNoInteractions(office);
    }

    @Test void forgedBodyNeverReachesStorage() throws Exception {
        var claims = claim("PUT", URL + "/put", new byte[]{1});
        assertEquals(401, mvc.perform(call("PUT", URL + "/put", new byte[]{2}, token(claims))).andReturn().getResponse().getStatus());
        verifyNoInteractions(office);
    }

    @Test void repeatedSignedRequestIsAcceptedWithoutReplayState() throws Exception {
        String jwt = token(claim("GET", URL + "/info", new byte[0]));
        assertEquals(200, mvc.perform(call("GET", URL + "/info", new byte[0], jwt)).andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(call("GET", URL + "/info", new byte[0], jwt)).andReturn().getResponse().getStatus());
    }

    @Test void signatureDoesNotReplaceDocumentSession() throws Exception {
        var claims = claim("GET", URL + "/info", new byte[0]); claims.remove("client_metadata");
        assertEquals(401, mvc.perform(call("GET", URL + "/info", new byte[0], token(claims))).andReturn().getResponse().getStatus());
    }

    @ParameterizedTest @ValueSource(strings = {
            "{}", "{\"tenantSeq\":10}", "{\"userSeq\":4}",
            "{\"tenantSeq\":\"10\",\"userSeq\":4}", "{\"tenantSeq\":10,\"userSeq\":\"4\"}",
            "{\"tenantSeq\":10.5,\"userSeq\":4}", "{\"tenantSeq\":10,\"userSeq\":4.5}",
            "{\"tenantSeq\":0,\"userSeq\":4}", "{\"tenantSeq\":10,\"userSeq\":0}",
            "{\"tenantSeq\":-1,\"userSeq\":4}", "{\"tenantSeq\":10,\"userSeq\":-1}",
            "{\"tenantSeq\":2147483648,\"userSeq\":4}", "{\"tenantSeq\":10,\"userSeq\":2147483648}",
            "{\"tenantSeq\":true,\"userSeq\":4}", "{\"tenantSeq\":10,\"userSeq\":null}"
    })
    void missingOrMalformedIdentityNeverReachesBusinessLogic(String json) throws Exception {
        var metadata = mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        var claims = claim("GET", URL + "/info", new byte[0]); claims.put("client_metadata", metadata);
        assertEquals(401, mvc.perform(call("GET", URL + "/info", new byte[0], token(claims))).andReturn().getResponse().getStatus());
        verifyNoInteractions(tenants, users, resources, permissions, storage, office);
    }

    @Test void identityWithoutServerIssuedSessionIsRejected() throws Exception {
        var claims = claim("GET", URL + "/get", new byte[0]);
        claims.put("client_metadata", Map.of("tenantSeq", 10, "userSeq", 4));
        assertEquals(401, mvc.perform(call("GET", URL + "/get", new byte[0], token(claims))).andReturn().getResponse().getStatus());
        claims.put("client_metadata", metadata(10, 4, "B".repeat(43)));
        assertEquals(401, mvc.perform(call("GET", URL + "/get", new byte[0], token(claims))).andReturn().getResponse().getStatus());
        verifyNoInteractions(tenants, users, resources, permissions, storage, office);
    }

    @ParameterizedTest @ValueSource(strings = {"tenantSeq", "userSeq", "ownerUserSeq", "resourceSeq", "path", "app", "adapter", "expiresAt", "proof"})
    void changedIdentityIsRejectedEvenWhenTfoSignsIt(String field) throws Exception {
        var metadata = new HashMap<>(metadata(10, 4, ID)); metadata.put(field, 11);
        var claims = claim("GET", URL + "/get", new byte[0]); claims.put("client_metadata", metadata);
        assertEquals(401, mvc.perform(call("GET", URL + "/get", new byte[0], token(claims))).andReturn().getResponse().getStatus());
        verifyNoInteractions(tenants, users, resources, permissions, storage, office);
    }

    @Test void metadataCannotOverrideTheTenantResolvedByTheFilter() throws Exception {
        TenantContext.set(11);
        assertEquals(401, signed("GET", URL + "/get", new byte[0]).getResponse().getStatus());
        TenantContext.clear();
        assertEquals(401, signed("GET", URL + "/get", new byte[0]).getResponse().getStatus());
        verifyNoInteractions(tenants, users, resources, permissions, storage, office);
    }

    @Test void tenantPathIsIncludedInSignatureButRemovedForOperationRouting() throws Exception {
        String prefix = "/t/acme";
        String url = prefix + URL + "/get";
        var response = mvc.perform(call("GET", url, new byte[0], token(claim("GET", url, new byte[0])))
                .contextPath(prefix)).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertArrayEquals(file.get(), response.getContentAsByteArray());

        // Signing the unprefixed API path cannot authorize a tenant-prefixed request.
        var missingPrefix = claim("GET", URL + "/get", new byte[0]);
        assertEquals(401, mvc.perform(call("GET", url, new byte[0], token(missingPrefix))
                .contextPath(prefix)).andReturn().getResponse().getStatus());

        // A signature for tenant A's URL cannot be replayed against tenant B's URL.
        assertEquals(401, mvc.perform(call("GET", "/t/other" + URL + "/get", new byte[0],
                token(claim("GET", url, new byte[0]))).contextPath("/t/other"))
                .andReturn().getResponse().getStatus());
    }

    @Test void tenantAdaptersPreserveActorAndOwner() throws Exception {
        assertEquals(200, signed("GET", URL + "/get", new byte[0]).getResponse().getStatus());
        TenantContext.set(11);
        when(settings.findOfficeConfig(11)).thenReturn(com.thinkfree.drive.systemconfig.data.OfficeData.builder().officeHttpStorageAdapterName("11")
            .officeHttpStorageRequestJwtSecret(SECRET).build());
        TenantData tenant = mock(TenantData.class); when(tenant.getStatus()).thenReturn(TenantStatus.ACTIVE);
        when(tenants.findByTenantSeq(11)).thenReturn(tenant);
        for (int seq : List.of(7, 8)) {
            UserDetailWithRolesData user = mock(UserDetailWithRolesData.class);
            when(user.getUserSeq()).thenReturn(seq); when(user.getTenantSeq()).thenReturn(11);
            when(user.getRoles()).thenReturn(List.of(RoleType.USER)); when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
            when(users.findDetailWithRoles(seq)).thenReturn(user);
            when(users.findDetailByUserSeq(seq)).thenReturn(user);
        }
        String secondId = "B".repeat(43), path = "documents/8-456/shared.docx";
        String url = HttpStorageController.BASE + "/" + path;
        remember(secondId, new HttpStorageSession(11, 7, 8, 456L, "11", path,
                WebOfficeType.WRITE_EDITOR, Instant.now().plusSeconds(3600).getEpochSecond()));
        jdbc.update("INSERT INTO TB_RESOURCE(user_seq,resource_seq,is_locked) VALUES (8,456,'N')");
        when(resources.findDetailOrNull(argThat(query -> query.getUserSeq() == 8 && query.getResourceSeq() == 456L)))
                .thenAnswer(call -> document(456L, 8, "shared.docx", "8/456"));
        when(storage.getFileData("8/456")).thenAnswer(call -> new FileData("application/octet-stream", new ByteArrayInputStream(file.get()), file.get().length));
        byte[] saved = "second tenant document".getBytes(StandardCharsets.UTF_8);
        for (String operation : List.of("put", "get")) {
            String method = operation.equals("put") ? "PUT" : "GET";
            byte[] body = operation.equals("put") ? saved : new byte[0];
            var claims = claim(method, url + "/" + operation, body); claims.put("client_metadata", metadata(11, 7, secondId)); claims.put("adapter", "11");
            var response = mvc.perform(call(method, url + "/" + operation, body, token(claims))).andReturn().getResponse();
            assertEquals(200, response.getStatus());
            if (operation.equals("get")) assertArrayEquals(saved, response.getContentAsByteArray());
        }
        verify(permissions, atLeastOnce()).checkPermission(SharePermissionType.EDIT, 456L, 8, RequestActor.user(7), false);
        verify(permissions, atLeastOnce()).checkPermission(SharePermissionType.VIEW, 456L, 8, RequestActor.user(7), false);
        verify(office).save(argThat(dto -> dto.getLoginUserSeq() == 7 && dto.getUserSeq() == 8
                && dto.getResourceSeq() == 456L && dto.getActor().getUserSeq() == 7));
        // A valid session for this tenant cannot be redirected to the first tenant's document.
        var claims = claim("GET", URL + "/get", new byte[0]); claims.put("client_metadata", metadata(11, 7, secondId)); claims.put("adapter", "11");
        assertEquals(403, mvc.perform(call("GET", URL + "/get", new byte[0], token(claims))).andReturn().getResponse().getStatus());
    }

    @ParameterizedTest @ValueSource(strings = {"tenantInactive", "actorInactive", "actorTenant", "ownerInactive", "ownerTenant"})
    void currentTenantAndAccountStateAreCheckedEvenForExistingSessions(String change) throws Exception {
        UserDetailWithRolesData actor = mock(UserDetailWithRolesData.class);
        when(actor.getUserSeq()).thenReturn(5); when(actor.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(actor.getTenantSeq()).thenReturn(10); when(actor.getRoles()).thenReturn(List.of(RoleType.USER));
        when(users.findDetailWithRoles(5)).thenReturn(actor);
        remember(ID, new HttpStorageSession(10, 5, 4, 123L, "10", PATH, WebOfficeType.WRITE_EDITOR,
                Instant.now().plusSeconds(3600).getEpochSecond()));
        var claims = claim("GET", URL + "/get", new byte[0]); claims.put("client_metadata", metadata(10, 5, ID));
        assertEquals(200, mvc.perform(call("GET", URL + "/get", new byte[0], token(claims))).andReturn().getResponse().getStatus());
        UserDetailData owner = users.findDetailByUserSeq(4);
        TenantData tenant = tenants.findByTenantSeq(10);
        switch (change) {
            case "tenantInactive" -> when(tenant.getStatus()).thenReturn(TenantStatus.INACTIVE);
            case "actorInactive" -> when(actor.getStatus()).thenReturn(UserStatus.INACTIVE);
            case "actorTenant" -> when(actor.getTenantSeq()).thenReturn(11);
            case "ownerInactive" -> when(owner.getStatus()).thenReturn(UserStatus.INACTIVE);
            case "ownerTenant" -> when(owner.getTenantSeq()).thenReturn(11);
        }
        clearInvocations(resources, permissions, storage, office);
        assertEquals(403, mvc.perform(call("GET", URL + "/get", new byte[0], token(claims))).andReturn().getResponse().getStatus());
        verifyNoInteractions(resources, permissions, storage, office);
    }

    @Test void expiredSessionAndWrongTenantAreRejected() throws Exception {
        session(WebOfficeType.WRITE_EDITOR, Instant.now().minusSeconds(2).getEpochSecond(), PATH, 10);
        assertEquals(401, signed("GET", URL + "/info", new byte[0]).getResponse().getStatus());
        session(WebOfficeType.WRITE_EDITOR, Instant.now().plusSeconds(3600).getEpochSecond(), PATH, 10);
        proofs.get(ID).put("ownerUserSeq", 11);
        assertEquals(401, signed("GET", URL + "/info", new byte[0]).getResponse().getStatus());
        TenantContext.set(11);
        assertEquals(401, signed("GET", URL + "/info", new byte[0]).getResponse().getStatus());
    }

    @Test void anotherDocumentCannotBeAccessedWithSameSession() throws Exception {
        assertEquals(403, signed("GET", URL.replace("4-123", "4-124") + "/get", new byte[0]).getResponse().getStatus());
        verifyNoInteractions(storage);
    }



    @Test void lockAndUnlockDelegateToExistingDriveFileLock() throws Exception {
        byte[] body = "{\"owner\":\"connection-1\"}".getBytes(StandardCharsets.UTF_8);
        assertEquals(204, signed("POST", URL + "/lock", body).getResponse().getStatus());
        verify(fileLocks).lockResource(eq(123L), eq(4), eq(4), any());
        assertEquals("Y", jdbc.queryForObject("SELECT is_locked FROM TB_RESOURCE", String.class));
        assertEquals(200, signed("PUT", URL + "/put", new byte[]{1}).getResponse().getStatus());
        assertEquals(204, signed("POST", URL + "/unlock", body).getResponse().getStatus());
        verify(fileLocks).unlockResource(eq(123L), eq(4), eq(4), any());
        assertEquals("N", jdbc.queryForObject("SELECT is_locked FROM TB_RESOURCE", String.class));
    }

    @Test void ownerMustMatchSignedOfficeConnection() throws Exception {
        assertEquals(403, signed("POST", URL + "/lock", "{\"owner\":\"other\"}".getBytes()).getResponse().getStatus());
    }



    @ParameterizedTest @ValueSource(strings = {"mkdir", "rename", "delete"})
    void unsupportedCapabilitiesStillRequireAuthentication(String operation) throws Exception {
        String method = operation.equals("list") ? "GET" : operation.equals("delete") ? "DELETE" : "POST";
        String url = HttpStorageController.BASE + "/" + operation;
        byte[] bytes = method.equals("POST") ? "{\"name\":\"test\"}".getBytes() : new byte[0];
        assertEquals(401, mvc.perform(call(method, url, bytes, null)).andReturn().getResponse().getStatus());
        var claims = claim(method, url, bytes); claims.remove("client_metadata");
        var response = mvc.perform(call(method, url, bytes, token(claims))).andReturn().getResponse();
        assertEquals(501, response.getStatus());
        assertEquals(Map.of("code", operation.toUpperCase(Locale.ROOT) + "_NOT_SUPPORTED"), mapper.readValue(response.getContentAsByteArray(), Map.class));
    }

    @ParameterizedTest @ValueSource(strings = {"key", "issuer", "type", "expired", "future", "lifetime", "adapter", "path", "method", "sha", "length"})
    void invalidJwtIsRejectedBeforeBusinessLogic(String kind) throws Exception {
        var claims = claim("GET", URL + "/info", new byte[0]);
        Instant issued = Instant.now().minusSeconds(1), expires = Instant.now().plusSeconds(50);
        String key = SECRET, issuer = "thinkfree-office", type = "tfo-storage-request+jwt";
        switch (kind) {
            case "key" -> key = "different-test-only-key-32-bytes-long";
            case "issuer" -> issuer = "other";
            case "type" -> type = "JWT";
            case "expired" -> { issued = Instant.now().minusSeconds(60); expires = Instant.now().minusSeconds(1); }
            case "future" -> { issued = Instant.now().plusSeconds(5); expires = Instant.now().plusSeconds(50); }
            case "lifetime" -> expires = Instant.now().plusSeconds(120);
            case "adapter" -> claims.put("adapter", "unknown");
            case "path" -> claims.put("path", URL + "/get");
            case "method" -> claims.put("method", "DELETE");
            case "sha" -> claims.put("content_sha256", "0".repeat(64));
            case "length" -> claims.put("content_length", 1);
        }
        assertEquals(401, mvc.perform(call("GET", URL + "/info", new byte[0], token(claims, issued, expires, key, issuer, type)))
                .andReturn().getResponse().getStatus());
        verifyNoInteractions(resources, users, permissions, storage, office);
    }

    @Test void tooLargeUploadIsRejectedWithoutConsumingBody() throws Exception {
        var claims = claim("PUT", URL + "/put", new byte[0]); claims.put("content_length", HttpStorageBody.MAX_FILE_BYTES + 1);
        var request = request(org.springframework.http.HttpMethod.PUT, URI.create(URL + "/put"))
                .header(HttpStorageRequestVerifier.HEADER, token(claims)).header("Content-Length", HttpStorageBody.MAX_FILE_BYTES + 1)
                .contentType("application/octet-stream");
        assertEquals(413, mvc.perform(request).andReturn().getResponse().getStatus());
        verifyNoInteractions(office);
    }

    @Test void unicodePathAndEmptyFileWork() throws Exception {
        String path = "documents/4-123/보고서 + 최종.docx";
        session(WebOfficeType.WRITE_EDITOR, Instant.now().plusSeconds(3600).getEpochSecond(), path, 10);
        String url = HttpStorageController.BASE + "/documents/4-123/" + org.springframework.web.util.UriUtils.encode("보고서 + 최종.docx", StandardCharsets.UTF_8);
        assertEquals(200, signed("GET", url + "/info", new byte[0]).getResponse().getStatus());
        assertEquals(200, signed("PUT", url + "/put", new byte[0]).getResponse().getStatus());
        assertEquals(0, signed("GET", url + "/get", new byte[0]).getResponse().getContentLength());
    }

    @ParameterizedTest @ValueSource(strings = {"../report.docx", "%2e%2e/report.docx", "a%2Fb/report.docx", "a%5Cb/report.docx", "a//report.docx"})
    void pathTraversalIsRejected(String path) throws Exception {
        assertEquals(400, signed("GET", HttpStorageController.BASE + "/" + path + "/info", new byte[0]).getResponse().getStatus());
    }


    @Test void disabledLaunchesDoNotInvalidateExistingDocumentCallbacks() throws Exception {
        when(settings.findOfficeConfig(10)).thenReturn(com.thinkfree.drive.systemconfig.data.OfficeData.builder()
                .officeConnectionType("none").officeHttpStorageAdapterName("10").officeHttpStorageRequestJwtSecret(SECRET).build());
        assertEquals(200, signed("GET", URL + "/info", new byte[0]).getResponse().getStatus());
        assertEquals(200, signed("PUT", URL + "/put", "retained session save".getBytes(StandardCharsets.UTF_8)).getResponse().getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"WRITE_WATCHER", "CALC_WATCHER", "SHOW_WATCHER", "HWP_WATCHER", "PDF_WATCHER"})
    void removedWatcherModesAreRejectedBeforeTicketIssuance(String app) throws Exception {
        var tickets = mock(OfficeTicketClient.class);
        var controller = new HttpStorageSessionController(properties, grants, service, settings, permissions, mapper, tickets);
        var routes = MockMvcBuilders.standaloneSetup(controller).build();
        var response = routes.perform(request(org.springframework.http.HttpMethod.POST,
                URI.create("/api/v1/office/client-sessions"))
                .contentType("application/json")
                .content(mapper.writeValueAsBytes(Map.of("resourceSeq", 123, "app", app))))
                .andReturn().getResponse();
        assertEquals(400, response.getStatus());
        verifyNoInteractions(tickets, resources, storage, office);
    }

    @Test void disabledOfficeRejectsUrlIssuanceBeforeLoadingDocumentOrCallingTfo() {
        var configuration = mock(com.thinkfree.drive.systemconfig.service.SystemConfigFindService.class);
        when(configuration.findOfficeConfig(10)).thenReturn(com.thinkfree.drive.systemconfig.data.OfficeData.builder().officeConnectionType("none").build());
        var tickets = mock(OfficeTicketClient.class);
        var controller = new HttpStorageSessionController(properties, grants, service, configuration, permissions, mapper, tickets);
        var principal = mock(com.thinkfree.drive.global.security.CustomUserDetails.class);
        when(principal.getTenantSeq()).thenReturn(10);
        assertThrows(HttpStorageException.class, () -> controller.open(new HttpStorageSessionController.OpenRequest(4, 123L, WebOfficeType.WRITE_EDITOR, "ko_KR"), principal));
        verifyNoInteractions(tickets);
    }
    @Test void sessionIssuerUsesTenantHttpStorageSettings() throws Exception {
        var configuration = mock(com.thinkfree.drive.systemconfig.service.SystemConfigFindService.class);
        when(configuration.findOfficeConfig(10)).thenReturn(com.thinkfree.drive.systemconfig.data.OfficeData.builder()
                .officeDomain("https://office.example.test")
                .officeHttpStorageAdapterName("10").officeHttpStorageRequestJwtSecret(SECRET)
                .officeTicketServiceToken("tenant-test-token").build());

        var tickets = mock(OfficeTicketClient.class);
        when(tickets.issue(anyString(), anyString(), anyString(), anyMap(), anyMap(), anyString(), anyString()))
                .thenReturn(new OfficeTicketClient.Issued("https://office.example.test/cloud-office/api/open-document?ticket=test", Instant.now().plusSeconds(60).getEpochSecond()));
        var controller = new HttpStorageSessionController(properties, grants, service, configuration, permissions, mapper, tickets);
        var principal = mock(com.thinkfree.drive.global.security.CustomUserDetails.class);
        when(principal.getUserSeq()).thenReturn(4); when(principal.getTenantSeq()).thenReturn(10);
        when(principal.getUsername()).thenReturn("Test user & 한글");
        var response = controller.open(new HttpStorageSessionController.OpenRequest(4, 123L, WebOfficeType.WRITE_EDITOR, "ko_KR"), principal);
        String url = response.getBody().getData().url();
        assertEquals("https://office.example.test/cloud-office/api/open-document?ticket=test", url);
        assertEquals("no-store", response.getHeaders().getCacheControl());
        var params = org.mockito.ArgumentCaptor.forClass(Map.class);
        var identity = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(tickets).issue(eq("https://office.example.test/cloud-office"), eq("10"), eq(PATH), params.capture(), identity.capture(), anyString(), eq("tenant-test-token"));
        assertEquals("4", identity.getValue().get("userId"));
        assertEquals("drive:10", identity.getValue().get("tenantId"));
        assertEquals("Test user & 한글", identity.getValue().get("displayName"));
        assertEquals("WORD_EDITOR", params.getValue().get("app"));
        String metadata = (String) params.getValue().get("__tfo_adapter_http_storage_client_metadata");
        assertFalse(mapper.readTree(metadata).has("sessionId"));
        assertEquals(4, grants.verify(mapper.readValue(metadata, Map.class)).userSeq());
        assertEquals(10, mapper.readTree(metadata).get("tenantSeq").intValue());
        assertEquals(4, mapper.readTree(metadata).get("userSeq").intValue());
        var claims = claim("GET", URL + "/info", new byte[0]); claims.put("client_metadata", mapper.readValue(metadata, Map.class));
        assertEquals(200, mvc.perform(call("GET", URL + "/info", new byte[0], token(claims))).andReturn().getResponse().getStatus());
        assertThrows(HttpStorageException.class, () -> controller.open(
                new HttpStorageSessionController.OpenRequest(4, 123L, WebOfficeType.WRITE_EDITOR, "en_US"), null));
        // The browser entry point must never inherit the callback's login bypass.
        assertFalse(com.thinkfree.drive.global.security.SecurityPaths.AUTH_WHITELIST_MATCHER.matches(
                request(org.springframework.http.HttpMethod.POST, "/api/v1/office/client-sessions").buildRequest(null)));
    }

    @Test void legacyDoesNotCallTicketIssuerAndEscapesDisplayName() throws Exception {
        var configuration = mock(com.thinkfree.drive.systemconfig.service.SystemConfigFindService.class);
        when(configuration.findOfficeConfig(10)).thenReturn(com.thinkfree.drive.systemconfig.data.OfficeData.builder()
                .officeDomain("https://office.example.test")
                .officeHttpStorageAdapterName("10").officeHttpStorageRequestJwtSecret(SECRET)
                .officeTicketServiceToken("tenant-test-token").build());

        var tickets = mock(OfficeTicketClient.class);
        var controller = new HttpStorageSessionController(properties, grants, service, configuration, permissions, mapper, tickets);
        var principal = mock(com.thinkfree.drive.global.security.CustomUserDetails.class);
        when(principal.getUserSeq()).thenReturn(4); when(principal.getTenantSeq()).thenReturn(10);
        when(principal.getUsername()).thenReturn("Name & 한글");
        var result = controller.open(new HttpStorageSessionController.OpenRequest(4,123L,WebOfficeType.WRITE_EDITOR,"ko_KR",
                HttpStorageSessionController.AuthMode.LEGACY),principal).getBody().getData();
        assertTrue(result.url().startsWith("https://office.example.test/cloud-office/api/10/documents/4-123/report.docx/open?"));
        assertTrue(result.url().contains("user_id=Name%20%26%20"));
        assertTrue(result.url().contains("__tfo_adapter_http_storage_client_metadata="));
        verifyNoInteractions(tickets);
    }

    @Test void clientSessionRouteReplacesOldSessionRoute() throws Exception {
        var controller = mock(HttpStorageSessionController.class);
        var routes = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        assertEquals(400, routes.perform(request(org.springframework.http.HttpMethod.POST,
                "/api/v1/office/client-sessions").contentType("application/json").content("{}"))
                .andReturn().getResponse().getStatus());
        assertEquals(404, routes.perform(request(org.springframework.http.HttpMethod.POST,
                "/api/v1/office/sessions").contentType("application/json").content("{}"))
                .andReturn().getResponse().getStatus());
    }

    @Test void authModeDefaultsToTicketAndRejectsUnknownValues() throws Exception {
        String request = "{\"resourceSeq\":123,\"app\":\"WRITE_EDITOR\"}";
        assertNull(mapper.readValue(request,HttpStorageSessionController.OpenRequest.class).authMode());
        assertEquals(HttpStorageSessionController.AuthMode.LEGACY, mapper.readValue(request.replace("}",",\"authMode\":\"legacy\"}"),HttpStorageSessionController.OpenRequest.class).authMode());
        assertThrows(Exception.class, () -> mapper.readValue(request.replace("}",",\"authMode\":\"unknown\"}"),HttpStorageSessionController.OpenRequest.class));
    }


    @Test void shortBodyAndDuplicateOwnerAreRejected() throws Exception {
        var claims = claim("PUT", URL + "/put", new byte[]{1, 2});
        var request = request(org.springframework.http.HttpMethod.PUT, URI.create(URL + "/put"))
                .content(new byte[]{1}).contentType("application/octet-stream").header("Content-Length", 2)
                .header(HttpStorageRequestVerifier.HEADER, token(claims));
        assertEquals(401, mvc.perform(request).andReturn().getResponse().getStatus());
        assertEquals(400, signed("POST", URL + "/lock", "{\"owner\":\"connection-1\",\"owner\":\"connection-1\"}".getBytes()).getResponse().getStatus());
        verifyNoInteractions(office);
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "tfo.http.storage.classes", matches = ".+")
    void actualTfoAdapterInteroperatesOverHttp() throws Exception {
        // Optional inter-repository contract test. No TFO binary or private dependency is added to Drive.
        var urls = new ArrayList<java.net.URL>();
        urls.add(java.nio.file.Path.of(System.getProperty("tfo.http.storage.classes")).toUri().toURL());
        for (String jar : java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("tfo.http.storage.classpath"))).trim().split(":")) {
            urls.add(java.nio.file.Path.of(jar).toUri().toURL());
        }
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(HttpStorageController.BASE, exchange -> {
            TenantContext.set(10);
            try {
                var call = request(org.springframework.http.HttpMethod.valueOf(exchange.getRequestMethod()), exchange.getRequestURI())
                        .content(exchange.getRequestBody().readAllBytes());
                exchange.getRequestHeaders().forEach((name, values) -> {
                    // MockMvc's builder checks this key case-sensitively when synthesizing body headers.
                    String header = name.equalsIgnoreCase("Content-Length") ? "Content-Length" : name;
                    call.header(header, values.toArray());
                });
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                if (contentType != null) call.contentType(contentType);
                var result = mvc.perform(call).andReturn();
                var response = result.getResponse();
                for (String name : response.getHeaderNames()) exchange.getResponseHeaders().put(name, new ArrayList<>(response.getHeaders(name)));
                byte[] bytes = response.getContentAsByteArray();
                exchange.sendResponseHeaders(response.getStatus(), bytes.length == 0 ? -1 : bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (Exception exception) { throw new java.io.IOException(exception); }
            finally { exchange.close(); TenantContext.clear(); }
        });
        server.start();
        try (var loader = new java.net.URLClassLoader(urls.toArray(java.net.URL[]::new), ClassLoader.getPlatformClassLoader())) {
            Class<?> adapterClass = loader.loadClass("com.tf.se.adapter.impl.httpStorage.HttpStorageAdapter");
            Class<?> paramClass = loader.loadClass("com.tf.se.util.db.AdapterParam");
            Object adapter = adapterClass.getConstructor().newInstance();
            Object param = paramClass.getConstructor().newInstance();
            var putParam = paramClass.getMethod("put", String.class, String.class);
            putParam.invoke(param, "adapter", "10"); putParam.invoke(param, "connId", "connection-1");
            putParam.invoke(param, "docId", "4-123");
            putParam.invoke(param, "__tfo_adapter_http_storage_endpoint_url", "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1");
            putParam.invoke(param, "__tfo_adapter_http_storage_request_jwt_secret", SECRET);
            String browseId = "B".repeat(43);
            remember(browseId, new HttpStorageSession(10, 4, 4, 123L, "10", "documents/4-123/report.docx", com.thinkfree.drive.weboffice.enums.WebOfficeType.WRITE_EDITOR, Instant.now().plusSeconds(3600).getEpochSecond()));
            var folder = mapper.convertValue(Map.of("resourceSeq", 55, "userSeq", 4, "resourceType", "FOLDER", "resourceName", "한글 +"),
                    com.thinkfree.drive.resource.data.ResourceData.class);
            var child = mapper.convertValue(Map.of("resourceSeq", 56, "userSeq", 4, "parentFolderSeq", 55, "resourceType", "FILE",
                    "resourceName", "목록.docx", "sizeByte", 42), com.thinkfree.drive.resource.data.ResourceData.class);
            when(resources.findAll(any())).thenAnswer(call -> ((com.thinkfree.drive.resource.query.ResourceListQuery) call.getArgument(0))
                    .getParentFolderSeq() == null ? List.of(folder) : List.of(child));
            when(resources.findResourceByExists(any())).thenReturn(mapper.convertValue(Map.of("resourceSeq", 55),
                    com.thinkfree.drive.resource.data.ResourceExistsData.class));
            putParam.invoke(param, "__tfo_adapter_http_storage_client_metadata", mapper.writeValueAsString(metadata(10, 4, browseId)));
            Object rootList = adapterClass.getMethod("list", String.class, paramClass).invoke(adapter, "", param);
            assertEquals(1, rootList.getClass().getMethod("getCount").invoke(rootList));
            Object nestedList = adapterClass.getMethod("list", String.class, paramClass).invoke(adapter, "한글 +", param);
            var listed = (List<?>) nestedList.getClass().getMethod("getFiles").invoke(nestedList);
            assertEquals(1, listed.size());
            assertEquals("한글 +/목록.docx", listed.get(0).getClass().getMethod("getFullName").invoke(listed.get(0)));
            putParam.invoke(param, "__tfo_adapter_http_storage_client_metadata", mapper.writeValueAsString(metadata(10, 4, ID)));
            assertNotNull(adapterClass.getMethod("info", String.class, String.class, paramClass).invoke(adapter, PATH, "connection-1", param));
            assertEquals(true, adapterClass.getMethod("lock", String.class, String.class, paramClass).invoke(adapter, PATH, "connection-1", param));
            byte[] document = "round trip from real TFO adapter".getBytes(StandardCharsets.UTF_8);
            assertEquals("4-123", adapterClass.getMethod("put", String.class, java.io.InputStream.class, long.class, paramClass)
                    .invoke(adapter, PATH, new ByteArrayInputStream(document), (long) document.length, param));
            Object download = adapterClass.getMethod("get", String.class, paramClass).invoke(adapter, PATH, param);
            Class<?> streamClass = loader.loadClass("com.tf.se.adapter.impl.TFSIReleaseCallbackWithInputStream");
            try (var input = (java.io.InputStream) streamClass.getMethod("getInputStream").invoke(download)) {
                assertArrayEquals(document, input.readAllBytes());
            } finally { streamClass.getMethod("release").invoke(download); }
            assertEquals(true, adapterClass.getMethod("unlock", String.class, String.class, paramClass).invoke(adapter, PATH, "connection-1", param));
        } finally { server.stop(0); }
    }

    @Test void viewerCannotChangeDocumentLock() throws Exception {
        session(WebOfficeType.WRITE_VIEWER, Instant.now().plusSeconds(3600).getEpochSecond(), PATH, 10);
        byte[] body = mapper.writeValueAsBytes(Map.of("owner", "connection-1"));
        assertEquals(403, signed("POST", URL + "/lock", body).getResponse().getStatus());
        assertEquals(403, signed("POST", URL + "/unlock", body).getResponse().getStatus());
        assertEquals("N", jdbc.queryForObject("SELECT is_locked FROM TB_RESOURCE", String.class));
    }

    @Test void independentSavesRunConcurrentlyWithoutOuterTransaction() throws Exception {
        // Two documents enter the actual save dispatch together, after their short guards have returned.
        jdbc.update("INSERT INTO TB_RESOURCE(user_seq,resource_seq,is_locked) VALUES(4,124,'N')");
        var second = new HttpStorageSession(10, 4, 4, 124L, "10", "documents/4-124/second.docx", WebOfficeType.WRITE_EDITOR, Instant.now().plusSeconds(3600).getEpochSecond());
        var entered = new java.util.concurrent.CountDownLatch(2);
        doAnswer(call -> {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            entered.countDown();
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS), "Both saves must enter without a global one-save gate");
            return null;
        }).when(office).save(any());
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = sessions.get(ID);
            var a = pool.submit(() -> service.put(first, "connection-1", null, new ByteArrayInputStream(new byte[]{1}), 1));
            var b = pool.submit(() -> service.put(second, "connection-2", null, new ByteArrayInputStream(new byte[]{2}), 1));
            assertEquals("4-123", a.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("4-124", b.get(10, java.util.concurrent.TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
}

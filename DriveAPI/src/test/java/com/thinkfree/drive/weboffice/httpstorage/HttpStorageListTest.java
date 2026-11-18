package com.thinkfree.drive.weboffice.httpstorage;

import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.permission.enums.SharePermissionType;
import com.thinkfree.drive.permission.support.RequestActor;
import com.thinkfree.drive.resource.data.ResourceData;
import com.thinkfree.drive.resource.data.ResourceExistsData;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.tenant.enums.TenantStatus;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;
import com.thinkfree.drive.resource.query.ResourceExistsQuery;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HttpStorageListTest {
    static final String SESSION_ID = "B".repeat(43);
    HttpStorageContractTest fixture;
    List<ResourceData> tree;

    @BeforeEach void setup() {
        fixture = new HttpStorageContractTest(); fixture.setup();
        fixture.remember(SESSION_ID, new HttpStorageSession(10, 4, 4, 123L, "10", "documents/4-123/report.docx", com.thinkfree.drive.weboffice.enums.WebOfficeType.WRITE_EDITOR, Instant.now().plusSeconds(3600).getEpochSecond()));
        tree = List.of(row(10, null, "내 문서 +", ResourceType.FOLDER, 0), row(11, 10L, "하위", ResourceType.FOLDER, 0),
                row(12, 11L, "보고서.docx", ResourceType.FILE, 42), row(13, 10L, "빈 폴더", ResourceType.FOLDER, 0),
                row(14, null, "root.docx", ResourceType.FILE, 15));
        when(fixture.resources.findResourceByExists(any())).thenAnswer(call -> {
            ResourceExistsQuery query = call.getArgument(0);
            assertEquals(4, query.getUserSeq()); assertEquals(false, query.getIsDelete());
            assertEquals(ResourceType.FOLDER, query.getResourceType());
            return tree.stream().filter(row -> row.getResourceType() == ResourceType.FOLDER
                            && Objects.equals(row.getParentFolderSeq(), query.getParentFolderSeq())
                            && row.getResourceName().equals(query.getOriginalFileName()))
                    .map(row -> fixture.mapper.convertValue(Map.of("resourceSeq", row.getResourceSeq()), ResourceExistsData.class))
                    .findFirst().orElse(null);
        });
        when(fixture.resources.findAll(any())).thenAnswer(call -> {
            com.thinkfree.drive.resource.query.ResourceListQuery query = call.getArgument(0);
            assertEquals(4, query.getUserSeq()); assertEquals(4, query.getRequestUserSeq());
            assertFalse(query.getIsDelete()); assertEquals(10_001, query.getPage().getPageSize());
            return tree.stream().filter(row -> Objects.equals(row.getParentFolderSeq(), query.getParentFolderSeq())).toList();
        });
    }

    @AfterEach void cleanup() { fixture.clearTenant(); }

    ResourceData row(long id, Long parent, String name, ResourceType type, long size) {
        Map<String, Object> values = new HashMap<>();
        values.put("resourceSeq", id); values.put("userSeq", 4); values.put("parentFolderSeq", parent);
        values.put("resourceName", name); values.put("resourceType", type); values.put("sizeByte", size);
        values.put("isLocked", id == 14);
        return fixture.mapper.convertValue(values, ResourceData.class);
    }

    String url(String path) {
        return HttpStorageController.BASE + "/" + (path.isEmpty() ? "" : UriUtils.encodePath(path, StandardCharsets.UTF_8) + "/") + "list";
    }

    org.springframework.mock.web.MockHttpServletResponse list(String path) throws Exception {
        return listWithMetadata(path, fixture.metadata(10, 4, SESSION_ID));
    }

    org.springframework.mock.web.MockHttpServletResponse listWithMetadata(String path, Map<String, Object> metadata) throws Exception {
        var claims = fixture.claim("GET", url(path), new byte[0]); claims.put("client_metadata", metadata);
        return fixture.mvc.perform(fixture.call("GET", url(path), new byte[0], fixture.token(claims))).andReturn().getResponse();
    }

    @Test void rootAndNestedFoldersReturnOnlyDirectChildrenWithProtocolFraming() throws Exception {
        var root = list("");
        assertEquals(200, root.getStatus()); assertEquals("application/json", root.getContentType());
        assertEquals("no-store", root.getHeader("Cache-Control"));
        assertEquals(root.getContentAsByteArray().length, root.getContentLength());
        var json = fixture.mapper.readTree(root.getContentAsByteArray());
        assertEquals(1, json.size()); assertEquals(2, json.get("entries").size());
        var folder = json.get("entries").get(0);
        assertEquals("내 문서 +", folder.get("path").asText()); assertEquals("directory", folder.get("type").asText());
        assertEquals(0, folder.get("size").asLong()); assertFalse(folder.get("writable").asBoolean());
        assertTrue(json.get("entries").get(1).get("locked").asBoolean());
        var nested = list("내 문서 +/하위");
        assertEquals(200, nested.getStatus());
        var entry = fixture.mapper.readTree(nested.getContentAsByteArray()).get("entries").get(0);
        assertEquals("내 문서 +/하위/보고서.docx", entry.get("path").asText());
        assertEquals("보고서.docx", entry.get("name").asText()); assertEquals(42, entry.get("size").asLong());
        assertTrue(entry.get("readable").asBoolean());
        verify(fixture.permissions).checkPermission(SharePermissionType.VIEW, 12L, 4, RequestActor.user(4), false);
        verifyNoInteractions(fixture.storage, fixture.office);
    }

    @Test void emptyFolderIsAnEmptyArrayAndMissingDeletedOrFilePathsAreNotDirectories() throws Exception {
        var empty = list("내 문서 +/빈 폴더");
        assertEquals(200, empty.getStatus()); assertEquals("{\"entries\":[]}", empty.getContentAsString());
        for (String path : List.of("missing", "deleted", "root.docx", "내 문서 +/하위/보고서.docx")) {
            assertEquals(404, list(path).getStatus());
        }
    }

    @Test void listUsesExistingDocumentSessionAndRequiresSignedMetadata() throws Exception {
        assertEquals(401, fixture.mvc.perform(fixture.call("GET", url(""), new byte[0], null)).andReturn().getResponse().getStatus());
        var probe = listWithMetadata("", null);
        assertEquals(501, probe.getStatus());
        assertEquals("{\"code\":\"LIST_NOT_SUPPORTED\"}", probe.getContentAsString());
        assertEquals(401, listWithMetadata("private-folder", null).getStatus());
        assertEquals(200, listWithMetadata("", fixture.metadata(10, 4, HttpStorageContractTest.ID)).getStatus());
    }

    @ParameterizedTest @ValueSource(strings = {"tenantSeq", "userSeq", "adapter", "path", "app", "proof", "expiresAt", "ownerUserSeq", "resourceSeq"})
    void modifiedAccessGrantIsRejectedBeforeListing(String field) throws Exception {
        var metadata = new HashMap<>(fixture.metadata(10, 4, SESSION_ID));
        metadata.put(field, metadata.get(field) instanceof Number ? 99 : "changed");
        assertEquals(401, listWithMetadata("", metadata).getStatus());
        verify(fixture.resources, never()).findAll(any());
    }

    @ParameterizedTest @ValueSource(strings = {"tenantInactive", "userInactive", "userTenant", "anonymous", "permission"})
    void currentAccountStateAndFolderPermissionsAreRechecked(String kind) throws Exception {
        assertEquals(200, list("내 문서 +").getStatus());
        var user = fixture.users.findDetailWithRoles(4);
        var tenant = fixture.tenants.findByTenantSeq(10);
        switch (kind) {
            case "tenantInactive" -> when(tenant.getStatus()).thenReturn(TenantStatus.INACTIVE);
            case "userInactive" -> when(user.getStatus()).thenReturn(UserStatus.INACTIVE);
            case "userTenant" -> when(user.getTenantSeq()).thenReturn(11);
            case "anonymous" -> when(user.getRoles()).thenReturn(List.of(RoleType.ANONYMOUS));
            case "permission" -> doThrow(HttpStorageException.forbidden()).when(fixture.permissions)
                    .checkPermission(SharePermissionType.VIEW, 10L, 4, RequestActor.user(4), false);
        }
        clearInvocations(fixture.resources);
        assertEquals(403, list("내 문서 +").getStatus());
        verify(fixture.resources, never()).findAll(any());
    }

    @Test void listingDoesNotRequireStoredSessionOrReplayRecord() throws Exception {
        var claims = fixture.claim("GET", url(""), new byte[0]);
        claims.put("client_metadata", fixture.metadata(10, 4, SESSION_ID));
        String token = fixture.token(claims);
        fixture.sessions.clear(); fixture.proofs.clear();
        assertEquals(200, fixture.mvc.perform(fixture.call("GET", url(""), new byte[0], token)).andReturn().getResponse().getStatus());
        assertEquals(200, fixture.mvc.perform(fixture.call("GET", url(""), new byte[0], token)).andReturn().getResponse().getStatus());
    }

    @Test void anotherUserOrIndirectChildFromTheRepositoryIsNeverExposed() throws Exception {
        var foreign = fixture.mapper.convertValue(Map.of("userSeq", 7, "resourceSeq", 99, "resourceName", "secret.docx",
                "resourceType", "FILE", "sizeByte", 4), ResourceData.class);
        doReturn(List.of(foreign)).when(fixture.resources).findAll(any());
        assertEquals(403, list("").getStatus());
        doReturn(List.of(row(12, 11L, "nested.docx", ResourceType.FILE, 1))).when(fixture.resources).findAll(any());
        assertEquals(403, list("").getStatus());
    }

    @Test void protocolLimitsRejectOverflowWithoutTruncationAndOmitOversizedDocuments() throws Exception {
        doReturn(Collections.nCopies(10_001, tree.get(0))).when(fixture.resources).findAll(any());
        assertEquals(413, list("").getStatus());
        doReturn(List.of(row(22, null, "huge.docx", ResourceType.FILE, HttpStorageBody.MAX_FILE_BYTES + 1), tree.get(4))).when(fixture.resources).findAll(any());
        var response = list(""); assertEquals(200, response.getStatus());
        assertEquals(1, fixture.mapper.readTree(response.getContentAsByteArray()).get("entries").size());
        doReturn(List.of(tree.get(4), tree.get(4))).when(fixture.resources).findAll(any());
        assertEquals(409, list("").getStatus());
        List<ResourceData> large = new ArrayList<>();
        for (int i = 0; i < 4000; i++) large.add(row(1000 + i, null, "가".repeat(240) + i, ResourceType.FILE, 1));
        doReturn(large).when(fixture.resources).findAll(any());
        assertEquals(413, list("").getStatus());
    }

    @ParameterizedTest @ValueSource(strings = {"../other", "%2e%2e/other", "folder%2Fother", "folder%5Cother", "folder//other"})
    void listRejectsPathTraversalBeforeResourceQueries(String rawPath) throws Exception {
        String url = HttpStorageController.BASE + "/" + rawPath + "/list";
        var claims = fixture.claim("GET", url, new byte[0]); claims.put("client_metadata", fixture.metadata(10, 4, SESSION_ID));
        assertEquals(400, fixture.mvc.perform(fixture.call("GET", url, new byte[0], fixture.token(claims))).andReturn().getResponse().getStatus());
        verify(fixture.resources, never()).findAll(any());
        verify(fixture.resources, never()).findResourceByExists(any());
    }

    @Test void obsoleteBrowseEndpointIsNotMapped() {
        for (var method : HttpStorageSessionController.class.getDeclaredMethods()) {
            var mapping = method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class);
            if (mapping != null) assertFalse(Arrays.asList(mapping.value()).contains("/api/v1/office/browse-sessions"));
        }
    }
}

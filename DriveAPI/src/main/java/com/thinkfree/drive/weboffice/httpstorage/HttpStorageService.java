package com.thinkfree.drive.weboffice.httpstorage;

import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.common.file.model.FileData;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.permission.enums.SharePermissionType;
import com.thinkfree.drive.permission.service.ResourcePermissionService;
import com.thinkfree.drive.permission.support.RequestActor;
import com.thinkfree.drive.resource.data.ResourceDetailData;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.query.ResourceFindQuery;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.tenant.enums.TenantStatus;
import com.thinkfree.drive.tenant.service.TenantFindService;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.enums.UserStatus;
import com.thinkfree.drive.user.service.UserFindService;
import com.thinkfree.drive.weboffice.dto.WebOfficeSaveDto;
import com.thinkfree.drive.weboffice.enums.WebOfficeMode;
import com.thinkfree.drive.weboffice.enums.WebOfficeSaveType;
import com.thinkfree.drive.weboffice.service.WebOfficeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.resource.enums.ResourceSortField;
import com.thinkfree.drive.resource.query.ResourceExistsQuery;
import com.thinkfree.drive.resource.query.ResourceListQuery;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HttpStorageService {
    private static final int MAX_ENTRIES = 10_000;
    private final ResourceFindService resources;
    private final UserFindService users;
    private final TenantFindService tenants;
    private final ResourcePermissionService permissions;
    private final StorageService storage;
    private final WebOfficeService office;
    private final ResourceAuditService audit;
    private final HttpStorageLocks locks;

    public HttpStorageSession authorize(HttpStorageRequestVerifier.VerifiedRequest request, String path, boolean write) {
        var metadata = request.metadata();
        if (metadata == null) throw HttpStorageException.unauthorized();
        HttpStorageSession session = metadata.session();
        // The verifier has checked Drive's access proof; TFO signing arbitrary browser metadata is insufficient.
        if (!session.adapter().equals(request.adapter())
                || !session.path().equals(path)) throw HttpStorageException.forbidden();
        // The upstream tenant filter resolves the tenant from its configured host or path strategy.
        if (!session.tenantSeq().equals(TenantContext.get())) throw HttpStorageException.forbidden();
        requirePermission(session, write);
        return session;
    }

    public UserDetailData requirePermission(HttpStorageSession session, boolean write) {
        if (write && !session.app().isEditer()) throw HttpStorageException.forbidden();
        var tenant = tenants.findByTenantSeq(session.tenantSeq());
        if (tenant == null || tenant.getStatus() != TenantStatus.ACTIVE) throw HttpStorageException.forbidden();
        var user = users.findDetailWithRoles(session.userSeq());
        if (user.getStatus() != UserStatus.ACTIVE) throw HttpStorageException.forbidden();
        boolean anonymous = user.getRoles().contains(RoleType.ANONYMOUS);
        if (!anonymous && !session.tenantSeq().equals(user.getTenantSeq())) throw HttpStorageException.forbidden();
        UserDetailData owner = users.findDetailByUserSeq(session.ownerUserSeq());
        if (!session.tenantSeq().equals(owner.getTenantSeq()) || owner.getStatus() != UserStatus.ACTIVE) {
            throw HttpStorageException.forbidden();
        }
        permissions.checkPermission(write ? SharePermissionType.EDIT : SharePermissionType.VIEW,
                session.resourceSeq(), session.ownerUserSeq(), RequestActor.of(user.getUserSeq(), anonymous), false);
        return user;
    }

    public ResourceDetailData resource(HttpStorageSession session) {
        ResourceDetailData resource = resources.findDetailOrNull(ResourceFindQuery.builder()
                .resourceSeq(session.resourceSeq()).userSeq(session.ownerUserSeq()).resourceType(ResourceType.FILE).isDelete(false).build());
        if (resource == null) throw new HttpStorageException(HttpStatus.NOT_FOUND);
        if (resource.getSizeByte() > HttpStorageBody.MAX_FILE_BYTES) throw new HttpStorageException(HttpStatus.PAYLOAD_TOO_LARGE);
        return resource;
    }

    public Map<String, Object> info(HttpStorageSession session) {
        ResourceDetailData resource = resource(session);
        boolean writable = session.app().isEditer();
        if (writable) requirePermission(session, true);
        var lock = locks.current(session);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", session.path());
        // Keep the opened path stable if the file was renamed while this Office session was active.
        result.put("name", session.path().substring(session.path().lastIndexOf('/') + 1));
        result.put("type", "file");
        result.put("size", resource.getSizeByte());
        result.put("readable", true);
        result.put("writable", writable);
        result.put("locked", lock.locked());
        if (lock.owner() != null) result.put("locker", lock.owner());
        result.put("revision", String.valueOf(resource.getCurrentVersionNumber()));
        return result;
    }

    @AuditLog
    public FileData get(HttpStorageSession session) {
        ResourceDetailData resource = resource(session);
        UserDetailData actor = requirePermission(session, false);
        FileData file = storage.getFileData(resource.getResourceSaveName());
        if (file.contentLength() < 0 || file.contentLength() > HttpStorageBody.MAX_FILE_BYTES) {
            try { file.content().close(); } catch (java.io.IOException ignored) { }
            throw new HttpStorageException(HttpStatus.PAYLOAD_TOO_LARGE);
        }
        try {
            audit.record(session.app().getMode() == WebOfficeMode.VIEWER ? EventName.FILE_PREVIEW : EventName.FILE_OPEN,
                    resource, session.ownerUserSeq(), actor);
        } catch (RuntimeException exception) {
            try { file.content().close(); } catch (java.io.IOException closeFailure) { exception.addSuppressed(closeFailure); }
            throw exception;
        }
        return file;
    }

    @AuditLog
    public String put(HttpStorageSession session, String connection, String saveType, InputStream input, long length) {
        UserDetailData actor = requirePermission(session, true);
        resource(session);
        WebOfficeSaveType type = WebOfficeSaveType.findByAdapterParam(saveType);
        if (saveType != null && type == null) throw HttpStorageException.badRequest();
        if (type == WebOfficeSaveType.SAVE_AS) throw HttpStorageException.forbidden();
        // Every successful protocol PUT persists bytes. Legacy autosave skips persistence intentionally;
        // map that case to the regular save path, retaining its version/quota/audit handling.
        if (type == null || !type.shouldSaveWithVersioning()) type = WebOfficeSaveType.SAVE;
        office.save(WebOfficeSaveDto.builder().webOfficeType(session.app()).loginUserSeq(session.userSeq())
                .loginUserName(actor.getUserName()).userSeq(session.ownerUserSeq()).resourceSeq(session.resourceSeq())
                .webOfficeSaveType(type).size(length).docId(session.docId()).inputStream(input).actor(actor).build());
        return session.docId();
    }

    public void lock(HttpStorageSession session, String owner) { requirePermission(session, true); resource(session); locks.lock(session, owner); }
    public void unlock(HttpStorageSession session, String owner) { requirePermission(session, true); resource(session); locks.unlock(session, owner); }
    private void requireListingUser(int tenantSeq, int userSeq) {
        var tenant = tenants.findByTenantSeq(tenantSeq);
        if (tenant == null || tenant.getStatus() != TenantStatus.ACTIVE) throw HttpStorageException.forbidden();
        var user = users.findDetailWithRoles(userSeq);
        if (user.getStatus() != UserStatus.ACTIVE || user.getRoles().contains(RoleType.ANONYMOUS)
                || !Integer.valueOf(tenantSeq).equals(user.getTenantSeq())) throw HttpStorageException.forbidden();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(HttpStorageRequestVerifier.VerifiedRequest request, String path) {
        var metadata = request.metadata();
        if (metadata == null) throw HttpStorageException.unauthorized();
        var session = metadata.session();
        if (!session.adapter().equals(request.adapter()) || !session.tenantSeq().equals(TenantContext.get())) {
            throw HttpStorageException.forbidden();
        }
        requireListingUser(session.tenantSeq(), session.userSeq());
        int userSeq = session.userSeq();
        var actor = RequestActor.user(userSeq);
        Long parent = null;
        if (!path.isEmpty()) {
            String[] segments = path.split("/", -1);
            if (segments.length > 64) throw HttpStorageException.badRequest();
            for (String name : segments) {
                requireName(name);
                // Resolve each live ancestor within the authenticated user's partition, including the root.
                var folder = resources.findResourceByExists(ResourceExistsQuery.builder().userSeq(userSeq)
                        .parentFolderSeq(parent).originalFileName(name).resourceType(ResourceType.FOLDER).isDelete(false).build());
                if (folder == null) throw new HttpStorageException(HttpStatus.NOT_FOUND);
                parent = folder.getResourceSeq();
                permissions.checkPermission(SharePermissionType.VIEW, parent, userSeq, actor, false);
            }
        }
        // Protocol v1 has no pagination. Fetch one extra row to detect overflow without silently truncating.
        var children = resources.findAll(ResourceListQuery.builder().userSeq(userSeq).requestUserSeq(userSeq)
                .parentFolderSeq(parent).isDelete(false).sortBy(ResourceSortField.NAME).sortOrder(SortOrder.ASC)
                .page(CursorPaginationQuery.of(null, null, null, MAX_ENTRIES + 1)).build());
        if (children.size() > MAX_ENTRIES) throw new HttpStorageException(HttpStatus.PAYLOAD_TOO_LARGE);
        List<Map<String, Object>> entries = new ArrayList<>();
        var paths = new HashSet<String>();
        for (var child : children) {
            if (!session.userSeq().equals(child.getUserSeq()) || !Objects.equals(parent, child.getParentFolderSeq())) {
                throw HttpStorageException.forbidden();
            }
            boolean folder = child.getResourceType() == ResourceType.FOLDER;
            if (!folder && child.getResourceType() != ResourceType.FILE) continue;
            long size = folder ? 0 : child.getSizeByte();
            // TFO cannot accept a LIST entry larger than its document limit.
            if (size > HttpStorageBody.MAX_FILE_BYTES) continue;
            if (size < 0) throw new HttpStorageException(HttpStatus.CONFLICT);
            permissions.checkPermission(SharePermissionType.VIEW, child.getResourceSeq(), userSeq, actor, false);
            String name = child.getResourceName();
            requireName(name);
            String fullPath = path.isEmpty() ? name : path + "/" + name;
            if (fullPath.length() > 4096 || !paths.add(fullPath)) throw new HttpStorageException(HttpStatus.CONFLICT);
            entries.add(Map.of("path", fullPath, "name", name, "type", folder ? "directory" : "file", "size", size,
                    "readable", true, "writable", false, "locked", Boolean.TRUE.equals(child.getIsLocked())));
        }
        return Map.of("entries", entries);
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank() || name.length() > 255 || name.equals(".") || name.equals("..")
                || name.contains("/") || name.contains("\\") || name.chars().anyMatch(Character::isISOControl)) {
            throw HttpStorageException.badRequest();
        }
    }
}

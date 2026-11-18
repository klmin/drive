package com.thinkfree.drive.auditlog.service.impl;

import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.auditlog.support.ResourceAuditSupport;
import com.thinkfree.drive.objectmapper.util.ObjectMapperUtil;
import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.permission.model.ResourceShareFarthest;
import com.thinkfree.drive.permission.service.ResourcePermissionFindService;
import com.thinkfree.drive.resource.converter.ResourceConverter;
import com.thinkfree.drive.resource.data.ResourceHierarchyData;
import com.thinkfree.drive.resource.data.ResourceDetailData;
import com.thinkfree.drive.resource.data.ResourceIdentity;
import com.thinkfree.drive.resource.enums.ResourceLocation;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.user.data.UserIdentity;
import com.thinkfree.drive.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultResourceAuditService implements ResourceAuditService {

    private static final String RESOURCE_NAME = "resourceName";
    private static final String PATH = "path";

    private final ResourceFindService resourceFindService;
    private final ResourceConverter resourceConverter;
    private final ResourcePermissionFindService resourcePermissionFindService;
    private final ObjectMapperUtil objectMapperUtil;

    @Override
    public ResourceDetailData findResource(Long resourceSeq, Integer ownerSeq) {
        var query = resourceConverter.toFindQuery(resourceSeq, ownerSeq, null, false);
        return resourceFindService.findDetail(query);
    }

    @Override
    public void record(EventName eventName, ResourceIdentity resource, Integer ownerSeq) {
        ResourceAuditSupport.record(eventName, resource,
                findDisplayPath(resource.getResourceSeq(), resource.getParentFolderSeq(), ownerSeq));
    }

    @Override
    public void record(EventName eventName, ResourceIdentity resource, Integer ownerSeq, UserIdentity actor) {
        ResourceAuditSupport.record(eventName, resource,
                findDisplayPath(resource.getResourceSeq(), resource.getParentFolderSeq(), ownerSeq, actor.getUserSeq()),
                actor);
    }

    @Override
    public void record(EventName eventName, Long resourceSeq, Integer ownerSeq) {
        var resource = findResourceOrNull(resourceSeq, ownerSeq);

        // 리소스를 못 찾아도 이벤트가 일어난 사실은 남긴다.(이벤트 성공 + 감사로그 기록 실패한 경우)
        // 경로를 못 구했으므로 위치도 붙이지 않는다(경로 null).
        if (resource == null) {
            log.warn("[AuditLog] 감사 대상 리소스 조회 실패 - objectSeq만 기록한다. resourceSeq={}, ownerSeq={}", resourceSeq, ownerSeq);
            ResourceAuditSupport.record(eventName, resourceSeq, null, null);
            return;
        }

        record(eventName, resource, ownerSeq);
    }

    @Override
    public void record(EventName eventName, ResourceHierarchyData item, Integer ownerSeq) {
        ResourceAuditSupport.record(eventName, item,
                findDisplayPath(item.getResourceSeq(), item.getParentFolderSeq(), ownerSeq));
    }

    @Override
    public void recordAll(List<ResourceHierarchyData> items, Integer ownerSeq,
                          Function<ResourceHierarchyData, EventName> eventNameResolver) {

        if (items.isEmpty()) {
            return;
        }

        var paths = findResourcePaths(items, ownerSeq);
        var location = resolveLocation(ownerSeq);
        items.forEach(item -> ResourceAuditSupport.record(eventNameResolver.apply(item), item,
                composePath(location, paths.get(item.getResourceSeq()))));
    }

    @Override
    public void recordRename(EventName eventName, ResourceIdentity resource, Integer ownerSeq, String beforeName) {
        recordRename(eventName, resource.getResourceSeq(), resource.getParentFolderSeq(),
                resource.getResourceName(), ownerSeq, beforeName);
    }

    @Override
    public void recordRename(EventName eventName, Long resourceSeq, Long parentFolderSeq, String objectName,
                             Integer ownerSeq, String beforeName) {
        ResourceAuditSupport.recordChange(eventName, resourceSeq, objectName,
                findDisplayPath(resourceSeq, parentFolderSeq, ownerSeq),
                toJson(RESOURCE_NAME, beforeName), toJson(RESOURCE_NAME, objectName));
    }

    @Override
    public void recordMove(ResourceIdentity resource, String oldParentPath, String oldResourceName, Long newParentFolderSeq,
                           Integer sourceOwnerSeq, Integer targetOwnerSeq) {

        var newParentOwnerSeq = targetOwnerSeq == null ? sourceOwnerSeq : targetOwnerSeq;
        var newParentPath = findResourcePath(resource.getResourceSeq(), newParentFolderSeq, newParentOwnerSeq);

        var oldLocation = resolveLocation(sourceOwnerSeq);
        var newLocation = resolveLocation(newParentOwnerSeq);

        // FILE은 자기 fullPath가 항상 null이라, 위치 루트(MY/SHARE) + 부모 폴더 경로 + 파일명으로 절대 경로를 조립한다.
        // 이동 위치에 같은 이름이 있으면 구분 번호가 붙으므로 before는 이동 전 이름을 써야 한다.
        var oldFilePath = composeFilePath(oldLocation, oldParentPath, oldResourceName);
        var newFilePath = composeFilePath(newLocation, newParentPath, resource.getResourceName());

        ResourceAuditSupport.recordChange(EventName.FILE_MOVE, resource, composePath(newLocation, newParentPath),
                toJson(PATH, oldFilePath), toJson(PATH, newFilePath));
    }

    // 영구 삭제는 경로가 없어(리소스 이미 삭제) 조회 없이 Support로 위임한다. 호출부가 Support를 직접 부르지 않도록 서비스가 감싼다.
    @Override
    public void recordPermanentDelete(EventName eventName, Long resourceSeq, String resourceName) {
        ResourceAuditSupport.recordDelete(eventName, resourceSeq, resourceName);
    }

    @Override
    public void recordPermanentDelete(EventName eventName, Long resourceSeq, String resourceName, Integer tenantSeq, UserIdentity actor) {
        ResourceAuditSupport.recordDelete(eventName, resourceSeq, resourceName, tenantSeq, actor);
    }

    /** 직렬화에 실패해도 값 없이 기록한다 - 감사 기록 실패가 업무 동작을 되돌려선 안 된다. */
    private String toJson(String key, Object value) {
        try {
            var map = new LinkedHashMap<String, Object>();
            map.put(key, value);
            return objectMapperUtil.writeValueAsString(map);
        } catch (RuntimeException e) {
            log.warn("[AuditLog] before/after 직렬화 실패 - 값 없이 기록한다. key={}", key, e);
            return null;
        }
    }

    @Override
    public String findDisplayPath(Long resourceSeq, Long parentFolderSeq, Integer ownerSeq) {
        return findDisplayPath(resourceSeq, parentFolderSeq, ownerSeq, SecurityUtil.getUserSeq());
    }

    /** 행위자를 명시로 받는다 - 시큐리티 컨텍스트가 없는 웹오피스 경로용. */
    private String findDisplayPath(Long resourceSeq, Long parentFolderSeq, Integer ownerSeq, Integer actorUserSeq) {
        return composePath(resolveLocation(ownerSeq, actorUserSeq),
                findResourcePath(resourceSeq, parentFolderSeq, ownerSeq, actorUserSeq));
    }

    /** 리소스 자신이 아니라 부모 폴더까지의 경로다 (FILE은 자기 fullPath가 null, FOLDER는 자기 이름을 포함). */
    @Override
    public String findResourcePath(Long resourceSeq, Long parentFolderSeq, Integer ownerSeq) {
        return findResourcePath(resourceSeq, parentFolderSeq, ownerSeq, SecurityUtil.getUserSeq());
    }

    private String findResourcePath(Long resourceSeq, Long parentFolderSeq, Integer ownerSeq, Integer actorUserSeq) {

        // 드라이브 루트 - 표시할 경로 없음
        if (parentFolderSeq == null || ownerSeq == null) {
            return null;
        }

        try {
            return isOwnerScope(actorUserSeq, ownerSeq)
                    ? findOwnerPath(parentFolderSeq, ownerSeq)
                    : findActorPath(resourceSeq, ownerSeq, actorUserSeq);
        } catch (RuntimeException e) {
            log.warn("[AuditLog] resourcePath 조회 실패 - 경로 없이 기록한다. resourceSeq={}, ownerSeq={}",
                    resourceSeq, ownerSeq, e);
            return null;
        }
    }

    /** 감사 대상 리소스를 소유자 스코프로 조회한다. 없으면 null. */
    private ResourceDetailData findResourceOrNull(Long resourceSeq, Integer ownerSeq) {
        try {
            var query = resourceConverter.toFindQuery(resourceSeq, ownerSeq, null, false);
            return resourceFindService.findDetailOrNull(query);
        } catch (RuntimeException e) {
            log.warn("[AuditLog] 감사 대상 리소스 조회 실패. resourceSeq={}, ownerSeq={}", resourceSeq, ownerSeq, e);
            return null;
        }
    }

    private Map<Long, String> findResourcePaths(List<ResourceHierarchyData> items, Integer ownerSeq) {

        Map<Long, String> paths = new HashMap<>();
        Integer actorUserSeq = SecurityUtil.getUserSeq();

        if (ownerSeq == null || isOwnerScope(actorUserSeq, ownerSeq)) {
            items.forEach(item -> paths.put(item.getResourceSeq(),
                    findResourcePath(item.getResourceSeq(), item.getParentFolderSeq(), ownerSeq)));
            return paths;
        }

        var farthestMap = findActorFarthestMap(actorUserSeq, ownerSeq,
                items.stream().map(ResourceHierarchyData::getResourceSeq).toList());

        items.forEach(item -> {
            var farthest = farthestMap.get(new PartitionKey(ownerSeq, item.getResourceSeq()));
            paths.put(item.getResourceSeq(), farthest == null ? null : farthest.getFullPath());
        });

        return paths;
    }

    /** 요청 컨텍스트가 없으면(스케줄러 등 SYSTEM) 소유자 기준으로 본다. */
    private boolean isOwnerScope(Integer actorUserSeq, Integer ownerSeq) {
        return actorUserSeq == null || Objects.equals(actorUserSeq, ownerSeq);
    }

    /**
     * 리소스 위치(내 드라이브/공유 문서함) 판정. 행위자가 소유자면 {@link ResourceLocation#MY}, 아니면 {@link ResourceLocation#SHARE}.
     * 요청자 없음(스케줄러 등 SYSTEM)이나 소유자 불명이면 관점이 없어 null.
     */
    private ResourceLocation resolveLocation(Integer ownerSeq) {
        return resolveLocation(ownerSeq, SecurityUtil.getUserSeq());
    }

    private ResourceLocation resolveLocation(Integer ownerSeq, Integer actorUserSeq) {
        if (actorUserSeq == null || ownerSeq == null) {
            return null;
        }
        return Objects.equals(actorUserSeq, ownerSeq) ? ResourceLocation.MY : ResourceLocation.SHARE;
    }

    /**
     * object.path를 완성한다: 위치 루트(MY/SHARE)를 부모 폴더 경로 앞에 붙이고 trailing slash를 제거한다.
     * 예: (MY, "영업팀/2026/") → "MY/영업팀/2026", 루트 항목(MY, null) → "MY". 위치 불명(null)이면 경로만.
     */
    private String composePath(ResourceLocation location, String folderPath) {
        String path = stripTrailingSlash(folderPath);
        boolean hasPath = path != null && !path.isEmpty();
        if (location == null) {
            return hasPath ? path : null;
        }
        return hasPath ? location.name() + "/" + path : location.name();
    }

    /**
     * 이동 before/after용 절대 파일 경로: 위치 루트 + 부모 폴더 경로 + 파일명.
     * 예: (MY, "2026/제안서/", "제안서.pptx") → "MY/2026/제안서/제안서.pptx", 루트(MY, null, "제안서.pptx") → "MY/제안서.pptx".
     */
    private String composeFilePath(ResourceLocation location, String parentPath, String fileName) {
        String parent = stripTrailingSlash(parentPath);
        StringBuilder sb = new StringBuilder();
        if (location != null) {
            sb.append(location.name()).append('/');
        }
        if (parent != null && !parent.isEmpty()) {
            sb.append(parent).append('/');
        }
        return sb.append(fileName).toString();
    }

    private static String stripTrailingSlash(String path) {
        if (path == null || !path.endsWith("/")) {
            return path;
        }
        return path.substring(0, path.length() - 1);
    }

    /**
     * 소유자에게는 절대경로 전체가 정답이라 공유 체인 재귀 CTE 없이 부모 폴더 단건 조회로 끝낸다.
     *
     * <p>isDelete는 null(스코프 무시)로 넘긴다. 휴지통 리소스도 다운로드가 허용되어
     * 부모 폴더가 삭제 상태일 수 있고, 경로는 삭제 여부와 무관하게 남아야 한다.
     */
    private String findOwnerPath(Long parentFolderSeq, Integer ownerSeq) {
        var query = resourceConverter.toFindQuery(parentFolderSeq, ownerSeq, ResourceType.FOLDER, null);
        var parentFolder = resourceFindService.findDetailOrNull(query);
        return parentFolder == null ? null : parentFolder.getFullPath();
    }

    /**
     * 목록/상세 조회와 동일한 규칙으로 조회한다. 공유 체인 쿼리의 FULL_PATH가 부모 폴더 조인 값이라
     * 리소스 자신의 seq만 넘기면 된다.
     *
     * <p>isDelete는 null(스코프 무시)로 넘긴다. 기록 시점의 삭제 상태가 이벤트마다 다르다
     * (DELETE는 삭제 전, RESTORE는 복원 후, 다운로드는 휴지통 리소스 허용).
     */
    private String findActorPath(Long resourceSeq, Integer ownerSeq, Integer actorUserSeq) {
        var farthest = resourcePermissionFindService.findResourceShareFarthestOrNull(
                actorUserSeq, new PartitionKey(ownerSeq, resourceSeq), null);
        return farthest == null ? null : farthest.getFullPath();
    }

    /** 다중 다운로드 fan-out용 일괄 조회. 실패해도 경로 없이 기록한다. */
    private Map<PartitionKey, ResourceShareFarthest> findActorFarthestMap(Integer actorUserSeq, Integer ownerSeq,
                                                                         List<Long> resourceSeqList) {
        var keys = resourceSeqList.stream().map(seq -> new PartitionKey(ownerSeq, seq)).toList();

        try {
            return resourcePermissionFindService.findResourcesShareFarthestMap(actorUserSeq, keys, null);
        } catch (RuntimeException e) {
            log.warn("[AuditLog] resourcePath 일괄 조회 실패 - 경로 없이 기록한다. actorUserSeq={}, ownerSeq={}",
                    actorUserSeq, ownerSeq, e);
            return Map.of();
        }
    }
}

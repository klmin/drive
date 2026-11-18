package com.thinkfree.drive.auditlog.support;

import com.thinkfree.drive.auditlog.context.AuditLogContext;
import com.thinkfree.drive.auditlog.context.AuditLogContextHolder;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.application.share.validation.ShareTargetValidationItem;
import com.thinkfree.drive.objectmapper.util.ObjectMapperUtil;
import com.thinkfree.drive.share.data.ShareWithTargetsData;
import com.thinkfree.drive.share.target.dto.ShareTargetUpsertDto;
import com.thinkfree.drive.user.data.UserData;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// SHARE_CREATE/SHARE_REMOVE fan-out에서 대상자 1명에 대한 row를 만드는 부분만 분리한 것.
// 대상자 목록 순회와 targetUser 조회(userDataList 기반)는 ShareUseCase 전용 데이터라 여기서 다루지 않고
// ShareUseCase.recordShareAudit이 루프 안에서 대상자 1명씩 이 메서드를 호출한다.
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ShareAuditSupport {

    /** 대상자가 행위자와 다른 테넌트인지. 외부일 때만 target 테넌트 정보를 남긴다. */
    public static boolean isExternalTenant(UserData targetUser, Integer actorTenantSeq) {
        return targetUser != null && !Objects.equals(targetUser.getTenantSeq(), actorTenantSeq);
    }

    // SHARE_CREATE 시 링크 자체(공유 범위 + 기본 권한)에 대한 row - 특정 대상자와 무관하게 항상 1건 기록한다.
    public static void recordLinkCreated(Long resourceSeq, String resourceName, String resourcePath, String afterValue) {
        AuditLogContextHolder.add(AuditLogContext.builder()
                .eventName(EventName.SHARE_CREATE)
                .objectSeq(resourceSeq)
                .objectName(resourceName)
                .resourcePath(resourcePath)
                .afterValue(afterValue)
                .build());
    }

    // SHARE_REMOVE(해제) 시 링크 자체(공유 범위 + 기본 권한)에 대한 row - 특정 대상자와 무관하게 항상 1건 기록한다.
    // recordLinkCreated의 대칭 - beforeValue에 해제 전 스냅샷을 담는다(해제 후엔 조회 불가라 미리 캡처해둔 값 사용).
    public static void recordLinkRemoved(Long resourceSeq, String resourceName, String resourcePath, String beforeValue) {
        AuditLogContextHolder.add(AuditLogContext.builder()
                .eventName(EventName.SHARE_REMOVE)
                .objectSeq(resourceSeq)
                .objectName(resourceName)
                .resourcePath(resourcePath)
                .beforeValue(beforeValue)
                .build());
    }

    // beforeValue: SHARE_REMOVE에서 대상자가 해제 전 갖고 있던 권한 스냅샷(JSON, SHARE_CREATE는 null).
    // afterValue: SHARE_CREATE에서 대상자에게 부여된 권한 스냅샷(JSON, SHARE_REMOVE는 null - 해제 후엔 권한이 없으므로).
    public static void record(EventName eventName, Long resourceSeq, String resourceName, String resourcePath,
                               UserData targetUser, Integer actorTenantSeq, String beforeValue, String afterValue) {
        boolean isExternalTenant = isExternalTenant(targetUser, actorTenantSeq);
        AuditLogContextHolder.add(AuditLogContext.builder()
                .eventName(eventName)
                .objectSeq(resourceSeq)
                .objectName(resourceName)
                .resourcePath(resourcePath)
                .targetTenantSeq(isExternalTenant ? targetUser.getTenantSeq() : null)
                .targetTenantName(isExternalTenant ? targetUser.getTenantName() : null)
                .targetUserSeq(targetUser.getUserSeq())
                .targetUserId(targetUser.getUserId())
                .targetUserName(targetUser.getUserName())
                .beforeValue(beforeValue)
                .afterValue(afterValue)
                .build());
    }

    // SHARE_PERMISSION_CHANGE - 공유 범위(shareType)/링크 기본 권한 변경 (특정 대상자 없음)
    public static void recordPermissionChange(Long resourceSeq, String resourceName, String resourcePath,
                                               String beforeValue, String afterValue) {
        AuditLogContextHolder.add(AuditLogContext.builder()
                .eventName(EventName.SHARE_PERMISSION_CHANGE)
                .objectSeq(resourceSeq)
                .objectName(resourceName)
                .resourcePath(resourcePath)
                .beforeValue(beforeValue)
                .afterValue(afterValue)
                .build());
    }

    // SHARE_PERMISSION_CHANGE - 유지되는 대상자 1명의 권한 변경.
    // 특정 대상자가 있는 건이라 SHARE_CREATE/REMOVE와 같이 외부 테넌트면 테넌트 정보도 남긴다.
    public static void recordTargetPermissionChange(Long resourceSeq, String resourceName, String resourcePath,
                                                      Integer targetUserSeq, String targetUserId, String targetUserName,
                                                      Integer targetTenantSeq, String targetTenantName,
                                                      String beforeValue, String afterValue) {
        AuditLogContextHolder.add(AuditLogContext.builder()
                .eventName(EventName.SHARE_PERMISSION_CHANGE)
                .objectSeq(resourceSeq)
                .objectName(resourceName)
                .resourcePath(resourcePath)
                .targetTenantSeq(targetTenantSeq)
                .targetTenantName(targetTenantName)
                .targetUserSeq(targetUserSeq)
                .targetUserId(targetUserId)
                .targetUserName(targetUserName)
                .beforeValue(beforeValue)
                .afterValue(afterValue)
                .build());
    }

    /**
     * 권한 변경 1건을 기록한다. 대상자의 테넌트는 새 대상 목록에서 찾고, 외부 테넌트일 때만 테넌트 정보를 남긴다.
     */
    public static void recordTargetPermissionChange(Long resourceSeq, String resourceName, String resourcePath,
                                                     TargetPermissionChange change, List<UserData> userDataList,
                                                     Integer actorTenantSeq) {
        var changedUser = findUserData(userDataList, change.userSeq());
        boolean isExternalTenant = isExternalTenant(changedUser, actorTenantSeq);
        recordTargetPermissionChange(resourceSeq, resourceName, resourcePath,
                change.userSeq(), change.userId(), change.userName(),
                isExternalTenant ? changedUser.getTenantSeq() : null,
                isExternalTenant ? changedUser.getTenantName() : null,
                change.beforeJson(), change.afterJson());
    }

    /** 유지되는 대상자 1명의 권한 변경(변경 전/후 스냅샷 JSON). */
    public record TargetPermissionChange(Integer userSeq, String userId, String userName, String beforeJson, String afterJson) {}

    /** 공유 범위(shareType)/링크 기본 권한 변경 전후 스냅샷 JSON. 변경이 없으면 이 값 자체를 만들지 않는다({@code null}로 표현). */
    public record ScopeChange(String beforeJson, String afterJson) {}

    /**
     * 대상자 집합만큼 감사 로그 row를 fan-out한다(SHARE_CREATE/SHARE_REMOVE 공용).
     * targetUserSeqSet은 대상자 없는 공유(링크만 공유)일 때 빈 Set이 아니라 null로 내려오는 경우가 있어 방어한다.
     *
     * @param valueByUserSeq 대상자별 권한 스냅샷(JSON). SHARE_CREATE면 afterValue(부여된 권한), SHARE_REMOVE면 beforeValue(해제 전 권한)로 들어간다
     */
    public static void recordAll(EventName eventName, Set<Integer> targetUserSeqSet, List<UserData> userDataList,
                                  Long resourceSeq, String resourceName, String resourcePath, Integer actorTenantSeq,
                                  Map<Integer, String> valueByUserSeq) {
        if (targetUserSeqSet == null) {
            return;
        }
        for (Integer targetUserSeq : targetUserSeqSet) {
            var targetUser = findUserData(userDataList, targetUserSeq);
            if (targetUser == null) {
                continue;
            }
            String value = valueByUserSeq == null ? null : valueByUserSeq.get(targetUserSeq);
            String beforeValue = eventName == EventName.SHARE_REMOVE ? value : null;
            String afterValue = eventName == EventName.SHARE_CREATE ? value : null;
            record(eventName, resourceSeq, resourceName, resourcePath, targetUser, actorTenantSeq, beforeValue, afterValue);
        }
    }

    /** SHARE_CREATE 대상자별로 이번 요청이 부여한 권한 스냅샷을 JSON으로 만든다. */
    public static Map<Integer, String> grantedPermissionByUserSeq(ObjectMapperUtil objectMapperUtil,
                                                                   List<? extends ShareTargetValidationItem> items) {
        if (items == null) {
            return Map.of();
        }
        Map<Integer, String> result = new LinkedHashMap<>();
        for (ShareTargetValidationItem item : items) {
            result.put(item.userSeq(), objectMapperUtil.writeValueAsString(
                    permissionSnapshot(item.canView(), item.canEdit(), item.canDownload(), item.canUpload(), item.canShare())));
        }
        return result;
    }

    /**
     * SHARE_REMOVE 대상자별로 해제 전 갖고 있던 권한 스냅샷을 JSON으로 만든다.
     * targets는 해제 호출 전에 미리 조회해둔 "변경 전" 대상자 목록이어야 한다.
     */
    public static Map<Integer, String> currentPermissionByUserSeq(ObjectMapperUtil objectMapperUtil,
                                                                   List<ShareWithTargetsData.ShareTargetItem> targets) {
        if (targets == null) {
            return Map.of();
        }
        Map<Integer, String> result = new LinkedHashMap<>();
        for (var target : targets) {
            result.put(target.getUserSeq(), objectMapperUtil.writeValueAsString(
                    permissionSnapshot(target.getCanView(), target.getCanEdit(), target.getCanDownload(), target.getCanUpload(), target.getCanShare())));
        }
        return result;
    }

    /**
     * 변경 전후 모두에 있는 대상자 중 권한이 바뀐 건만 골라낸다. 추가·제거된 대상자는 SHARE_CREATE/SHARE_REMOVE가 담당한다.
     * 권한 하나만 바뀌어도 5개 전체를 스냅샷으로 담는다(스펙 3-2).
     */
    public static List<TargetPermissionChange> targetPermissionChanges(ObjectMapperUtil objectMapperUtil,
                                                                        List<ShareWithTargetsData.ShareTargetItem> beforeTargets,
                                                                        List<ShareTargetUpsertDto> afterTargets) {
        List<TargetPermissionChange> changes = new ArrayList<>();
        for (ShareTargetUpsertDto afterTarget : afterTargets) {
            var beforeTarget = findTargetItem(beforeTargets, afterTarget.getUserSeq());
            if (beforeTarget == null) {
                continue;
            }

            boolean changed = !Objects.equals(beforeTarget.getCanView(), afterTarget.getCanView())
                    || !Objects.equals(beforeTarget.getCanEdit(), afterTarget.getCanEdit())
                    || !Objects.equals(beforeTarget.getCanDownload(), afterTarget.getCanDownload())
                    || !Objects.equals(beforeTarget.getCanUpload(), afterTarget.getCanUpload())
                    || !Objects.equals(beforeTarget.getCanShare(), afterTarget.getCanShare());

            if (!changed) {
                continue;
            }

            String before = objectMapperUtil.writeValueAsString(permissionSnapshot(beforeTarget.getCanView(), beforeTarget.getCanEdit(),
                    beforeTarget.getCanDownload(), beforeTarget.getCanUpload(), beforeTarget.getCanShare()));
            String after = objectMapperUtil.writeValueAsString(permissionSnapshot(afterTarget.getCanView(), afterTarget.getCanEdit(),
                    afterTarget.getCanDownload(), afterTarget.getCanUpload(), afterTarget.getCanShare()));

            changes.add(new TargetPermissionChange(beforeTarget.getUserSeq(), beforeTarget.getUserId(), beforeTarget.getUserName(), before, after));
        }
        return changes;
    }

    /**
     * 공유 범위(shareType)와 링크 기본 권한 중 하나라도 바뀌었는지 판정하고, 바뀌었으면 5개 권한 전체를 스냅샷으로 담아
     * JSON diff를 만든다(스펙 3-2: SHARE_PERMISSION_CHANGE는 바뀐 필드만이 아니라 전체 스냅샷). 변경 없으면 {@code null}.
     */
    public static ScopeChange computeScopeChange(ObjectMapperUtil objectMapperUtil,
            String beforeShareType, Boolean beforeCanView, Boolean beforeCanEdit, Boolean beforeCanDownload, Boolean beforeCanUpload, Boolean beforeCanShare,
            String afterShareType, Boolean afterCanView, Boolean afterCanEdit, Boolean afterCanDownload, Boolean afterCanUpload, Boolean afterCanShare) {

        boolean changed = !Objects.equals(beforeShareType, afterShareType)
                || !Objects.equals(beforeCanView, afterCanView)
                || !Objects.equals(beforeCanEdit, afterCanEdit)
                || !Objects.equals(beforeCanDownload, afterCanDownload)
                || !Objects.equals(beforeCanUpload, afterCanUpload)
                || !Objects.equals(beforeCanShare, afterCanShare);

        if (!changed) {
            return null;
        }

        Map<String, Object> before = scopeSnapshot(beforeShareType, beforeCanView, beforeCanEdit, beforeCanDownload, beforeCanUpload, beforeCanShare);
        Map<String, Object> after = scopeSnapshot(afterShareType, afterCanView, afterCanEdit, afterCanDownload, afterCanUpload, afterCanShare);

        return new ScopeChange(objectMapperUtil.writeValueAsString(before), objectMapperUtil.writeValueAsString(after));
    }

    /** 공유 범위(shareType) + 링크 기본 권한 스냅샷. 키 순서가 before/after 비교에 그대로 보이므로 삽입 순서를 유지한다. */
    public static Map<String, Object> scopeSnapshot(String shareType, Boolean canView, Boolean canEdit, Boolean canDownload, Boolean canUpload, Boolean canShare) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("shareType", shareType);
        snapshot.put("canView", canView);
        snapshot.put("canEdit", canEdit);
        snapshot.put("canDownload", canDownload);
        snapshot.put("canUpload", canUpload);
        snapshot.put("canShare", canShare);
        return snapshot;
    }

    /** 대상자 권한 스냅샷. 키 순서가 before/after 비교에 그대로 보이므로 삽입 순서를 유지한다. */
    public static Map<String, Object> permissionSnapshot(Boolean canView, Boolean canEdit, Boolean canDownload,
                                                          Boolean canUpload, Boolean canShare) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("canView", canView);
        snapshot.put("canEdit", canEdit);
        snapshot.put("canDownload", canDownload);
        snapshot.put("canUpload", canUpload);
        snapshot.put("canShare", canShare);
        return snapshot;
    }

    private static UserData findUserData(List<UserData> userDataList, Integer userSeq) {
        return userDataList.stream()
                .filter(u -> Objects.equals(u.getUserSeq(), userSeq))
                .findFirst()
                .orElse(null);
    }

    private static ShareWithTargetsData.ShareTargetItem findTargetItem(List<ShareWithTargetsData.ShareTargetItem> targets, Integer userSeq) {
        if (targets == null) {
            return null;
        }
        return targets.stream()
                .filter(t -> Objects.equals(t.getUserSeq(), userSeq))
                .findFirst()
                .orElse(null);
    }
}

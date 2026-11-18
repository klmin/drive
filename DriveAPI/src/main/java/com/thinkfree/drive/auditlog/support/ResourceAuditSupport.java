package com.thinkfree.drive.auditlog.support;

import com.thinkfree.drive.auditlog.context.AuditLogContext;
import com.thinkfree.drive.auditlog.context.AuditLogContextHolder;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.resource.data.ResourceHierarchyData;
import com.thinkfree.drive.resource.data.ResourceIdentity;
import com.thinkfree.drive.user.data.UserIdentity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

// FILE_FOLDER 카테고리 이벤트는 UseCase(예: FILE_UPLOAD)와 Service(예: FILE_RENAME) 양쪽에서 발생하므로
// 호출 위치와 무관하게 이 클래스 하나로 AuditLogContext 빌드를 통일한다.
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ResourceAuditSupport {

    // RESOURCE shape(objectSeq/objectName/resourcePath, before/after 없음) - Resource 엔티티를 이미 갖고 있는 경우.
    // resourcePath는 resource.getFullPath()가 아니라 호출부가 ResourceAuditLookupService.findResourcePath(parentFolderSeq, userSeq)로
    // 별도 조회해서 넘겨야 함 (FILE은 자기 fullPath가 항상 null, FOLDER는 자기 이름까지 포함돼 있어 둘 다 그대로 못 씀)
    public static void record(EventName eventName, ResourceIdentity resource, String resourcePath) {
        AuditLogContextHolder.add(AuditLogContext.builder()
                .eventName(eventName)
                .objectSeq(resource.getResourceSeq())
                .objectName(resource.getResourceName())
                .resourcePath(resourcePath)
                .build());
    }

    // RESOURCE shape + actor override - 웹오피스 전용.
    // 웹오피스는 시큐리티 필터를 타지 않아 SecurityContext가 없으므로 호출부가 조회한 User를 actor로 넘긴다.
    // 익명 접근은 시스템 공용 익명 계정 1건이라 모든 익명 행위자가 같은 값으로 기록된다.
    public static void record(EventName eventName, ResourceIdentity resource, String resourcePath, UserIdentity actor) {
        AuditLogContextHolder.add(AuditLogContext.builder()
                .eventName(eventName)
                .objectSeq(resource.getResourceSeq())
                .objectName(resource.getResourceName())
                .resourcePath(resourcePath)
                .actorUserSeq(actor.getUserSeq())
                .actorUserId(actor.getUserId())
                .actorUserName(actor.getUserName())
                .build());
    }

    // RESOURCE shape, Resource 엔티티가 없을 때(예: 방금 생성된 리소스라 스칼라 값만 있는 경우)
    public static void record(EventName eventName, Long objectSeq, String objectName, String resourcePath) {
        AuditLogContextHolder.add(AuditLogContext.builder()
                .eventName(eventName)
                .objectSeq(objectSeq)
                .objectName(objectName)
                .resourcePath(resourcePath)
                .build());
    }

    // RESOURCE_DELETE shape(objectSeq/objectName만, resourcePath 없음) - PERMANENT_DELETE fan-out(ResourceBrief 기반)
    public static void recordDelete(EventName eventName, Long objectSeq, String objectName) {
        AuditLogContextHolder.add(AuditLogContext.builder()
                .eventName(eventName)
                .objectSeq(objectSeq)
                .objectName(objectName)
                .build());
    }

    // RESOURCE_DELETE shape + actor/tenantSeq override - 스케줄러 휴지통 자동정리(SYSTEM) 전용.
    // 요청/인증 컨텍스트가 없어 actor(슈퍼어드민)와 tenantSeq(대상 소유 테넌트, row별)를 명시로 넘긴다.
    // accessMethod는 override 안 함 → Aspect가 요청 없음으로 자동 SYSTEM 판정.
    public static void recordDelete(EventName eventName, Long objectSeq, String objectName, Integer tenantSeq, UserIdentity actor) {
        AuditLogContextHolder.add(AuditLogContext.builder()
                .eventName(eventName)
                .objectSeq(objectSeq)
                .objectName(objectName)
                .tenantSeq(tenantSeq)
                .actorUserSeq(actor.getUserSeq())
                .actorUserId(actor.getUserId())
                .actorUserName(actor.getUserName())
                .build());
    }

    // RESOURCE_CHANGE shape(objectSeq/objectName/resourcePath/beforeValue/afterValue) - RENAME/MOVE.
    // resourcePath는 record(EventName, Resource, String)와 동일하게 호출부가 별도 조회해서 넘겨야 함.
    public static void recordChange(EventName eventName, ResourceIdentity resource, String resourcePath, String beforeValue, String afterValue) {
        AuditLogContextHolder.add(AuditLogContext.builder()
                .eventName(eventName)
                .objectSeq(resource.getResourceSeq())
                .objectName(resource.getResourceName())
                .resourcePath(resourcePath)
                .beforeValue(beforeValue)
                .afterValue(afterValue)
                .build());
    }

    // RESOURCE_CHANGE shape, Resource 엔티티 하나로 objectName/resourcePath를 동시에 표현할 수 없을 때
    // (예: FILE_RENAME은 rename 후 최종 이름은 transient entity에, resourcePath는 rename 전 조회한 엔티티에 각각 있음)
    public static void recordChange(EventName eventName, Long objectSeq, String objectName, String resourcePath, String beforeValue, String afterValue) {
        AuditLogContextHolder.add(AuditLogContext.builder()
                .eventName(eventName)
                .objectSeq(objectSeq)
                .objectName(objectName)
                .resourcePath(resourcePath)
                .beforeValue(beforeValue)
                .afterValue(afterValue)
                .build());
    }

    // 멀티 다운로드 fan-out 전용 - ResourceHierarchyData 기반(FILE_DOWNLOAD/FOLDER_DOWNLOAD는 호출부가 결정).
    // item.getFullPath()는 이 다중조회 자체의 zip 엔트리 상대경로라 위치를 나타내지 않으므로 resourcePath는
    // 호출부가 ResourceAuditLookupService.findResourcePath(item.getParentFolderSeq(), userSeq)로 넘겨야 함.
    public static void record(EventName eventName, ResourceHierarchyData item, String resourcePath) {
        AuditLogContextHolder.add(AuditLogContext.builder()
                .eventName(eventName)
                .objectSeq(item.getResourceSeq())
                .objectName(item.getResourceName())
                .resourcePath(resourcePath)
                .build());
    }
}

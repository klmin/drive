package com.thinkfree.drive.auditlog.context;

import com.thinkfree.drive.auditlog.enums.AccessMethod;
import com.thinkfree.drive.auditlog.enums.EventName;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuditLogContext {
    private EventName eventName;

    private Long objectSeq;
    private String objectUserId;
    private String objectName;
    // 위치 루트(MY/SHARE) 포함 최종 경로. 예: MY/영업팀/2026
    private String resourcePath;

    private Integer targetTenantSeq;
    private String targetTenantName;
    private Integer targetUserSeq;
    private String targetUserId;
    private String targetUserName;

    private String beforeValue;
    private String afterValue;

    // override 필드. null이면 Aspect 기본 판정(tenantSeq·accessMethod=요청 컨텍스트, actor=SecurityContext).
    // 기본이 안 맞는 곳만 지정 — tenantSeq: SETTING_UPDATE 대상 테넌트 fan-out, accessMethod·actor: 인증 컨텍스트 없는 LOGIN 등.
    private Integer tenantSeq;
    private AccessMethod accessMethod;
    private Integer actorUserSeq;
    private String actorUserId;
    private String actorUserName;

    // outcome override. null이면 Aspect가 정상/예외로 판정. 메서드 결과와 무관해야 할 때만 지정 (예: 계정 잠김=SUCCESS).
    private String outcomeStatus;
}

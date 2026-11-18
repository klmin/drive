package com.thinkfree.drive.api.v1.admin.auditlog.response;

import com.thinkfree.drive.global.annotation.DriveDateTimeFormat;
import com.thinkfree.governance.sdk.client.EventSummary;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 감사 로그 목록 한 줄. 거버넌스 {@link EventSummary}(평평한 조회 필드)에서 화면에 필요한 값만 추린다.
 * SDK 타입을 프론트로 노출하지 않기 위한 경계 DTO.
 */
public record AuditLogItemResponse(
        String eventId,          // 이벤트 고유 ID (거버넌스 발급, 상세 조회 키)
        // 이벤트 발생 시각. 거버넌스는 UTC(RFC 3339)로 주지만, Drive 표준 시간 포맷(KST yyyy-MM-dd HH:mm:ss.SSS)으로 내려준다.
        @DriveDateTimeFormat Instant occurredAt,
        // eventType: 이벤트 종류 = 조회 필터(eventType 파라미터)가 매칭하는 값. 로그인 성공/실패는 LOGIN 하나로
        //            합쳐지고(구분은 outcomeStatus), LOGOUT·그 외는 TFD 원본명과 동일.
        //            (전송 시 sourceEventType도 같은 값이라 응답에는 eventType만 노출)
        String eventType,        // 이벤트 종류 (조회 필터 기준값)
        String eventCategory,    // TFD 상위 분류 (LOGIN / FILE_FOLDER / SHARE / USER_MANAGEMENT / SETTING)
        String actorType,        // 행위자 유형 (예: HUMAN)
        String actorName,        // 행위자(수행자) 이름
        String actorEmail,       // 행위자 이메일 (= Drive userId)
        String objectType,       // 대상 리소스 유형 (FILE / FOLDER / USER / SETTING)
        String objectName,       // 대상 이름 (파일·폴더명, 사용자명 등)
        String objectEmail,      // 대상 계정의 이메일 (= Drive userId. 대상이 계정인 이벤트에만)
        String objectPath,       // 대상 경로 (부모 폴더 fullPath. 루트 바로 아래면 빈값/null)
        String outcomeStatus,    // 처리 결과 (SUCCESS / FAIL)
        String outcomeCode,      // 실패 사유 코드 (실패 건에만, 없으면 null)
        String channel,          // 유입 경로 (WEB / API / MCP / SYSTEM)
        String clientIp,         // 요청 클라이언트 IP
        // 아래 2개는 거버넌스 내부 용어(scopeId/scopeName)를 Drive 언어로 번역해 노출한다.
        // scope.id에는 Drive의 tenantSeq를, scope.name에는 테넌트명을 실어 보내기 때문.
        // 나머지 필드는 거버넌스 계약 필드명을 그대로 사용한다(스펙 대조 용이).
        String tenantSeq,        // 테넌트 식별자 (거버넌스 scopeId)
        String tenantName,       // 테넌트명 (거버넌스 scopeName)
        Map<String, Object> change,   // 변경 전/후 내용 {before, after, summary} (변경성 이벤트에만)
        List<Object> targets          // 공유 대상자 등 대상 목록 (해당 이벤트에만)
) {
    public static AuditLogItemResponse from(EventSummary s) {
        return new AuditLogItemResponse(
                s.getEventId(),
                parseOccurredAt(s.getOccurredAt()),
                s.getEventType(),        // 이벤트 종류 (조회 필터 기준)
                s.getEventCategory(),
                s.getActorType(),
                s.getActorName(),
                s.getActorEmail(),
                s.getObjectType(),
                s.getObjectName(),
                objectEmail(s),
                s.getObjectPath(),
                s.getOutcomeStatus(),
                s.getOutcomeCode(),
                s.getChannel(),
                s.getClientIp(),
                s.getScopeId(),    // → tenantSeq
                s.getScopeName(),  // → tenantName
                s.getChange(),
                s.getTargets()
        );
    }

    /**
     * 대상 계정 이메일. SDK EventSummary에 아직 전용 getter가 없어 원문 map에서 읽는다
     * (계약이 늘어 SDK가 모르는 필드는 asMap으로 읽으라는 SDK 안내를 따름).
     */
    private static String objectEmail(EventSummary s) {
        Object value = s.asMap().get("objectEmail");
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    /** 거버넌스 UTC 시각 문자열(Z/offset 모두 허용)을 Instant로. 없거나 형식이 예상과 다르면 null. */
    private static Instant parseOccurredAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeException e) {
            return null;
        }
    }
}


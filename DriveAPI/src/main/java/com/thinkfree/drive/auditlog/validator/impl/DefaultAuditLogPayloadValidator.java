package com.thinkfree.drive.auditlog.validator.impl;

import com.thinkfree.drive.auditlog.payload.AuditLogPayload;
import com.thinkfree.drive.auditlog.validator.AuditLogPayloadValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 전송 직전 payload에 이벤트별 필수 항목이 다 채워졌는지 검사한다.
 * <p>
 * 누락이 있어도 <b>전송은 그대로 진행</b>하고(payload는 null인 채로 전송) 경고 로그만 남긴다.
 * 감사 로그는 fire-and-forget이라 불완전하더라도 "이벤트가 일어났다"는 기록 자체는 남기는 게 누락보다 낫다.
 * <p>
 * 검사 대상:
 * <ul>
 *   <li>공통 필수 필드(DDL NOT NULL): eventRegisterDate, tenantSeq, userSeq, userId, eventCategory, eventName, accessMethod.
 *       (tenantName/userName/ipAddress는 nullable이라 필수 아님 - 특히 ipAddress는 SYSTEM 이벤트에서 정당하게 null)</li>
 *   <li>eventName의 {@link com.thinkfree.drive.auditlog.enums.CollectionShape}별 추가 필수 필드.</li>
 * </ul>
 * <p>
 * resourcePath는 필수에서 제외한다 - 스펙 4-1상 루트 리소스는 경로가 없어(괄호 생략) 정당하게 null일 수 있다.
 * SHARE 계열의 target* 는 조건부 - 대상자별 row에는 셋 다 있어야 하지만 링크 레벨 row에는 아예 없다(있으면 셋 다 검사, 없으면 통과).
 * <p>
 * before/after는 <b>값의 존재 유무만</b> 확인하고 형식(JSON 여부·특정 key·값 종류)은 검사하지 않는다.
 * before/after의 형식은 이벤트마다 평문(RENAME/MOVE)·델타 JSON(ALLOWED_IP)·스냅샷 JSON(SHARE/INFO_UPDATE) 등으로 이미 제각각이고
 * 이벤트가 추가되면 더 다양해질 수 있어, 형식을 이 검증기에 하드코딩하면 이벤트가 늘 때마다 깨지기 쉽다.
 */
@Slf4j
@Component
public class DefaultAuditLogPayloadValidator implements AuditLogPayloadValidator {

    @Override
    public void validate(AuditLogPayload payload) {
        List<String> missing = collectMissingRequiredFields(payload);

        if (!missing.isEmpty()) {
            // 누락돼도 전송은 그대로 하되, 추적 가능하도록 경고 로그를 남긴다.
            log.warn("[AuditLog] payload missing required field(s) {} - sent anyway. eventName={}, objectSeq={}, objectUserId={}",
                    missing, payload.getEventName(), payload.getObjectSeq(), payload.getObjectUserId());
        }
    }

    // 누락된 필수 필드명 목록을 계산한다(공통 + shape별). 검증 규칙 자체를 단위 테스트로 검증하기 위해 분리(package-private).
    List<String> collectMissingRequiredFields(AuditLogPayload payload) {
        List<String> missing = new ArrayList<>();
        checkCommon(payload, missing);
        checkShape(payload, missing);
        return missing;
    }

    private void checkCommon(AuditLogPayload p, List<String> missing) {
        require(p.getEventRegisterDate(), "eventRegisterDate", missing);
        require(p.getTenantSeq(), "tenantSeq", missing);
        require(p.getUserSeq(), "userSeq", missing);
        require(p.getUserId(), "userId", missing);
        require(p.getEventCategory(), "eventCategory", missing);
        require(p.getEventName(), "eventName", missing);
        require(p.getAccessMethod(), "accessMethod", missing);
    }

    private void checkShape(AuditLogPayload p, List<String> missing) {
        if (p.getEventName() == null) {
            return; // eventName 자체가 없으면 shape를 판단할 수 없음(공통 검사에서 이미 누락으로 기록됨)
        }
        switch (p.getEventName().getCollectionShape()) {
            case NONE -> {
                // 추가 필수 항목 없음
            }
            case RESOURCE, RESOURCE_DELETE -> requireObject(p, missing);
            case RESOURCE_CHANGE -> {
                requireObject(p, missing);
                require(p.getBeforeValue(), "beforeValue", missing);
                require(p.getAfterValue(), "afterValue", missing);
            }
            case SHARE_CREATE -> {
                requireObject(p, missing);
                require(p.getAfterValue(), "afterValue", missing);
                requireShareTargetIfPresent(p, missing);
            }
            case SHARE_REMOVE -> {
                requireObject(p, missing);
                require(p.getBeforeValue(), "beforeValue", missing);
                requireShareTargetIfPresent(p, missing);
            }
            case SHARE_PERMISSION_CHANGE -> {
                requireObject(p, missing);
                require(p.getBeforeValue(), "beforeValue", missing);
                require(p.getAfterValue(), "afterValue", missing);
                requireShareTargetIfPresent(p, missing);
            }
            case USER -> requireUser(p, missing);
            case USER_CHANGE -> {
                requireUser(p, missing);
                require(p.getBeforeValue(), "beforeValue", missing);
                require(p.getAfterValue(), "afterValue", missing);
            }
            case VALUE_CHANGE -> {
                // SETTING_ALLOWED_IP_CHANGE - beforeValue는 델타 방식이라 항상 null, afterValue(델타)만 필수
                require(p.getAfterValue(), "afterValue", missing);
            }
            case SETTING_CHANGE -> {
                require(p.getObjectSeq(), "objectSeq", missing);
                require(p.getObjectName(), "objectName", missing);
                require(p.getBeforeValue(), "beforeValue", missing);
                require(p.getAfterValue(), "afterValue", missing);
            }
        }
    }

    // FILE_FOLDER/SHARE 계열의 리소스 식별 필드
    private void requireObject(AuditLogPayload p, List<String> missing) {
        require(p.getObjectSeq(), "objectSeq", missing);
        require(p.getObjectName(), "objectName", missing);
    }

    // USER_MANAGEMENT 계열의 유저 식별 필드
    private void requireUser(AuditLogPayload p, List<String> missing) {
        require(p.getObjectSeq(), "objectSeq", missing);
        require(p.getObjectUserId(), "objectUserId", missing);
        require(p.getObjectName(), "objectName", missing);
    }

    // SHARE 대상자 정보는 조건부: 하나라도 있으면 대상자별 row로 보고 셋 다 요구, 셋 다 없으면 링크 레벨 row로 보고 통과.
    private void requireShareTargetIfPresent(AuditLogPayload p, List<String> missing) {
        boolean anyTarget = p.getTargetUserSeq() != null || p.getTargetUserId() != null || p.getTargetUserName() != null;
        if (!anyTarget) {
            return;
        }
        require(p.getTargetUserSeq(), "targetUserSeq", missing);
        require(p.getTargetUserId(), "targetUserId", missing);
        require(p.getTargetUserName(), "targetUserName", missing);
    }

    private void require(Object value, String fieldName, List<String> missing) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            missing.add(fieldName);
        }
    }
}

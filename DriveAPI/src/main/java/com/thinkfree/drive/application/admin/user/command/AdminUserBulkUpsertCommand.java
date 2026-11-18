package com.thinkfree.drive.application.admin.user.command;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.exception.UserErrorCode;
import com.thinkfree.drive.util.StringUtil;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 사용자 일괄 등록/수정(Bulk Upsert) 커맨드 — 웹 요청(AdminUserBulkUpsertRequest)과 항목 동형(필드명만 전체 표기).
 * insert/update 분리·정규화·필수값 검증(구 request record 소유 로직)은 진입점 공용이라 커맨드가 소유한다.
 */
public record AdminUserBulkUpsertCommand
(
    List<UserItemCommand> users
)
{
    public record UserItemCommand(
            Integer userSeq,       // null=insert, present=update
            String userId,         // insert 시 필수
            String encPwd,         // insert 시 필수
            String userName,
            Long totalQuotaByte,
            Set<RoleType> roles,   // insert 시 필수(USER/ADMIN 1개 이상), update 는 null 이면 역할 유지
            String countryCode,
            String phoneNumber
    ) {
        public boolean isInsert() {
            return userSeq == null;
        }

        public void validateInsertFields() {
            if (!StringUtils.hasText(userId) || !StringUtils.hasText(encPwd) || roles == null) {
                throw ApiRuntimeException.type(UserErrorCode.REQUIRED_FIELD);
            }
        }

        public void validatePhoneFields() {
            StringUtil.validateRequiredTogether(countryCode, phoneNumber,
                    () -> ApiRuntimeException.type(UserErrorCode.PHONE_FIELDS_REQUIRED_TOGETHER));
        }

        /** 역할별로 확정한 할당량(TenantQuotaPolicy.resolveUserQuota)을 담은 항목 */
        public UserItemCommand withTotalQuotaByte(long resolvedTotalQuotaByte) {
            return new UserItemCommand(userSeq, userId, encPwd, userName, resolvedTotalQuotaByte, roles,
                    countryCode, phoneNumber);
        }

        /** 교체 후 보유 역할을 담은 항목 — update 의 roles 생략(유지)을 현재 보유 역할로 채운다 */
        public UserItemCommand withRoles(Set<RoleType> resolvedRoles) {
            return new UserItemCommand(userSeq, userId, encPwd, userName, totalQuotaByte, resolvedRoles,
                    countryCode, phoneNumber);
        }

        public UserItemCommand normalized() {
            return new UserItemCommand(userSeq, userId, encPwd, userName, totalQuotaByte, roles,
                    StringUtil.normalizeCountryCode(countryCode),
                    StringUtil.normalizePhoneNumber(phoneNumber));
        }
    }

    public PartitionedItems partition() {
        List<UserItemCommand> inserts = new ArrayList<>();
        List<UserItemCommand> updates = new ArrayList<>();

        for (UserItemCommand item : users) {
            item.validatePhoneFields();
            UserItemCommand normalized = item.normalized();
            if (normalized.isInsert()) {
                normalized.validateInsertFields();
                inserts.add(normalized);
            } else {
                updates.add(normalized);
            }
        }

        return new PartitionedItems(inserts, updates);
    }

    public record PartitionedItems(List<UserItemCommand> inserts, List<UserItemCommand> updates) {}
}

package com.thinkfree.drive.auditlog.support;

import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.objectmapper.util.ObjectMapperUtil;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.role.service.UserRoleFindService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * USER_INFO_UPDATE / ADMIN_INFO_UPDATE 감사 로그 기록 헬퍼.
 * 변경 전 {@link User}와 변경 후 값들을 비교해 <b>바뀐 필드만</b> before/after JSON에 담는다
 * (스펙 3-2: USER_INFO_UPDATE는 전체 스냅샷이 아니라 변경분만). 바뀐 게 없으면 기록하지 않는다.
 * 단건 수정(AdminUseCase)과 일괄 수정(AdminUserUseCase.bulkUpsert)이 동일 로직을 공유하도록 분리함.
 * JSON 직렬화가 필요해 static 유틸이 아니라 Spring bean.
 */
@Component
@RequiredArgsConstructor
public class UserInfoUpdateAuditSupport {

    private final ObjectMapperUtil objectMapperUtil;
    private final UserRoleFindService userRoleFindService;

    /**
     * 사용자 본인 정보 수정(updateCurrentUser) 전용.
     * 본인은 이름/저장용량을 변경할 수 없어(관리자만 가능) 감사 대상 필드 중 실제로 바뀔 수 있는
     * 국가코드/전화번호만 비교한다. 나머지(언어/랜딩페이지/프로필이미지)는 감사 스펙 수집 대상이 아님.
     * 이름/저장용량은 변경 전 값을 그대로 넘겨(=diff에서 자연히 제외) 공통 record 로직을 재사용한다.
     */
    public void recordSelfInfoUpdate(UserDetailData before, String newCountryCode, String newPhone) {
        record(before, before.getUserName(), before.getTotalQuotaByte(), newCountryCode, newPhone);
    }

    public void record(UserDetailData before, String newName, Long newStorageQuota, String newCountryCode, String newPhone) {
        Map<String, Object> beforeMap = new LinkedHashMap<>();
        Map<String, Object> afterMap = new LinkedHashMap<>();

        putIfChanged(beforeMap, afterMap, "name", before.getUserName(), newName);
        putIfChanged(beforeMap, afterMap, "storageQuota", before.getTotalQuotaByte(), newStorageQuota);
        putIfChanged(beforeMap, afterMap, "countryCode", before.getCountryCode(), newCountryCode);
        putIfChanged(beforeMap, afterMap, "phone", before.getPhoneNumber(), newPhone);

        if (beforeMap.isEmpty()) {
            return;
        }

        EventName eventName = userRoleFindService.holds(before.getUserSeq(), RoleType.ADMIN) ? EventName.ADMIN_INFO_UPDATE : EventName.USER_INFO_UPDATE;
        // Resource 표시용 objectName은 "변경 후" 이름으로 남긴다(이름 미변경 시엔 newName == 기존 이름).
        // userId(이메일)는 정보 수정으로 바뀌지 않으므로 before의 값을 그대로 사용.
        String objectName = newName != null ? newName : before.getUserName();
        UserAuditSupport.record(eventName, before.getUserSeq().longValue(), before.getUserId(), objectName,
                objectMapperUtil.writeValueAsString(beforeMap),
                objectMapperUtil.writeValueAsString(afterMap));
    }

    private void putIfChanged(Map<String, Object> before, Map<String, Object> after, String key, Object oldValue, Object newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            before.put(key, oldValue);
            after.put(key, newValue);
        }
    }
}

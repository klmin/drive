package com.thinkfree.drive.global.security.tenant;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.user.exception.UserErrorCode;
import com.thinkfree.drive.user.repository.UserQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 다건 userSeq의 테넌트 경계를 검증하는 유틸리티. usecase/service에서 명시적으로 호출한다.
 *
 * <p>단건은 {@link com.thinkfree.drive.global.security.tenant.annotation.TenantUser} 어노테이션 + aspect 사용.
 */
@Component
@RequiredArgsConstructor
public class TenantValidator {

    private final UserQueryRepository userQueryRepository;

    /**
     * 주어진 userSeq 목록이 모두 현재 요청의 테넌트에 속하는지 검증.
     * 하나라도 다른 테넌트거나 존재하지 않으면 USER_NOT_FOUND (404).
     */
    public void assertSameTenant(List<Integer> userSeqList) {
        if (userSeqList == null || userSeqList.isEmpty()) {
            return;
        }
        Integer tenantSeq = TenantContext.getOrThrow();
        int matched = userQueryRepository.countByUserSeqsAndTenant(userSeqList, tenantSeq);
        if (matched != userSeqList.size()) {
            throw ApiRuntimeException.type(UserErrorCode.USER_NOT_FOUND);
        }
    }
}

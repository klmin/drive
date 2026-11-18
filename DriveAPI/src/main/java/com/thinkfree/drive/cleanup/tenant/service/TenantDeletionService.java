package com.thinkfree.drive.cleanup.tenant.service;

import com.thinkfree.drive.cleanup.user.service.UserPermanentDeletionService;
import com.thinkfree.drive.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 테넌트 삭제 — 한 트랜잭션에서 사용자 영구 삭제(기존 공통 처리) → 테넌트 범위 데이터 → tb_tenant 행.
 * DB 밖 정리는 각 모듈이 커밋 후에 한다: 파일 스토리지(ResourceDeletedEvent, 비동기)·라이선스 캐시(LicenseService)·호스트 캐시(TenantService).
 * 유형 검사(지금은 SYSTEM_MANAGED 만)는 호출부. 감사 기록 없음.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TenantDeletionService {

    private final TenantService tenantService;
    private final UserPermanentDeletionService userPermanentDeletionService;
    private final TenantCleanUpService tenantCleanUpService;

    public void deleteTenant(int tenantSeq, int actorUserSeq) {
        // 행 잠금 — 같은 테넌트의 사용자 등록·총 용량 변경과 직렬화. 없으면 TENANT_NOT_FOUND
        tenantService.lock(tenantSeq);

        // tb_user 는 DELETED 로 남는다(tb_user_log·감사 참조)
        userPermanentDeletionService.deleteAllInTenant(tenantSeq, actorUserSeq);

        tenantCleanUpService.deleteTenantScopedData(tenantSeq);

        tenantService.delete(tenantSeq);
    }
}

package com.thinkfree.drive.cleanup.share.global.service.impl;

import com.thinkfree.drive.cleanup.share.global.plan.ShareAccessScope;
import com.thinkfree.drive.cleanup.share.global.plan.ShareCleanupPlan;
import com.thinkfree.drive.cleanup.share.global.service.GlobalShareCleanUpService;
import com.thinkfree.drive.pinnedfolder.service.PinnedFolderService;
import com.thinkfree.drive.recent.service.RecentService;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.share.service.ShareService;
import com.thinkfree.drive.share.target.service.ShareTargetService;
import com.thinkfree.drive.starred.service.StarredService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DefaultGlobalShareCleanUpService implements GlobalShareCleanUpService {

    private final ShareService shareService;
    private final ShareTargetService shareTargetService;
    private final RecentService recentService;
    private final StarredService starredService;
    private final PinnedFolderService pinnedFolderService;

    @Override
    public void cleanupAllSharesByTenant(Integer tenantSeq) {
        log.info("전역 공유 설정 비활성화 - 테넌트 공유 데이터 정리 시작: tenantSeq : {}", tenantSeq);

        recentService.deleteAllSharedByTenant(tenantSeq);
        starredService.deleteAllSharedByTenant(tenantSeq);
        pinnedFolderService.unpinAllSharedByTenant(tenantSeq);
        shareService.deleteByTenant(tenantSeq);

        log.info("테넌트 공유 데이터 정리 완료: tenantSeq : {}", tenantSeq);
    }

    @Override
    public void apply(Integer tenantSeq, ShareCleanupPlan plan) {
        
        if (plan.isNoop()) {
            return;
        }

        // 공유 기능 자체 OFF → 테넌트 전체 정리(나머지 선별 연산은 ofFullWipe 특성상 모두 no-op).
        if (plan.fullWipe()) {
            cleanupAllSharesByTenant(tenantSeq);
            return;
        }

        // 1) 불허된 scope 공유를 통째로 삭제(메타데이터 → 대상 → share 순으로 살아있는 share 와 조인).
        //    대상(share_target)은 shareService.deleteByTenantAndScopes 내부에서 함께 삭제한다.
        List<String> shareTypes = toShareTypeNames(plan.deleteScopes());
        
        if (!shareTypes.isEmpty()) {
            recentService.deleteSharedByTenantAndShareTypes(tenantSeq, shareTypes);
            starredService.deleteSharedByTenantAndShareTypes(tenantSeq, shareTypes);
            pinnedFolderService.unpinSharedByTenantAndShareTypes(tenantSeq, shareTypes);
            shareService.deleteByTenantAndScopes(tenantSeq, shareTypes);
        }

        // 2) 살아있는 공유의 공유대상 strip — share 행은 유지.
        //    제한됨 OFF(전체) 가 외부공유 OFF(다른 테넌트) 의 상위 집합이므로 우선.
        if (plan.stripAllTargets()) {
            shareTargetService.deleteByTenant(tenantSeq);
        } else if (plan.stripOtherTenantTargets()) {
            shareTargetService.deleteOtherTenantByTenant(tenantSeq);
        }

        // 3) 다른 테넌트 메타(최근/중요/핀) 정리 — 접근경로가 0이 된 다른 테넌트 접근자만.
        //    같은 테넌트 메타는 건드리지 않는다(살아있는 링크로 접근 유지 가능 — live-gating 신뢰).
        if (plan.deleteOtherTenantMeta()) {
            recentService.deleteOtherTenantSharedByTenant(tenantSeq);
            starredService.deleteOtherTenantSharedByTenant(tenantSeq);
            pinnedFolderService.unpinOtherTenantSharedByTenant(tenantSeq);
        }
    }

    /**
     * cleanup scope(3값) → share 모델의 {@link ShareType} 이름으로 매핑한다.
     * SPECIFIC→SPECIFIC_USERS, ORG_LINK→ORG_LINK, PUBLIC→PUBLIC_LINK.
     */
    private List<String> toShareTypeNames(Set<ShareAccessScope> scopes) {
        List<String> names = new ArrayList<>();
        for (ShareAccessScope scope : scopes) {
            switch (scope) {
                case SPECIFIC -> names.add(ShareType.SPECIFIC_USERS.name());
                case ORG_LINK -> names.add(ShareType.ORG_LINK.name());
                case PUBLIC -> names.add(ShareType.PUBLIC_LINK.name());
            }
        }
        return names;
    }
}

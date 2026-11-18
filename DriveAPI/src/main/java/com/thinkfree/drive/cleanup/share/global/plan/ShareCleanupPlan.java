package com.thinkfree.drive.cleanup.share.global.plan;

import java.util.Set;

/**
 * 공유 설정 변경(old → new effective) 결과로 "무엇을 지울지"를 표현하는 순수 결과값.
 * <p>
 * 5개 연산의 조합으로 귀결된다(각 축은 "허용→불허(revoke)" 일 때만 동작):
 * <ul>
 *   <li>{@link #fullWipe} — 공유 기능 자체 OFF: 테넌트 전체 삭제</li>
 *   <li>{@link #deleteScopes} — 불허된 scope 공유를 통째(share+대상+메타) 삭제</li>
 *   <li>{@link #stripAllTargets} — 제한됨(개별 대상 추가 능력) OFF: 살아있는 공유의 공유대상 전부 제거</li>
 *   <li>{@link #stripOtherTenantTargets} — 외부공유 OFF: 살아있는 공유의 다른 테넌트 공유대상만 제거</li>
 *   <li>{@link #deleteOtherTenantMeta} — 다른 테넌트가 접근경로를 모두 잃을 때 그들의 메타(최근/중요/핀) 제거</li>
 * </ul>
 * 핵심: <b>같은 테넌트 메타는 벌크 삭제하지 않는다</b>. 살아있는 링크(조직내부/전체공개)로 접근이 유지될 수 있어
 * stale 여부 판정이 리소스별이라 벌크로 불가능하기 때문 — live-gating(접근 시점 차단)에 맡긴다.
 * 다른 테넌트 메타만, 그것도 "접근경로가 0이 됨이 확실할 때"(외부 OFF, 또는 제한됨 OFF + 전체공개 OFF)만 정리한다.
 * <p>
 * 하이어라키(부모→자식 전파)는 고려하지 않는다 — 벌크 정리는 조건에 맞는 row 를 한 번에 지운다.
 */
public record ShareCleanupPlan(
        boolean fullWipe,
        Set<ShareAccessScope> deleteScopes,
        boolean stripAllTargets,
        boolean stripOtherTenantTargets,
        boolean deleteOtherTenantMeta
) {

    public static ShareCleanupPlan ofFullWipe() {
        return new ShareCleanupPlan(true, Set.of(), false, false, false);
    }

    public static ShareCleanupPlan ofNoop() {
        return new ShareCleanupPlan(false, Set.of(), false, false, false);
    }

    public boolean isNoop() {
        return !fullWipe
                && deleteScopes.isEmpty()
                && !stripAllTargets
                && !stripOtherTenantTargets
                && !deleteOtherTenantMeta;
    }

    /** fullWipe 외 선별 삭제 포함 여부 — 실행기가 미구현(Phase 2) 경로를 가려내기 위함. */
    public boolean hasSelectiveDeletion() {
        return !deleteScopes.isEmpty()
                || stripAllTargets
                || stripOtherTenantTargets
                || deleteOtherTenantMeta;
    }
}

package com.thinkfree.drive.auditlog.service;

import com.thinkfree.drive.application.share.validation.ShareTargetValidationItem;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.support.ShareAuditSupport;
import com.thinkfree.drive.share.data.ShareWithTargetsData;
import com.thinkfree.drive.share.target.dto.ShareTargetUpsertDto;
import com.thinkfree.drive.user.data.UserData;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SHARE 카테고리 감사 로그 기록의 단일 진입점. 호출부는 {@link ShareAuditSupport}를
 * 직접 부르지 않고 이 서비스만 의존한다(Support는 내부 구현).
 *
 * <p>SHARE_PERMISSION_CHANGE의 "변경 전" 값(공유 범위/대상자별 권한)은 뮤테이션 전에 조회해야 하므로 호출부가
 * {@link com.thinkfree.drive.auditlog.lookup.share.ShareAuditLookupService#findCurrentShareState}로 미리 캡처해서 넘긴다.
 */
public interface ShareAuditService {

    /** SHARE_CREATE 링크 레벨 row(특정 대상자 없음) — 공유 생성 시 항상 1건 기록한다. */
    void recordLinkCreated(Long resourceSeq, String resourceName, String resourcePath,
                            String shareType, Boolean canView, Boolean canEdit, Boolean canDownload, Boolean canUpload, Boolean canShare);

    /** SHARE_REMOVE 링크 레벨 row(특정 대상자 없음) — 공유 해제 시 항상 1건 기록한다. */
    void recordLinkRemoved(Long resourceSeq, String resourceName, String resourcePath,
                            String shareType, Boolean canView, Boolean canEdit, Boolean canDownload, Boolean canUpload, Boolean canShare);

    /** 대상자 집합만큼 SHARE_CREATE/SHARE_REMOVE row를 fan-out한다. */
    void recordAll(EventName eventName, Set<Integer> targetUserSeqSet, List<UserData> userDataList,
                   Long resourceSeq, String resourceName, String resourcePath, Integer actorTenantSeq,
                   Map<Integer, String> valueByUserSeq);

    /** SHARE_PERMISSION_CHANGE — 공유 범위(shareType)/링크 기본 권한 변경(특정 대상자 없음). */
    void recordPermissionChange(Long resourceSeq, String resourceName, String resourcePath, String beforeValue, String afterValue);

    /** SHARE_PERMISSION_CHANGE — 유지되는 대상자 1명의 권한 변경. */
    void recordTargetPermissionChange(Long resourceSeq, String resourceName, String resourcePath,
                                       ShareAuditSupport.TargetPermissionChange change, List<UserData> userDataList, Integer actorTenantSeq);

    /** SHARE_CREATE afterValue용 — 대상자별로 이번 요청이 부여한 권한 스냅샷 JSON을 만든다. */
    Map<Integer, String> grantedPermissionByUserSeq(List<? extends ShareTargetValidationItem> items);

    /** SHARE_REMOVE beforeValue용 — 대상자별 해제 전 권한 스냅샷 JSON을 만든다. targets는 뮤테이션 전에 조회한 목록이어야 한다. */
    Map<Integer, String> currentPermissionByUserSeq(List<ShareWithTargetsData.ShareTargetItem> targets);

    /** 변경 전후 모두에 있는 대상자 중 권한이 바뀐 건만 골라 before/after 스냅샷 JSON diff를 만든다. */
    List<ShareAuditSupport.TargetPermissionChange> targetPermissionChanges(
            List<ShareWithTargetsData.ShareTargetItem> beforeTargets, List<ShareTargetUpsertDto> afterTargets);

    /**
     * 공유 범위(shareType)/링크 기본 권한이 바뀌었는지 판정하고, 바뀌었으면 before/after 스냅샷 JSON diff를 만든다.
     * 변경이 없으면 {@code null}.
     */
    ShareAuditSupport.ScopeChange computeScopeChange(
            String beforeShareType, Boolean beforeCanView, Boolean beforeCanEdit, Boolean beforeCanDownload, Boolean beforeCanUpload, Boolean beforeCanShare,
            String afterShareType, Boolean afterCanView, Boolean afterCanEdit, Boolean afterCanDownload, Boolean afterCanUpload, Boolean afterCanShare);
}

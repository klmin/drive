package com.thinkfree.drive.auditlog.service.impl;

import com.thinkfree.drive.application.share.validation.ShareTargetValidationItem;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.ShareAuditService;
import com.thinkfree.drive.auditlog.support.ShareAuditSupport;
import com.thinkfree.drive.objectmapper.util.ObjectMapperUtil;
import com.thinkfree.drive.share.data.ShareWithTargetsData;
import com.thinkfree.drive.share.target.dto.ShareTargetUpsertDto;
import com.thinkfree.drive.user.data.UserData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DefaultShareAuditService implements ShareAuditService {

    private final ObjectMapperUtil objectMapperUtil;

    @Override
    public void recordLinkCreated(Long resourceSeq, String resourceName, String resourcePath,
                                   String shareType, Boolean canView, Boolean canEdit, Boolean canDownload, Boolean canUpload, Boolean canShare) {
        var snapshot = ShareAuditSupport.scopeSnapshot(shareType, canView, canEdit, canDownload, canUpload, canShare);
        ShareAuditSupport.recordLinkCreated(resourceSeq, resourceName, resourcePath, objectMapperUtil.writeValueAsString(snapshot));
    }

    @Override
    public void recordLinkRemoved(Long resourceSeq, String resourceName, String resourcePath,
                                   String shareType, Boolean canView, Boolean canEdit, Boolean canDownload, Boolean canUpload, Boolean canShare) {
        var snapshot = ShareAuditSupport.scopeSnapshot(shareType, canView, canEdit, canDownload, canUpload, canShare);
        ShareAuditSupport.recordLinkRemoved(resourceSeq, resourceName, resourcePath, objectMapperUtil.writeValueAsString(snapshot));
    }

    @Override
    public void recordAll(EventName eventName, Set<Integer> targetUserSeqSet, List<UserData> userDataList,
                           Long resourceSeq, String resourceName, String resourcePath, Integer actorTenantSeq,
                           Map<Integer, String> valueByUserSeq) {
        ShareAuditSupport.recordAll(eventName, targetUserSeqSet, userDataList, resourceSeq, resourceName, resourcePath, actorTenantSeq, valueByUserSeq);
    }

    @Override
    public void recordPermissionChange(Long resourceSeq, String resourceName, String resourcePath, String beforeValue, String afterValue) {
        ShareAuditSupport.recordPermissionChange(resourceSeq, resourceName, resourcePath, beforeValue, afterValue);
    }

    @Override
    public void recordTargetPermissionChange(Long resourceSeq, String resourceName, String resourcePath,
                                              ShareAuditSupport.TargetPermissionChange change, List<UserData> userDataList, Integer actorTenantSeq) {
        ShareAuditSupport.recordTargetPermissionChange(resourceSeq, resourceName, resourcePath, change, userDataList, actorTenantSeq);
    }

    @Override
    public Map<Integer, String> grantedPermissionByUserSeq(List<? extends ShareTargetValidationItem> items) {
        return ShareAuditSupport.grantedPermissionByUserSeq(objectMapperUtil, items);
    }

    @Override
    public Map<Integer, String> currentPermissionByUserSeq(List<ShareWithTargetsData.ShareTargetItem> targets) {
        return ShareAuditSupport.currentPermissionByUserSeq(objectMapperUtil, targets);
    }

    @Override
    public List<ShareAuditSupport.TargetPermissionChange> targetPermissionChanges(
            List<ShareWithTargetsData.ShareTargetItem> beforeTargets, List<ShareTargetUpsertDto> afterTargets) {
        return ShareAuditSupport.targetPermissionChanges(objectMapperUtil, beforeTargets, afterTargets);
    }

    @Override
    public ShareAuditSupport.ScopeChange computeScopeChange(
            String beforeShareType, Boolean beforeCanView, Boolean beforeCanEdit, Boolean beforeCanDownload, Boolean beforeCanUpload, Boolean beforeCanShare,
            String afterShareType, Boolean afterCanView, Boolean afterCanEdit, Boolean afterCanDownload, Boolean afterCanUpload, Boolean afterCanShare) {
        return ShareAuditSupport.computeScopeChange(objectMapperUtil,
                beforeShareType, beforeCanView, beforeCanEdit, beforeCanDownload, beforeCanUpload, beforeCanShare,
                afterShareType, afterCanView, afterCanEdit, afterCanDownload, afterCanUpload, afterCanShare);
    }
}

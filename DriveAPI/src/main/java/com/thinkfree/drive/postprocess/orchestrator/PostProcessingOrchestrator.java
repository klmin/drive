package com.thinkfree.drive.postprocess.orchestrator;

import com.thinkfree.drive.cleanup.resource.service.ResourceCleanUpService;
import com.thinkfree.drive.postprocess.orchestrator.converter.PostProcessingOrchestratorConverter;
import com.thinkfree.drive.postprocess.orchestrator.dto.*;
import com.thinkfree.drive.postprocess.quota.service.QuotaPostProcessingService;
import com.thinkfree.drive.postprocess.share.service.SharePostProcessingService;
import com.thinkfree.drive.postprocess.upload.service.UploadPostProcessingService;
import com.thinkfree.drive.quota.service.QuotaService;
import com.thinkfree.drive.tenant.service.TenantFindService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PostProcessingOrchestrator {
    
    private final PostProcessingOrchestratorConverter postProcessingOrchestratorConverter;
    private final UploadPostProcessingService uploadPostProcessingService;
    private final QuotaPostProcessingService quotaPostProcessingService;
    private final SharePostProcessingService sharePostProcessingService;
    private final QuotaService quotaService;
    private final ResourceCleanUpService resourceCleanupService;
    private final TenantFindService tenantFindService;

    private String resolveTenantName(Integer tenantSeq) {
        if (tenantSeq == null) {
            return null;
        }
        return tenantFindService.findByTenantSeq(tenantSeq).getTenantName();
    }
    
    /**
     * 추후 사용시 수정 필요. share_target 알림 : 공유유형이 SPECIFIC_USERS인 데이터의 공유 대상만 가져오도록 되어 있음.
     * @param dto
     */
    public void processAfterUploadAndNotify(PostProcessUploadDto dto){
        var userSeq = dto.userSeq();
        var actorUserSeq = dto.actorUserSeq();
        var occurredDatetime = dto.occurredDatetime();
        var sharingUploadNotifyDto = postProcessingOrchestratorConverter.toSharingUploadNotifyDto(
                dto.parentFolderSeq(), dto.resourceSeq(), dto.folderName(), dto.fileName(), userSeq, dto.userName(), actorUserSeq, occurredDatetime, dto.isDelete()
        );
        
        var quotaStateChangeNotifyDto = postProcessingOrchestratorConverter.toQuotaStateChangeNotifyDto(userSeq, actorUserSeq, occurredDatetime);
        
        uploadPostProcessingService.processSharedUploadAndNotify(sharingUploadNotifyDto);
        quotaPostProcessingService.processQuotaIncreaseAndNotify(quotaStateChangeNotifyDto);
    }
    
    public void processQuotaIncreaseAndNotify(PostProcessQuotaChangeDto dto){
        var quotaStateChangeNotifyDto = postProcessingOrchestratorConverter.toQuotaStateChangeNotifyDto(dto.userSeq(), dto.actorUserSeq(), dto.occurredDatetime());
        quotaPostProcessingService.processQuotaIncreaseAndNotify(quotaStateChangeNotifyDto);
    }
    
    public void processQuotaDecreaseAndNotify(PostProcessQuotaChangeDto dto){
        var quotaStateChangeNotifyDto = postProcessingOrchestratorConverter.toQuotaStateChangeNotifyDto(dto.userSeq(), dto.actorUserSeq(), dto.occurredDatetime());
        quotaPostProcessingService.processQuotaDecreaseAndNotify(quotaStateChangeNotifyDto);
    }
    
    public void processMoveAndNotify(PostProcessMoveDto dto){
        
        var resourceSeqList = dto.resourceSeqList();
        var resourceType = dto.resourceType();
        
        if(resourceSeqList.isEmpty()){
            return;
        }
        
        if (resourceType.isFile() && resourceSeqList.size() != 1) {
            throw new IllegalArgumentException("FILE type must provide exactly 1 resourceSeq in resourceSeqList");
        }
        
        var userSeq = dto.userSeq();
        var targetUserSeq = dto.targetUserSeq();
        var resourceSeq = dto.resourceSeqList().get(0);
        var totalSizeByte = dto.totalSizeByte();
        
        switch(dto.moveContextType()){
            
            case CROSS_USER -> {
                
                if(totalSizeByte > 0) {
                    quotaService.moveQuota(totalSizeByte, userSeq, targetUserSeq, dto.actorUserSeq());
                    var quotaStateTransferDto = postProcessingOrchestratorConverter.toQuotaStateTransferDto(dto);
                    quotaPostProcessingService.processQuotaMoveAndNotify(quotaStateTransferDto);
                }
                
                var unshareLookupNotifyDto = postProcessingOrchestratorConverter.toUnshareLookupNotifyDto(dto, resolveTenantName(dto.tenantSeq()));
                sharePostProcessingService.processUnsharedByMoveAndNotify(unshareLookupNotifyDto);
                resourceCleanupService.deleteSpecificShare(resourceType, resourceSeqList, resourceSeq, targetUserSeq, userSeq);
            }
            case INTRA_USER -> {
                
                switch(dto.shareCleanupAction()){
                    case DELETE_RELATED_DATA -> {
                        resourceCleanupService.deleteRelatedDataExcludingOwner(resourceType, resourceSeqList, resourceSeq, userSeq, userSeq);
                    }
                    case DELETE_SPECIFIC_SHARE -> {
                        var unshareLookupNotifyDto = postProcessingOrchestratorConverter.toUnshareLookupNotifyDto(dto, resolveTenantName(dto.tenantSeq()));
                        sharePostProcessingService.processUnsharedByMoveAndNotify(unshareLookupNotifyDto);
                        resourceCleanupService.deleteSpecificShare(resourceType, resourceSeqList, resourceSeq, userSeq, userSeq);
                    }
                    case NONE -> {}
                }
            }
        }
    }
    
    public void processTrashAndNotify(PostProcessTrashDto dto){
        
        var quotaStateChangeNotifyDto = postProcessingOrchestratorConverter.toQuotaStateChangeNotifyDto(dto);
        var deletedUnshareNotifyDto = postProcessingOrchestratorConverter.toDeletedUnshareNotifyDto(dto, resolveTenantName(dto.tenantSeq()));
        
        quotaPostProcessingService.processQuotaDecreaseAndNotify(quotaStateChangeNotifyDto);
        sharePostProcessingService.processDeletedUnsharedAndNotify(deletedUnshareNotifyDto);
    }
    
    public void processSharedAndNotify(PostProcessShareDto dto){
        var tenantName = resolveTenantName(dto.tenantSeq());
        var shareNotifyDto = postProcessingOrchestratorConverter.toShareNotifyDto(dto, tenantName);
        sharePostProcessingService.processSharedAndNotify(shareNotifyDto);
    }

    public void processUnsharedAndNotify(PostProcessUnshareDto dto){
        var tenantName = resolveTenantName(dto.tenantSeq());
        var unshareNotifyDto = postProcessingOrchestratorConverter.toUnshareNotifyDto(dto, tenantName);
        sharePostProcessingService.processUnsharedAndNotify(unshareNotifyDto);
    }

    public void processShareTargetChangedAndNotify(PostProcessShareTargetChangeDto dto){
        var tenantName = resolveTenantName(dto.tenantSeq());
        var shareTargetChangeNotifyDto = postProcessingOrchestratorConverter.toShareTargetChangeNotifyDto(dto, tenantName);
        sharePostProcessingService.processShareTargetChangedAndNotify(shareTargetChangeNotifyDto);
    }
    
    
}

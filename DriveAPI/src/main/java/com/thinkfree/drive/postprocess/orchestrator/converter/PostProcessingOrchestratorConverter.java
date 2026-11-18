package com.thinkfree.drive.postprocess.orchestrator.converter;

import com.thinkfree.drive.postprocess.orchestrator.dto.*;
import com.thinkfree.drive.postprocess.quota.dto.QuotaStateChangeNotifyDto;
import com.thinkfree.drive.postprocess.quota.dto.QuotaStateTransferDto;
import com.thinkfree.drive.postprocess.share.service.dto.*;
import com.thinkfree.drive.postprocess.upload.dto.SharingUploadNotifyDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

@Mapper(componentModel = "spring")
public interface PostProcessingOrchestratorConverter {

    SharingUploadNotifyDto toSharingUploadNotifyDto(Long parentFolderSeq, Long resourceSeq, String folderName, String fileName, Integer userSeq, String userName, Integer actorUserSeq, Instant occurredDatetime, Boolean isDelete);
    QuotaStateChangeNotifyDto toQuotaStateChangeNotifyDto(Integer userSeq,  int actorUserSeq, Instant occurredDatetime);
    QuotaStateTransferDto toQuotaStateTransferDto(PostProcessMoveDto dto);

    @Mapping(target = "tenantSeq", source = "dto.tenantSeq")
    @Mapping(target = "tenantName", source = "tenantName")
    UnshareLookupNotifyDto toUnshareLookupNotifyDto(PostProcessMoveDto dto, String tenantName);

    QuotaStateChangeNotifyDto toQuotaStateChangeNotifyDto(PostProcessTrashDto dto);

    @Mapping(target = "tenantSeq", source = "dto.tenantSeq")
    @Mapping(target = "tenantName", source = "tenantName")
    DeletedUnshareNotifyDto toDeletedUnshareNotifyDto(PostProcessTrashDto dto, String tenantName);

    @Mapping(target = "tenantSeq", source = "dto.tenantSeq")
    @Mapping(target = "tenantName", source = "tenantName")
    ShareNotifyDto toShareNotifyDto(PostProcessShareDto dto, String tenantName);

    @Mapping(target = "tenantSeq", source = "dto.tenantSeq")
    @Mapping(target = "tenantName", source = "tenantName")
    UnshareNotifyDto toUnshareNotifyDto(PostProcessUnshareDto dto, String tenantName);

    @Mapping(target = "tenantSeq", source = "dto.tenantSeq")
    @Mapping(target = "tenantName", source = "tenantName")
    ShareTargetChangeNotifyDto toShareTargetChangeNotifyDto(PostProcessShareTargetChangeDto dto, String tenantName);

}

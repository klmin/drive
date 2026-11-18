package com.thinkfree.drive.application.share.converter;

import com.thinkfree.drive.application.share.command.ShareTargetItemCommand;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.share.target.dto.ShareTargetSaveDto;
import com.thinkfree.drive.share.target.dto.ShareTargetUpsertDto;
import com.thinkfree.drive.share.target.dto.ShareTargetsReplaceDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 공유 대상 교체 Command → 도메인 Dto 변환 전담 — 구 api.v1 ShareTargetRequestConverter의
 * upsert/replace 방향 매핑을 그대로 인계 (canDelete=false 상수 포함, 웹 계약 동일).
 */
@Mapper(componentModel = "spring")
public interface ShareTargetCommandConverter {

    @Mapping(target = "userSeq", source = "item.userSeq")
    @Mapping(target = "canView", source = "item.canView")
    @Mapping(target = "canEdit", source = "item.canEdit")
    @Mapping(target = "canDownload", source = "item.canDownload")
    @Mapping(target = "canUpload", source = "item.canUpload")
    @Mapping(target = "canDelete", constant = "false")
    @Mapping(target = "canShare", source = "item.canShare")
    ShareTargetUpsertDto toShareTargetUpsertDto(ShareTargetItemCommand item, Long shareSeq, Long resourceSeq, Integer sharedByUserSeq);

    default List<ShareTargetUpsertDto> toShareTargetUpsertDtoList(List<ShareTargetItemCommand> items, Long shareSeq, Long resourceSeq, Integer userSeq) {
        return items.stream()
                    .map(item -> toShareTargetUpsertDto(item, shareSeq, resourceSeq, userSeq))
                    .toList();
    }

    ShareTargetsReplaceDto toShareTargetsReplaceDto(Long shareSeq, Integer sharedByUserSeq, ShareType shareType, ResourceType resourceType, Long resourceSeq, List<ShareTargetUpsertDto> targets);

    @Mapping(target = "userSeq", source = "item.userSeq")
    @Mapping(target = "canView", source = "item.canView")
    @Mapping(target = "canEdit", source = "item.canEdit")
    @Mapping(target = "canDownload", source = "item.canDownload")
    @Mapping(target = "canUpload", source = "item.canUpload")
    @Mapping(target = "canDelete", constant = "false")
    @Mapping(target = "canShare", source = "item.canShare")
    ShareTargetSaveDto toShareTargetSaveDto(ShareTargetItemCommand item, Long resourceSeq, Integer sharedByUserSeq);

    default List<ShareTargetSaveDto> toShareTargetSaveDtoList(List<ShareTargetItemCommand> items, Long resourceSeq, Integer userSeq) {
        return items.stream()
                    .map(item -> toShareTargetSaveDto(item, resourceSeq, userSeq))
                    .toList();
    }

}

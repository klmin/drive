package com.thinkfree.drive.application.share.converter;

import com.thinkfree.drive.application.share.command.ShareSaveCommand;
import com.thinkfree.drive.application.share.command.ShareUpdateCommand;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.share.dto.ShareSaveDto;
import com.thinkfree.drive.share.dto.ShareUpdateDto;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.share.target.dto.ShareTargetSaveDto;
import com.thinkfree.drive.share.target.dto.ShareTargetsReplaceDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 공유 등록/수정 Command → 도메인 Dto 변환 전담 — 구 api.v1 ShareRequestConverter의
 * 도메인 방향 매핑을 그대로 인계 (canDelete=false 상수 포함, 웹 계약 동일).
 */
@Mapper(componentModel = "spring")
public interface ShareCommandConverter {

    // targets는 이름이 command.targets와 겹쳐 명시 지정 필수 — 파라미터(식별자 채워진 SaveDto 목록)가 소스다.
    // 미지정 시 MapStruct가 command.targets를 자동 변환해 resourceSeq/sharedByUserSeq 없는 행이 들어간다(실측).
    @Mapping(target = "targets", source = "targets")
    @Mapping(target = "resourceSeq", source = "command.resourceSeq")
    @Mapping(target = "resourceType", source = "resourceType")
    @Mapping(target = "shareType", source = "command.shareType")
    @Mapping(target = "canView", source = "command.canView")
    @Mapping(target = "canEdit", source = "command.canEdit")
    @Mapping(target = "canDownload", source = "command.canDownload")
    @Mapping(target = "canUpload", source = "command.canUpload")
    @Mapping(target = "canDelete", constant = "false")
    @Mapping(target = "canShare", source = "command.canShare")
    ShareSaveDto toShareSaveDto(ShareSaveCommand command, int userSeq, ResourceType resourceType, List<ShareTargetSaveDto> targets);

    @Mapping(target = "shareType", source = "command.shareType")
    @Mapping(target = "canView", source = "command.canView")
    @Mapping(target = "canEdit", source = "command.canEdit")
    @Mapping(target = "canDownload", source = "command.canDownload")
    @Mapping(target = "canUpload", source = "command.canUpload")
    @Mapping(target = "canDelete", constant = "false")
    @Mapping(target = "canShare", source = "command.canShare")
    @Mapping(target = "shareSeq", source = "shareSeq")
    @Mapping(target = "resourceType", source = "resourceType")
    @Mapping(target = "resourceSeq", source = "resourceSeq")
    ShareUpdateDto toShareUpdateDto(ShareUpdateCommand command, ShareType previousShareType, Long shareSeq, int userSeq, Integer ownerTenantSeq, ResourceType resourceType, Long resourceSeq, String resourceName, ShareTargetsReplaceDto targetsReplaceDto);

}

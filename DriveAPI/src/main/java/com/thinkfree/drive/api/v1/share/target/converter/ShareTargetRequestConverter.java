package com.thinkfree.drive.api.v1.share.target.converter;

import com.thinkfree.drive.api.v1.share.target.request.ShareTargetRequestItem;
import com.thinkfree.drive.api.v1.share.target.request.ShareTargetUpsertRequest;
import com.thinkfree.drive.application.share.command.ShareTargetItemCommand;
import com.thinkfree.drive.application.share.command.ShareTargetReplaceCommand;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 웹 요청(축약 필드) → application Command 변환 전담.
 * 도메인 Dto(save/upsert/replace) 생성은 application의 ShareTargetCommandConverter가 담당한다.
 */
@Mapper(componentModel = "spring")
public interface ShareTargetRequestConverter {

    @Mapping(target = "userSeq", source = "us")
    @Mapping(target = "canView", source = "cv")
    @Mapping(target = "canEdit", source = "ce")
    @Mapping(target = "canDownload", source = "cd")
    @Mapping(target = "canUpload", source = "cu")
    @Mapping(target = "canShare", source = "cs")
    @Mapping(target = "sendEmail", source = "se")
    ShareTargetItemCommand toItemCommand(ShareTargetRequestItem item);

    @Mapping(target = "sharedByUserSeq", source = "ss")
    @Mapping(target = "targets", source = "ts")
    ShareTargetReplaceCommand toReplaceCommand(ShareTargetUpsertRequest request);

}

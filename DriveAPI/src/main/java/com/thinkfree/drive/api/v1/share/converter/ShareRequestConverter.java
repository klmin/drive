package com.thinkfree.drive.api.v1.share.converter;

import com.thinkfree.drive.api.v1.share.request.SharePathRequest;
import com.thinkfree.drive.api.v1.share.request.ShareSaveRequest;
import com.thinkfree.drive.api.v1.share.request.ShareTypesRequest;
import com.thinkfree.drive.api.v1.share.request.ShareUpdateRequest;
import com.thinkfree.drive.api.v1.share.request.ShareUserSearchRequest;
import com.thinkfree.drive.api.v1.share.target.converter.ShareTargetRequestConverter;
import com.thinkfree.drive.application.share.command.ShareDetailCommand;
import com.thinkfree.drive.application.share.command.ShareSaveCommand;
import com.thinkfree.drive.application.share.command.ShareTypesCommand;
import com.thinkfree.drive.application.share.command.ShareUpdateCommand;
import com.thinkfree.drive.application.share.command.ShareUserSearchCommand;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 웹 요청(축약 필드) → application Command 변환 전담.
 * 도메인 Dto 생성은 application의 ShareCommandConverter가 담당한다.
 * 대상 항목(us/cv/...) 변환은 ShareTargetRequestConverter.toItemCommand 재사용(uses).
 */
@Mapper(componentModel = "spring", uses = ShareTargetRequestConverter.class)
public interface ShareRequestConverter {

    @Mapping(target = "resourceSeq", source = "rs")
    @Mapping(target = "shareType", source = "st")
    @Mapping(target = "canView", source = "cv")
    @Mapping(target = "canEdit", source = "ce")
    @Mapping(target = "canDownload", source = "cd")
    @Mapping(target = "canUpload", source = "cu")
    @Mapping(target = "canShare", source = "cs")
    @Mapping(target = "targets", source = "ts")
    ShareSaveCommand toSaveCommand(ShareSaveRequest request);

    @Mapping(target = "shareType", source = "st")
    @Mapping(target = "canView", source = "cv")
    @Mapping(target = "canEdit", source = "ce")
    @Mapping(target = "canDownload", source = "cd")
    @Mapping(target = "canUpload", source = "cu")
    @Mapping(target = "canShare", source = "cs")
    @Mapping(target = "targets", source = "ts")
    ShareUpdateCommand toUpdateCommand(ShareUpdateRequest request);

    @Mapping(target = "sharedByUserSeq", source = "ss")
    ShareDetailCommand toDetailCommand(SharePathRequest request);

    @Mapping(target = "searchKeyword", source = "sk")
    @Mapping(target = "sharedByUserSeq", source = "ss")
    ShareUserSearchCommand toUserSearchCommand(ShareUserSearchRequest request);

    @Mapping(target = "sharedByUserSeq", source = "ss")
    ShareTypesCommand toTypesCommand(ShareTypesRequest request);

}

package com.thinkfree.drive.application.share.command;

import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.application.share.validation.ShareValidationRequest;

import java.util.List;

/**
 * 공유 수정 커맨드 — 웹 ShareUpdateRequest(st/cv/...)의 application 등가물.
 * 검증기(ShareWriteValidator)가 요구하는 ShareValidationRequest 계약을 레코드 암묵 접근자
 * (targets()는 공변 반환)로 그대로 충족한다.
 */
public record ShareUpdateCommand
(
    ShareType shareType,
    Boolean canView,
    Boolean canEdit,
    Boolean canDownload,
    Boolean canUpload,
    Boolean canShare,
    List<ShareTargetItemCommand> targets
)
    implements ShareValidationRequest
{
}

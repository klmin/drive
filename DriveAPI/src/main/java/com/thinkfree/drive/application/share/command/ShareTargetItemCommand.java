package com.thinkfree.drive.application.share.command;

import com.thinkfree.drive.application.share.validation.ShareTargetValidationItem;

/**
 * 공유 대상 1건 (교체 커맨드 항목) — 웹 ShareTargetRequestItem(us/cv/...)의 application 등가물.
 * 검증기(ShareWriteValidator)가 요구하는 ShareTargetValidationItem 계약을 그대로 구현한다.
 */
public record ShareTargetItemCommand
(
    Integer userSeq,
    Boolean canView,
    Boolean canEdit,
    Boolean canDownload,
    Boolean canUpload,
    Boolean canShare,
    Boolean sendEmail
)
    implements ShareTargetValidationItem
{
}

package com.thinkfree.drive.api.v1.share.target.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.application.share.validation.ShareTargetValidationItem;
import jakarta.validation.constraints.NotNull;

/**
 * 공유 대상 1건 요청 (등록/수정 공통). 유저 + 권한 플래그 + 메일 발송 여부.
 */
public record ShareTargetRequestItem
(
    @Schema(title = "User ID", description = "Numeric Drive userSeq of the target account, returned by the user list.")
    @NotNull
    Integer us,

    @Schema(title = "Allow viewing", description = "Allow recipients to view the shared item when true.")
    @NotNull
    Boolean cv,

    @Schema(title = "Allow editing", description = "Allow recipients to edit the shared item when true.")
    @NotNull
    Boolean ce,

    @Schema(title = "Allow downloading", description = "Allow recipients to download the shared item when true.")
    @NotNull
    Boolean cd,

    @Schema(title = "Allow uploading", description = "Allow recipients to upload files to the shared folder when true.")
    @NotNull
    Boolean cu,

    @Schema(title = "Allow resharing", description = "Allow recipients to share the item with other people when true.")
    @NotNull
    Boolean cs,

    @Schema(title = "Send notification email", description = "Send an email notification for this operation when true.")
    Boolean se
)
    implements ShareTargetValidationItem
{
    @Override
    public Integer userSeq() {
        return us;
    }

    @Override
    public Boolean canView() {
        return cv;
    }

    @Override
    public Boolean canEdit() {
        return ce;
    }

    @Override
    public Boolean canDownload() {
        return cd;
    }

    @Override
    public Boolean canUpload() {
        return cu;
    }

    @Override
    public Boolean canShare() {
        return cs;
    }

    @Override
    public Boolean sendEmail() {
        return se;
    }
}

package com.thinkfree.drive.api.v1.share.target.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

import java.util.List;

public record ShareTargetUpsertRequest
(

    @Schema(title = "Shared-by user ID", description = "User ID of the Drive that holds the item: sharedByUserSeq from the shared, starred, recent or pinned-folder list, or userSeq from the file and folder list or detail (not ownerSeq). Leave unset for items in your own Drive.")
    Integer ss,

    // 링크(ORG/PUBLIC) 유형은 대상 없이(전체 삭제) 허용 → @NotEmpty 제거. SPECIFIC_USERS 필수 여부는 서비스 검증에서 처리.
    @Schema(title = "Share recipients", description = "Complete list of recipients and their permissions. It replaces the current recipients: anyone left out is removed from the list, and an empty or missing list removes all recipients.")
    @Valid
    List<ShareTargetRequestItem> ts
)
{
}

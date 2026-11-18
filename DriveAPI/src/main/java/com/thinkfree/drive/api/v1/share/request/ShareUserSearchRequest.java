package com.thinkfree.drive.api.v1.share.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.permission.aspect.PermissionAwareShare;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PathVariable;

public record ShareUserSearchRequest(
    @Schema(title = "Search keyword", description = "Text used to filter the matching entries.")
    @NotBlank(message = "검색란(이메일 or 계정명)은 필수입니다.")
    String sk,

    @Schema(title = "Share ID", description = "Numeric shareSeq returned by the share list. This identifies the sharing record, not the file.")
    @PathVariable
    Long shareSeq,

    @Schema(title = "Shared-by user ID", description = "User ID of the Drive that holds the item: sharedByUserSeq from the shared, starred, recent or pinned-folder list, or userSeq from the file and folder list or detail (not ownerSeq). Leave unset for items in your own Drive.")
    Integer ss
) implements PermissionAwareShare {
    @Override
    public Integer sharedByUserSeq() {
        return ss;
    }
}

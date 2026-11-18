package com.thinkfree.drive.api.v1.resource.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 잠금/해제 요청 본문 — 대상 리소스는 path 의 resourceSeq 로만 받는다.
 * {@code PermissionAwareResource} 를 구현하지 않아야 권한 aspect 가 어노테이션의 {@code #resourceSeq}(path)·{@code #request.ss} 로 검사한다
 * (구현하면 본문 값으로 검사해 path 리소스와 어긋난다).
 */
public record ResourceLockRequest(
        @Schema(title = "Shared-by user ID", description = "User ID of the Drive that holds the item: sharedByUserSeq from the shared, starred, recent or pinned-folder list, or userSeq from the file and folder list or detail (not ownerSeq). Leave unset for items in your own Drive.")
        Integer ss
) {
}

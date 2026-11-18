package com.thinkfree.drive.api.page.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.resource.enums.ResourceType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CursorPagingRequest(
        @Schema(title = "Next-page cursor value", description = "Copy nextCursorValue from the previous response, together with ci and ct. Leave unset for the first page.")
        Object cv, // cursorValue : nullable, 첫 페이지면 null
        @Schema(title = "Next-page cursor ID", description = "Copy nextCursor from the previous response. Leave unset for the first page.")
        Long ci, // cursorId : nullable, 첫 페이지면 null
        @Schema(title = "Next-page cursor type", description = "Copy nextCursorType from the previous response together with ci and cv. Leave unset for the first page.")
        ResourceType ct,
        @Schema(title = "Page size", description = "Number of entries to return per page. Enter an integer of at least 1.")
        @NotNull
        @Min(1)
        Integer ps // pageSize
) {
}

package com.thinkfree.drive.api.v1.trash.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.common.search.enums.FileType;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.trash.enums.TrashSortField;
import org.hibernate.validator.constraints.Length;

import java.time.LocalDate;

public record TrashListRequest
(
        @Schema(title = "Resource type", description = "Choose FILE or FOLDER to filter the list. Leave unset to include both.")
        ResourceType rt,
        @Schema(title = "Deleted by account email", description = "Exact sign-in account ID of the user who deleted the item.")
        @Length(max=254)
        String di,
        @Schema(title = "Deleted from", description = "Start of the deletion-date range, in YYYY-MM-DD format. Send ed as well.")
        LocalDate sd,
        @Schema(title = "Deleted through", description = "End of the deletion-date range, in YYYY-MM-DD format. Send sd as well.")
        LocalDate ed,
        @Schema(title = "File category", description = "Filter by document, spreadsheet, presentation, PDF, image or note.")
        FileType ft,
        @Schema(title = "Sort field", description = "Field used to sort the result. Send so to choose ascending or descending order.")
        TrashSortField sb,
        @Schema(title = "Sort order", description = "ASC sorts in ascending order; DESC sorts in descending order. Send sb to choose the field.")
        SortOrder so
)
{
}

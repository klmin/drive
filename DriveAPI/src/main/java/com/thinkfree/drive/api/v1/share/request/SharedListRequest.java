package com.thinkfree.drive.api.v1.share.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.common.search.enums.FileType;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.share.target.enums.ShareTargetSortField;
import org.hibernate.validator.constraints.Length;

import java.time.LocalDate;

public record SharedListRequest
(

        @Schema(title = "Resource type", description = "Choose FILE or FOLDER to filter the list. Leave unset to include both.")
        ResourceType rt,
        @Schema(title = "File category", description = "Filter by document, spreadsheet, presentation, PDF, image or note.")
        FileType ft,
        @Schema(title = "Owner account email", description = "Filter by the owner's sign-in account ID (email address).")
        @Length(max=254)
        String oi,
        @Schema(title = "Modified from", description = "Start of the modified-date range, in YYYY-MM-DD format. Send ed as well.")
        LocalDate sd,
        @Schema(title = "Modified through", description = "End of the modified-date range, in YYYY-MM-DD format. Send sd as well.")
        LocalDate ed,
        @Schema(title = "Sort field", description = "Field used to sort the result. Send so to choose ascending or descending order.")
        ShareTargetSortField sb,
        @Schema(title = "Sort order", description = "ASC sorts in ascending order; DESC sorts in descending order. Send sb to choose the field.")
        SortOrder so
)
{
}

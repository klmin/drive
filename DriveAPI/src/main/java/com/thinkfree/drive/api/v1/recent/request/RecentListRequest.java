package com.thinkfree.drive.api.v1.recent.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.common.search.enums.FileType;
import com.thinkfree.drive.resource.enums.ResourceType;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.Length;

import java.time.LocalDate;

public record RecentListRequest
(
        @Schema(title = "Resource type", description = "Choose FILE or FOLDER to filter the list. Leave unset to include both.")
        ResourceType rt,
        @Schema(title = "Owner account email", description = "Filter by the owner's sign-in account ID (email address).")
        @Length(max=254)
        String oi,
        @Schema(title = "Last recorded from", description = "Start of the range of dates the item was last recorded as recent, in YYYY-MM-DD format. Drive web records an item when you open it; API callers record it with Record a recent item. Send ed as well.")
        LocalDate sd,
        @Schema(title = "Last recorded through", description = "End of the range of dates the item was last recorded as recent, in YYYY-MM-DD format. Send sd as well.")
        LocalDate ed,
        @Schema(title = "File category", description = "Filter by document, spreadsheet, presentation, PDF, image or note.")
        FileType ft
)
{
}

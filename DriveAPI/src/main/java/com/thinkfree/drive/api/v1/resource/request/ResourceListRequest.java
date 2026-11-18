package com.thinkfree.drive.api.v1.resource.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.common.search.enums.FileType;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.resource.enums.ResourceSearchType;
import com.thinkfree.drive.resource.enums.ResourceSortField;
import com.thinkfree.drive.resource.enums.ResourceType;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;

import java.time.LocalDate;

public record ResourceListRequest
(
    @Schema(title = "Shared-by user ID", description = "Leave unset for your own Drive. For a folder shared with you, use its sharedByUserSeq from the shared list or userSeq from the file and folder list (not ownerSeq). Not used when st is ALL; leave it unset there.")
    Integer ss,
    @Schema(title = "Name search keyword", description = "Search by resource name when st is ALL. The MY listing does not apply this keyword.")
    String sk,
    @Schema(title = "Parent folder ID", description = "Folder resourceSeq to list when st is MY. Leave blank for your Drive root; 0 does not mean root. For a shared folder, enter its folder ID and ss.")
    Long pfs,
    @Schema(title = "Resource type", description = "Choose FILE or FOLDER to filter the list. Leave unset to include both.")
    ResourceType rt,
    @Schema(title = "List scope", description = "MY lists one user's Drive; ALL searches all resources visible to you. Use MY with ss and pfs unset to list your own root.")
    @NotNull
    ResourceSearchType st,
    @Schema(title = "Owner account email", description = "Filter by the owner's sign-in account ID (email address).")
    @Length(max=254)
    String oi,
    @Schema(title = "Modified from", description = "Start of the modified-date range, in YYYY-MM-DD format. Send ed as well.")
    LocalDate sd,
    @Schema(title = "Modified through", description = "End of the modified-date range, in YYYY-MM-DD format. Send sd as well.")
    LocalDate ed,
    @Schema(title = "File category", description = "Filter by document, spreadsheet, presentation, PDF, image or note.")
    FileType ft,
    @Schema(title = "Sort field", description = "Field used to sort the result. Send so to choose ascending or descending order.")
    ResourceSortField sb,
    @Schema(title = "Sort order", description = "ASC sorts in ascending order; DESC sorts in descending order. Send sb to choose the field.")
    SortOrder so
)
{
}

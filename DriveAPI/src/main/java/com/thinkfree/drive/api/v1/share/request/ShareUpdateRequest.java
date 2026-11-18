package com.thinkfree.drive.api.v1.share.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.application.share.validation.ShareTargetValidationItem;
import com.thinkfree.drive.application.share.validation.ShareValidationRequest;
import com.thinkfree.drive.api.v1.share.target.request.ShareTargetRequestItem;
import com.thinkfree.drive.share.enums.ShareType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ShareUpdateRequest
(
    @Schema(title = "Share type", description = "SPECIFIC_USERS shares with selected users; ORG_LINK uses an organization link; PUBLIC_LINK uses a public link.")
    @NotNull
    ShareType st,
    
    @Schema(title = "Allow viewing", description = "Link permission: lets people the link reaches (users in your organization for ORG_LINK, anyone for PUBLIC_LINK) view the item. Required when st is ORG_LINK or PUBLIC_LINK; ignored for SPECIFIC_USERS. Must be true.")
    Boolean cv,
    
    @Schema(title = "Allow editing", description = "Link permission: lets people the link reaches (users in your organization for ORG_LINK, anyone for PUBLIC_LINK) edit the item. Required when st is ORG_LINK or PUBLIC_LINK; ignored for SPECIFIC_USERS.")
    Boolean ce,
    
    @Schema(title = "Allow downloading", description = "Link permission: lets people the link reaches (users in your organization for ORG_LINK, anyone for PUBLIC_LINK) download the item. Required when st is ORG_LINK or PUBLIC_LINK; ignored for SPECIFIC_USERS.")
    Boolean cd,
    
    @Schema(title = "Allow uploading", description = "Link permission: lets people the link reaches (users in your organization for ORG_LINK, anyone for PUBLIC_LINK) upload files to the folder. Required when st is ORG_LINK or PUBLIC_LINK; ignored for SPECIFIC_USERS. Can be true only for folders shared with ORG_LINK.")
    Boolean cu,

    @Schema(title = "Allow resharing", description = "Link permission for resharing. Required when st is ORG_LINK or PUBLIC_LINK; ignored for SPECIFIC_USERS. Links cannot grant resharing, so send false.")
    Boolean cs,
    
    @Schema(title = "Share recipients", description = "Complete list of recipients and their permissions. It replaces the current recipients: anyone left out is removed from the list, and an empty or missing list removes all recipients.")
    @Valid
    List<ShareTargetRequestItem> ts
)
    implements ShareValidationRequest
{
    @Override
    public ShareType shareType() {
        return st;
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
    public List<? extends ShareTargetValidationItem> targets() {
        return ts;
    }
}

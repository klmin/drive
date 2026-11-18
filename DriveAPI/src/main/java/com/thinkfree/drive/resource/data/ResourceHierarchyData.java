package com.thinkfree.drive.resource.data;

import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ResourceHierarchyData {
    private Long resourceSeq;
    private Long parentFolderSeq;
    private ResourceType resourceType;
    private String resourceName;
    private String resourceSaveName;
    private String fullPath;
    private Integer depth;

    public Boolean getIsFolder(){
        return ResourceType.FOLDER == resourceType;
    }
}

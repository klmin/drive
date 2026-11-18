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
public class ResourceTreeData {
    private Long resourceSeq;
    private String resourceName;
    private Long parentFolderSeq;
    private ResourceType resourceType;
}

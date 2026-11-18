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
public class ResourceVersionHierarchyData {
    
    private Long resourceSeq;
    private Long resourceVersionSeq;
    private Integer userSeq;
    private Long parentFolderSeq;
    private ResourceType resourceType;
    private Long sizeByte;
    
    public boolean isFile(){
        return resourceType.isFile();
    }
    
    public boolean hasVersion(){
        return resourceVersionSeq != null;
    }
    
}

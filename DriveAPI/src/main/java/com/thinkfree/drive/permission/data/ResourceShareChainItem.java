package com.thinkfree.drive.permission.data;

import com.thinkfree.drive.share.enums.ShareType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ResourceShareChainItem {
    
    private Integer userSeq;
    private Long resourceSeq;
    private Long ancestorResourceSeq;
    private String resourceName;
    private Long parentFolderSeq;
    private String parentFolderName;
    private String fullPathSeq;
    private String fullPath;
    private Long farthestShareResourceSeq;
    private String farthestResourceName;
    
    private Long shareSeq;
    private ShareType shareType;
    private Long shareTargetSeq;
    
    private Integer depth;
}

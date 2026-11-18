package com.thinkfree.drive.pinnedfolder.data;

import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor // 공개 전체 생성자는 JPQL 생성자 식용(PinnedFolderJpaRepository#findPinnedFolders)
public class PinnedFolderData {
    
    private Long pinnedFolderSeq;
    private Long resourceSeq;
    private Integer sharedByUserSeq;
    private String resourceName;
    private ResourceType resourceType;
}

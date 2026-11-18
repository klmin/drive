package com.thinkfree.drive.share.data;

import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.share.enums.ShareType;
import lombok.*;


/** 공개 전체 생성자는 JPQL 생성자 식용(ShareJpaRepository#findSharedResourceBy*, 5차 패스) — 필드 순서를 바꾸면 JPQL 도 맞출 것 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SharedResourceData {
    private Long shareSeq;
    private Long resourceSeq;
    private Integer userSeq;
    private Integer tenantSeq;
    private ShareType shareType;
    private ResourceType resourceType;
    private String resourceName;
}

package com.thinkfree.drive.share.target.data;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ShareTargetNearestData {
    private Integer userSeq;
    private Integer sharedByUserSeq;
    private Long shareTargetSeq;
    private Long ancestorShareTargetSeq;
    private Integer tenantSeq;
    private Integer ownerTenantSeq;
}

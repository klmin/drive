package com.thinkfree.drive.share.target.data;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareTargetPartitionKeyData {
    private Integer userSeq;
    private Long resourceSeq;
    private Integer sharedByUserSeq;
}

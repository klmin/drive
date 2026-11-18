package com.thinkfree.drive.share.data;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UnsharedResourceItem {
    private Integer userSeq;
    private Long resourceSeq;
}

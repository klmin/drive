package com.thinkfree.drive.share.data;

import lombok.*;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UnsharedResourceData {
    private Integer sharedByUserSeq;
    private List<UnsharedResourceItem> unsharedResources;
}

package com.thinkfree.drive.share.target.data;

import lombok.*;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UnsharedTargetsData {
    private Integer userSeq;
    private List<Long> resourceSeqList;
}

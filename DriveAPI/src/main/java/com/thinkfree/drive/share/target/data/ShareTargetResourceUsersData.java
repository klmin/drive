package com.thinkfree.drive.share.target.data;

import lombok.*;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ShareTargetResourceUsersData {
    private Long resourceSeq;
    private String resourceName;
    private List<Integer> targetUsers;
}

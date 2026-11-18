package com.thinkfree.drive.share.target.data;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor // 공개 전체 생성자는 JPQL 생성자 식용(ShareTargetJpaRepository#TARGET_USER_JOIN_SHARE)
public class ShareTargetUserData {
    private Integer userSeq;
}

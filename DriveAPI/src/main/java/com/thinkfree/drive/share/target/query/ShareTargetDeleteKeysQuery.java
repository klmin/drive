package com.thinkfree.drive.share.target.query;

import com.thinkfree.drive.share.target.key.ShareTargetDeleteKey;
import lombok.Builder;
import lombok.Getter;

import java.util.Collection;

@Builder
@Getter
public class ShareTargetDeleteKeysQuery {
    private Collection<ShareTargetDeleteKey> deleteKeys;
    private Integer sharedByUserSeq;
}

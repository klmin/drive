package com.thinkfree.drive.share.target.context;

import java.util.List;
import java.util.Set;

public record ShareTargetChangeContext
(
    Set<Integer> addedUserSeqSet,
    List<Integer> deletedTargetUserKeys
)
{
}

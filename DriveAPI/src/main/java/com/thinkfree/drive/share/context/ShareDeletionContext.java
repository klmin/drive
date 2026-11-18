package com.thinkfree.drive.share.context;

import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.partition.UserResourceGroup;
import com.thinkfree.drive.share.target.key.ShareTargetDeleteKey;
import com.thinkfree.drive.share.target.result.ShareTargetDeletionResult;

import java.util.List;
import java.util.Set;

public record ShareDeletionContext
(
    boolean deleteDescendants,
    List<PartitionKey> cleanUpPartitionKeys,
    List<ShareTargetDeleteKey> shareTargetDeleteKeys,
    List<UserResourceGroup> cleanUpUserResourceGroups,
    Set<Integer> deleteShareTargetUserSeqSet
)
{
    public static ShareDeletionContext empty() {
        return new ShareDeletionContext(false, List.of(), List.of(), List.of(), Set.of());
    }
    
    public static ShareDeletionContext linkFile(int userSeq, long resourceSeq) {
        return new ShareDeletionContext(false, List.of(new PartitionKey(userSeq, resourceSeq)), List.of(), List.of(), Set.of());
    }
    
    public static ShareDeletionContext linkFolder(List<PartitionKey> cleanUpPartitionKeys) {
        return new ShareDeletionContext(true, cleanUpPartitionKeys, List.of(), List.of(), Set.of());
    }
    
    public static ShareDeletionContext from(ShareTargetDeletionResult result) {
        return new ShareDeletionContext(result.deleteDescendants(), result.cleanUpPartitionKeys(), result.shareTargetDeleteKeys(), result.cleanUpUserResourceGroups(), result.deleteShareTargetUserSeqSet());
    }

}

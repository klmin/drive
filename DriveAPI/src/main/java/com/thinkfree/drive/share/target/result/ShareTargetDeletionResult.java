package com.thinkfree.drive.share.target.result;

import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.partition.UserResourceGroup;
import com.thinkfree.drive.share.target.key.ShareTargetDeleteKey;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record ShareTargetDeletionResult
(
    boolean deleteDescendants,
    List<PartitionKey> cleanUpPartitionKeys,
    List<ShareTargetDeleteKey> shareTargetDeleteKeys,
    List<UserResourceGroup> cleanUpUserResourceGroups
)
{
    public static ShareTargetDeletionResult empty() {
        return new ShareTargetDeletionResult(false, List.of(), List.of(), List.of());
    }
    
    public static ShareTargetDeletionResult targetFile(List<PartitionKey> cleanUpPartitionKeys, List<ShareTargetDeleteKey> shareTargetDeleteKeys) {
        return new ShareTargetDeletionResult(false, cleanUpPartitionKeys, shareTargetDeleteKeys, List.of());
    }
    
    public static ShareTargetDeletionResult targetFolder(List<ShareTargetDeleteKey> shareTargetDeleteKeys, List<UserResourceGroup> cleanUpUserResourceGroups) {
        return new ShareTargetDeletionResult(true, List.of(), shareTargetDeleteKeys, cleanUpUserResourceGroups);
    }
    
    public Set<Integer> deleteShareTargetUserSeqSet() {
        if (shareTargetDeleteKeys == null || shareTargetDeleteKeys.isEmpty()) {
            return Set.of();
        }
        return shareTargetDeleteKeys.stream()
                                    .map(ShareTargetDeleteKey::userSeq)
                                    .collect(Collectors.toSet());
    }
}

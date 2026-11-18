package com.thinkfree.drive.share.result;

import com.thinkfree.drive.partition.PartitionKey;

import java.util.List;
import java.util.Set;

/**
 * 전체공개(PUBLIC_LINK) -> 조직링크(ORG_LINK) 축소 시 링크 접근을 잃는 외부 테넌트 유저의 메타 정리분.
 *
 * @param preserveUserSeqSet 교체 후 target 으로 남아 접근이 유지되는 유저 — 정리에서 제외
 */
public record ShareExternalShrinkResult
(
    Integer sharedByUserSeq,
    Integer ownerTenantSeq,
    List<PartitionKey> cleanUpPartitionKeys,
    boolean deleteDescendants,
    Set<Integer> preserveUserSeqSet
)
{
}

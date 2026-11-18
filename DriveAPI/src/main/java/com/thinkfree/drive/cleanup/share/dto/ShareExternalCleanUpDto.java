package com.thinkfree.drive.cleanup.share.dto;

import com.thinkfree.drive.partition.PartitionKey;

import java.util.List;
import java.util.Set;

/**
 * 전체공개(PUBLIC_LINK) -> 조직링크(ORG_LINK) 축소 시 외부 테넌트 유저 메타(최근/중요/핀) 정리.
 *
 * @param preserveUserSeqSet target 으로 접근이 유지되는 유저 — 정리에서 제외
 */
public record ShareExternalCleanUpDto
(
    Integer sharedByUserSeq,
    Integer ownerTenantSeq,
    List<PartitionKey> partitionKeys,
    boolean deleteDescendants,
    Set<Integer> preserveUserSeqSet
)
{
}

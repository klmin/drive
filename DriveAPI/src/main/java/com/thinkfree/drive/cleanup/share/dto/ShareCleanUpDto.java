package com.thinkfree.drive.cleanup.share.dto;

import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.partition.UserResourceGroup;
import com.thinkfree.drive.share.enums.ShareType;

import java.util.List;
import java.util.Set;

/**
 * 메타(최근/중요/핀) 정리분 운반체. {@code shareType} 이 삭제 "방식"의 라우팅 키라서, 같은 필드도 방식에 따라 의미가 다르다.
 * (DefaultShareCleanUpService.deleteShareCascade 가 소비)
 *
 * <p><b>대상 방식 (shareType = SPECIFIC_USERS)</b> — deleteResult 채널. 접근을 잃는 유저가 열거됨 → 정밀 삭제.
 * <ul>
 * <li>파일: {@code partitionKeys} = [(빠진 대상 userSeq, 공유 리소스 seq)] →
 *     SQL {@code (USER_SEQ, RESOURCE_SEQ) IN (...)}. PartitionKey 의 sharedByUserSeq 칸 = <b>빠진 공유대상(받은 사람)</b>.</li>
 * <li>폴더: {@code userResourceGroups} = [(빠진 대상 userSeq, [본인이 접근 잃는 하위 resourceSeq…])] →
 *     유저별 {@code USER_SEQ = userSeq AND RESOURCE_SEQ IN (...)}. 핀폴더 포함.</li>
 * <li>{@code preserveUserSeqSet} = 미사용(Set.of()). 보존 판정(keepsLinkAccess/상위 대상)이 이미 끝나
 *     "접근을 잃는 유저만" 골라져 들어오기 때문.</li>
 * </ul>
 *
 * <p><b>링크 방식 (shareType = ORG_LINK / PUBLIC_LINK)</b> — linkDeleteResult 채널(링크→specific 전환, 3-2/3-3).
 * 링크 접근자는 열거 불가 → 리소스 범위로 삭제하고 유저는 제외 조건으로 거른다.
 * <ul>
 * <li>{@code partitionKeys} = 정리할 리소스 목록. PartitionKey 의 sharedByUserSeq 칸 = <b>소유자(운반용 더미)</b>, SQL 미사용 — resourceSeq 만 씀.</li>
 * <li>파일: 단건([(소유자, 파일seq)]) → {@code USER_SEQ != 소유자 AND USER_SEQ NOT IN preserve} 로 삭제(핀 없음).</li>
 * <li>폴더: 하위 트리 목록 → 위 조건 + 핀폴더 포함.</li>
 * <li>{@code preserveUserSeqSet} = 교체 후 target 으로 남아 접근이 유지되는 유저 → 삭제 제외.</li>
 * </ul>
 *
 * <p><b>공통</b>
 * @param sharedByUserSeq      항상 <b>소유자</b> (메타 테이블 SHARED_BY_USER_SEQ 파티션 키)
 * @param deleteDescendants    폴더(하위 포함) 정리 여부 — 어느 필드를 소비할지 결정하는 분기 키(false=파일/true=폴더)
 * @param preserveUserSeqSet   메타 정리에서 제외할 유저 — 링크 방식에서만 유효(target 으로 접근 유지), 대상 방식은 Set.of()
 */
public record ShareCleanUpDto
(
    Integer sharedByUserSeq,
    ShareType shareType,
    List<PartitionKey> partitionKeys,
    List<UserResourceGroup> userResourceGroups,
    boolean deleteDescendants,
    Set<Integer> preserveUserSeqSet
)
{
}

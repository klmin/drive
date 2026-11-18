package com.thinkfree.drive.share.result;

import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.partition.UserResourceGroup;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.share.enums.ShareType;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 공유 삭제/수정으로 발생한 정리분 운반체. notify(공유해제 알림)와 cleanup(최근/중요/핀 메타 삭제)이 함께 소비한다.
 * <p>
 * shareType 이 cleanup 방식의 라우팅 키라서, 어느 방식이냐에 따라 같은 필드도 의미가 다르다:
 * <ul>
 * <li><b>대상 방식 (shareType=SPECIFIC_USERS)</b> — 삭제 대상 유저가 열거됨.
 *     파일: cleanUpPartitionKeys = (빠진 대상 userSeq, 공유 리소스) 쌍 /
 *     폴더: deleteDescendants=true + cleanUpUserResourceGroups = 빠진 유저별 접근 상실 하위 리소스 목록.</li>
 * <li><b>링크 방식 (shareType=ORG/PUBLIC_LINK)</b> — 삭제 대상 유저 열거 불가(잠재 접근자 = 조직 전체/전원).
 *     cleanUpPartitionKeys 는 정리할 <b>리소스 범위</b> 운반용(userSeq 칸은 소유자, 의미 없음).
 *     실제 삭제 유저는 쿼리에서 "소유자·preserveUserSeqSet 제외 전원" 조건으로 결정.</li>
 * </ul>
 *
 * @param sharedByUserSeq              공유 소유자 (메타 테이블 파티션 키)
 * @param shareType                    cleanup 방식 라우팅 키 (위 참조)
 * @param cleanUpPartitionKeys         대상 방식=(빠진 유저, 리소스) 쌍 / 링크 방식=정리할 리소스 목록
 * @param cleanUpUserResourceGroups    대상 방식 폴더 케이스 전용 — 유저별 접근 상실 하위 리소스 목록
 * @param deleteDescendants            폴더(하위 포함) 정리 여부 — cleanup 이 소비할 필드를 고르는 분기 키
 * @param resourceSeq / fileName / occurredDatetime  알림(notify) 페이로드용 — delete 엔드포인트 흐름에선 이 결과가 유일한 출처
 * @param deleteShareTargetUserSeqList 이번 작업으로 target 에서 빠진 유저 전원 — unshare 알림 대상.
 *                                     메타 유지 여부(keepsLinkAccess)와 무관하게 빠진 유저는 모두 담긴다. 링크 방식 결과에선 빈 집합.
 * @param preserveUserSeqSet           링크 방식 정리에서 메타를 보존할 유저 = 교체 후 남는 target (target 으로 접근 유지)
 */
public record ShareDeleteResult
(
    Integer sharedByUserSeq,
    ShareType shareType,
    ResourceType resourceType,
    List<PartitionKey> cleanUpPartitionKeys,
    List<UserResourceGroup> cleanUpUserResourceGroups,
    boolean deleteDescendants,
    long resourceSeq,
    String fileName,
    Instant occurredDatetime,
    Set<Integer> deleteShareTargetUserSeqList,
    Set<Integer> preserveUserSeqSet
)
{
}

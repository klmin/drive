package com.thinkfree.drive.share.result;

import java.util.Set;

/**
 * 공유 수정 결과. 정리분 3종은 "누가 접근을 잃는가"의 표현 방식이 달라 분리 운반한다.
 * (대상 교체분=유저 열거형 / 링크 소멸분=리소스 범위+제외 조건형 / 링크 축소분=리소스 범위+테넌트 조건형)
 *
 * @param deleteResult         대상 교체로 빠진 유저 정리분 — 항상 non-null(교체 없으면 빈 컨텍스트 → cleanup no-op).
 *                             shareType=SPECIFIC_USERS 고정(대상 방식 라우팅).
 *                             빠진 유저 전원이 deleteShareTargetUserSeqList(알림용)에 담기고,
 *                             메타 정리 키는 그중 접근을 잃는 유저만 (최종 PUBLIC=없음 / ORG=외부 테넌트만 / SPECIFIC=전원).
 * @param linkDeleteResult     링크 소멸(링크->제한됨 전환) 정리분 — 해당 전환이 아니면 null.
 *                             shareType=이전 링크 유형. 소유자·잔존 target(preserveUserSeqSet) 제외 전원의 메타 삭제.
 * @param externalShrinkResult 링크 축소(전체공개->조직링크 전환) 정리분 — 해당 전환이 아니면 null.
 *                             외부 테넌트 유저(잔존 target 제외)의 메타 삭제. 전용 cascade 로 dispatch.
 */
public record ShareUpdateResult
(
    Integer sharedByUserSeq,
    Set<Integer> addedUserSeqSet,
    ShareDeleteResult deleteResult,
    ShareDeleteResult linkDeleteResult,
    ShareExternalShrinkResult externalShrinkResult
)
{
}

package com.thinkfree.drive.share.result;

/**
 * 공유 해제(delete) 정리분 운반체 — update 의 {@link ShareUpdateResult} 와 동형(단, 전환이 없어 channel 2개).
 * <p>
 * 해제는 공유가 통째로 사라지므로 "누가 접근을 잃는가"를 두 방식으로 나눠 표현한다:
 * <ul>
 * <li><b>targetDeleteResult (항상)</b> — 대상(per-user) 방식, shareType=SPECIFIC_USERS 로 라우팅.
 *     SPECIFIC 공유 = 빠진 대상 전원(상위 target 없는). 링크 공유 = 링크로 접근이 유지되지 않는 target
 *     (ORG_LINK=외부 테넌트 & 상위 target 없는 / PUBLIC_LINK=∅). 해제 알림 대상(빠진 target 전원)도 이 채널이 운반.</li>
 * <li><b>linkDeleteResult (링크 유형만, SPECIFIC 이면 null)</b> — 링크 방식, prev 링크 유형으로 라우팅.
 *     링크 도달 범위(소유자 제외 전원)를 리소스 범위로 sweep. 상위 공유가 있으면 빈 컨텍스트(보존).</li>
 * </ul>
 * 두 채널의 겹침(상위 없는 링크 해제 시 외부 target 이 양쪽에서 지워짐)은 멱등이라 허용한다.
 */
public record ShareUnshareResult
(
    ShareDeleteResult targetDeleteResult,
    ShareDeleteResult linkDeleteResult
)
{
}

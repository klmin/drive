package com.thinkfree.drive.api.v1.superadmin.sharesetting.response;

/**
 * 슈퍼어드민 공유 설정 조회 응답 (7개 필드).
 * <p>
 * 화면 컨트롤 3단계 중첩 구조를 반영한다. 각 단계 값은 실제 상태를 내려주고,
 * 그 단계가 화면에 안 보이는 하위 단계만 null = "렌더하지 않음" 신호.
 *
 * <p>
 * 응답 규칙:
 * <ul>
 *   <li>글로벌 행 없음 → 전 필드 null</li>
 *   <li>개별적용(isEnforced=false) → shareEnabled·접근범위 4개 모두 null</li>
 *   <li>전체적용 + 공유 off(shareEnabled=false) → 접근범위 4개 null</li>
 *   <li>전체적용 + 공유 on(shareEnabled=true) → 접근범위 4개 db값</li>
 * </ul>
 *
 * @param shareSettingSeq      글로벌 공유설정 PK
 * @param isEnforced           적용범위: true=전체적용 / false=테넌트 개별적용
 * @param shareEnabled         공유 기능 사용 여부 (글로벌 share_status, system_config 독립 값)
 * @param externalShareAllowed 외부 공유 허용
 * @param allowSpecificUsers   접근범위 - 제한됨
 * @param allowOrgLink         접근범위 - 조직 내부 공개
 * @param allowPublic          접근범위 - 전체 공개
 */
public record SuperAdminShareSettingResponse(
        Integer shareSettingSeq,
        Boolean isEnforced,
        Boolean shareEnabled,
        Boolean externalShareAllowed,
        Boolean allowSpecificUsers,
        Boolean allowOrgLink,
        Boolean allowPublic
) {
}

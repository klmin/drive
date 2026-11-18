package com.thinkfree.drive.api.v1.admin.sharesetting.response;

/**
 * 테넌트 어드민 공유 설정 조회 응답. (슈퍼어드민과 유사한 약자 필드)
 * <p>
 * 값은 실제 적용(effective) 설정이다: 슈퍼어드민 전체적용(enforce) 중이면 글로벌 설정, 아니면 테넌트 자기 설정.
 *
 * @param isEnforced 슈퍼어드민이 전체 적용 중인지 → true 면 어드민은 변경 불가(폼 잠금), 값은 글로벌 설정
 * @param shareEnabled         공유 기능 사용 여부 (enforce 면 글로벌 share_status, 아니면 테넌트 share_status)
 * @param externalShareAllowed        외부 공유 허용
 * @param allowSpecificUsers        접근범위 - 제한됨
 * @param allowOrgLink        접근범위 - 조직 내부 공개
 * @param allowPublic         접근범위 - 전체 공개
 */
public record AdminShareSettingResponse(
        Boolean isEnforced,
        Boolean shareEnabled,
        Boolean externalShareAllowed,
        Boolean allowSpecificUsers,
        Boolean allowOrgLink,
        Boolean allowPublic
) {
}

package com.thinkfree.drive.application.apikey;

/**
 * API Key 조작이 미치는 범위. 어떤 키를 건드릴 수 있는지를 값으로 표현한다.
 *
 * <p>범위의 출처는 진입점마다 다르다(관리자 경로=Host 해석 테넌트, 사용자 경로=로그인 사용자,
 * 슈퍼관리자 경로=제한 없음). 따라서 usecase 가 TenantContext·SecurityContext 를 직접 읽지 않고,
 * 각 호출부가 자신이 아는 정보로 이 객체를 구성해 전달한다.
 *
 * <p>각 범위는 목록 조회 범위와 정확히 같다 — 목록에 보이는 키가 곧 조작 가능한 키다.
 */
public record ApiKeyScope(Type type, Integer tenantSeq, Integer registerUserSeq) {

    public enum Type {
        /** 지정 테넌트의 ADMIN·USER role 키 */
        TENANT,
        /** 지정 사용자가 발급한 키 */
        REGISTER_USER,
        /** 테넌트·role 무관 전체 */
        ALL
    }

    /** 관리자 — 자기 테넌트의 ADMIN·USER role 키 */
    public static ApiKeyScope tenant(int tenantSeq) {
        return new ApiKeyScope(Type.TENANT, tenantSeq, null);
    }

    /** 사용자 — 본인이 발급한 키 */
    public static ApiKeyScope registerUser(int registerUserSeq) {
        return new ApiKeyScope(Type.REGISTER_USER, null, registerUserSeq);
    }

    /** 슈퍼관리자 — 전체 */
    public static ApiKeyScope all() {
        return new ApiKeyScope(Type.ALL, null, null);
    }
}

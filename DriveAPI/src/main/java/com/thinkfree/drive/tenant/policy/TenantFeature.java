package com.thinkfree.drive.tenant.policy;

/**
 * 테넌트 유형·사용자 모드에 따라 막힐 수 있는 기능 — 호출부는 기능만 말하고, 어느 유형·모드에서 막는지는 {@link DefaultTenantPolicy} 규칙 표가 정한다.
 */
public enum TenantFeature {
    /** 관리자 라이선스 등록·온라인 인증 */
    LICENSE_MANAGE,
    /** 관리자 일반 설정·office 설정·노트(인텔릭트) 설정 */
    SYSTEM_CONFIG,
    /** 관리자 로그인 설정 저장 + 슈퍼관리자 강제 로그인 설정 적용 여부 */
    LOGIN_SETTING,
    /**
     * 공유 생성·수정·삭제·링크 접근·대상 교체, 공유 조회(공유 문서함·상세·공유 사용자·공유 대상 검색·공유 유형·공유 데이터 존재 여부),
     * 공유 설정(관리자 저장 + 유효 설정 off)
     */
    SHARE,
    /** 사용자 등록(일반·관리자 일괄·OIDC 가입) + 일괄 등록 전 아이디 상태 확인(bulk-status) */
    USER_REGISTER,
    /** 보조 계정 관리 — 사용자 활성화, 삭제 대기 계정 복구·영구 삭제. SINGLE 은 보호 계정 하나뿐이라 대상이 생기지 않는다 */
    SUB_ACCOUNT_MANAGE,
    /** 관리자·사용자 경로 API Key 발급·변경 */
    API_KEY_MANAGE,
    /** 메일 — 관리자 SMTP 설정 저장·발송 테스트, 비밀번호 재설정 링크 메일·링크 토큰 확인, 관리자 CSV 등록 안내 메일(공유 알림 메일은 SHARE 로 막힘) */
    MAIL,
    /** 관리자 접근 제한 — 허용 IP·허용 국가 저장, 접근 제한 사용 여부 저장(조회는 막지 않음) */
    ACCESS_RESTRICTION,
    /** 관리자 MFA 사용 여부 저장·사용자 MFA 초기화(조회는 막지 않음) — 사용 여부를 켤 수 없어 초기화할 2FA 도 생기지 않는다 */
    MFA_SETTING,
    /** 스토리지 설정 조회의 접속 정보(accessKey·secretKey·버킷·리전·엔드포인트) 보기 — 허용되지 않으면 조회 응답에서 마스킹 */
    STORAGE_INFO_VIEW,
    /** 시스템 정보 조회의 도메인 주소 보기 — 허용되지 않으면 조회 응답에서 마스킹 */
    SYSTEM_INFO_VIEW,
    /** SMTP 설정 조회의 접속 정보(클라이언트 이름·호스트·계정·비밀번호·OAuth 토큰·발신 주소·테스트 수신 주소) 보기 — 허용되지 않으면 조회 응답에서 마스킹 */
    SMTP_INFO_VIEW,
    /** 관리자·사용자 경로 API Key 목록의 키 값(전체 '*') 보기 — 허용되지 않으면 화면이 블러로 표시하는 값으로 내려준다 */
    API_KEY_INFO_VIEW,
    /** 관리자 office 설정 조회의 접속 정보(TFO 주소·티켓 발급 토큰·저장소 어댑터 이름·요청 서명키) 보기 — 허용되지 않으면 조회 응답에서 마스킹 */
    OFFICE_INFO_VIEW
}

package com.thinkfree.drive.share.setting.data;

import java.time.Instant;

/**
 * tb_share_setting 단일 행 읽기 모델.
 * tenantSeq == null 이면 슈퍼어드민(글로벌) 행, 그 외는 테넌트별 행.
 * Y/N char 컬럼은 원문 그대로 보관하고 boolean 헬퍼로 해석한다.
 * (2단계 JdbcClient 이관 시 record 전환 — 접근자는 {@code shareSettingSeq()} 형)
 */
public record ShareSettingData(
        Integer shareSettingSeq,
        Integer tenantSeq,
        String isEnforced,               // Y/N (글로벌 행 전용: Y=전체적용)
        String isExternalShareAllowed,   // Y/N
        String isSpecificUsersAllowed,   // Y/N
        String isOrgLinkAllowed,         // Y/N
        String isPublicAllowed,          // Y/N
        Integer registerUserSeq,
        Instant registerDate,
        Integer updateUserSeq,
        Instant updateDate
) {

    /** JPQL 생성자 식용 — 엔티티의 Boolean(YesNoConverter) 플래그를 Y/N 원문으로 바꿔 담는다(null 은 null). */
    public ShareSettingData(Integer shareSettingSeq, Integer tenantSeq,
                            Boolean isEnforced, Boolean isExternalShareAllowed, Boolean isSpecificUsersAllowed, Boolean isOrgLinkAllowed, Boolean isPublicAllowed,
                            Integer registerUserSeq, Instant registerDate, Integer updateUserSeq, Instant updateDate) {
        this(shareSettingSeq, tenantSeq,
                yn(isEnforced), yn(isExternalShareAllowed), yn(isSpecificUsersAllowed),
                yn(isOrgLinkAllowed), yn(isPublicAllowed),
                registerUserSeq, registerDate, updateUserSeq, updateDate);
    }

    public boolean isGlobal() {
        return tenantSeq == null;
    }

    public boolean enforced() {
        return isYes(isEnforced);
    }

    public boolean externalShareAllowed() {
        return isYes(isExternalShareAllowed);
    }

    public boolean specificUsersAllowed() {
        return isYes(isSpecificUsersAllowed);
    }

    public boolean orgLinkAllowed() {
        return isYes(isOrgLinkAllowed);
    }

    public boolean publicAllowed() {
        return isYes(isPublicAllowed);
    }

    private static String yn(Boolean value) {
        return value == null ? null : (value ? "Y" : "N");
    }

    private static boolean isYes(String value) {
        return "Y".equalsIgnoreCase(value);
    }
}

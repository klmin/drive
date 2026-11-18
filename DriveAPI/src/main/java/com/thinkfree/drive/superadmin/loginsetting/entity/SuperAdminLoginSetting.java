package com.thinkfree.drive.superadmin.loginsetting.entity;

import com.thinkfree.drive.api.v1.auth.enums.LoginType;
import com.thinkfree.drive.global.jpa.audit.BaseAuditEntity;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.type.YesNoConverter;

/**
 * tb_super_admin_login_setting — 슈퍼어드민 로그인 설정(단일 행, PK = {@link #SINGLETON_SEQ} 할당형, CHECK singleton).
 * 조회는 JPQL 생성자 식({@code DefaultSuperAdminLoginSettingQueryRepository}), 쓰기는 upsert(JdbcClient)뿐. 감사 4필드는 {@link BaseAuditEntity}. 변경 메서드 없음.
 */
@SuperBuilder
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tb_super_admin_login_setting")
public class SuperAdminLoginSetting extends BaseAuditEntity {

    public static final int SINGLETON_SEQ = 1;

    @Id
    private Integer superAdminLoginSettingSeq;
    @Enumerated(EnumType.STRING)
    private LoginType loginType;
    @Convert(converter = YesNoConverter.class)
    private Boolean isEnforced;
}

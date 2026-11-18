package com.thinkfree.drive.global.jooq;

import com.thinkfree.drive.user.enums.DefaultLandingPage;
import com.thinkfree.drive.user.enums.MfaStatus;
import com.thinkfree.drive.user.enums.UserLanguage;
import com.thinkfree.drive.user.enums.UserStatus;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static com.thinkfree.drive.jooq.Tables.TB_SHARE_SETTING;
import static com.thinkfree.drive.jooq.Tables.TB_USER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * jOOQ 도입 설정 확인 — 방언 고정, 생성 코드 타입 매핑(Y/N→Boolean, timestamptz→Instant), 스키마 이름 미출력,
 * JPA 트랜잭션(JpaTransactionManager)의 커넥션을 JdbcClient 와 같이 쓰는지. 시드 = initialize.sql 의 tb_share_setting(tenant 1).
 */
@SpringBootTest
@ActiveProfiles("test")
class JooqSetupIntegrationTest {

    @Autowired
    DSLContext dsl;

    @Autowired
    JdbcClient jdbcClient;

    @Test
    @DisplayName("방언 POSTGRES, 생성 SQL 에 스키마 이름(public) 없음")
    void dialectAndRenderedSql() {
        assertThat(dsl.dialect().family()).isEqualTo(SQLDialect.POSTGRES);

        String sql = dsl.selectFrom(TB_SHARE_SETTING).where(TB_SHARE_SETTING.TENANT_SEQ.eq(1)).getSQL();
        assertThat(sql).contains("\"tb_share_setting\"").doesNotContain("\"public\"");
    }

    @Test
    @DisplayName("Y/N 플래그는 Boolean, timestamptz 는 Instant 로 읽힌다")
    void forcedTypes() {
        var row = dsl.selectFrom(TB_SHARE_SETTING).where(TB_SHARE_SETTING.TENANT_SEQ.eq(1)).fetchOne();

        assertThat(row).isNotNull();
        assertThat(row.getIsEnforced()).isFalse();
        assertThat(row.getIsSpecificUsersAllowed()).isTrue();
        assertThat(row.getRegisterDate()).isNotNull();
    }

    @Test
    @DisplayName("tb_user 의 status·mfa_status 는 enum(name()), user_language·default_landing_page 는 코드 값 enum 으로 읽힌다")
    void userEnumForcedTypes() {
        // 시드 user_seq 2(admin) = ACTIVE·DISABLED·'auto'·'home'
        var row = dsl.selectFrom(TB_USER).where(TB_USER.USER_SEQ.eq(2)).fetchOne();

        assertThat(row).isNotNull();
        assertThat(row.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(row.getMfaStatus()).isEqualTo(MfaStatus.DISABLED);
        assertThat(row.getUserLanguage()).isEqualTo(UserLanguage.AUTO);
        assertThat(row.getDefaultLandingPage()).isEqualTo(DefaultLandingPage.HOME);
        // 조건에 enum 을 그대로 넣으면 name() 문자열로 바인딩된다
        assertThat(dsl.fetchExists(TB_USER, TB_USER.USER_SEQ.eq(2).and(TB_USER.STATUS.eq(UserStatus.ACTIVE)))).isTrue();
    }

    @Test
    @Transactional
    @DisplayName("user_language·default_landing_page 는 name() 이 아니라 코드 값('ko'·'my-drive')으로 저장된다 — JPA 변환기와 같음, 테스트 롤백")
    void userCodeEnumWrite() {
        dsl.update(TB_USER)
                .set(TB_USER.USER_LANGUAGE, UserLanguage.KO)
                .set(TB_USER.DEFAULT_LANDING_PAGE, DefaultLandingPage.MY_DRIVE)
                .where(TB_USER.USER_SEQ.eq(2))
                .execute();

        var raw = jdbcClient.sql("SELECT user_language || '/' || default_landing_page FROM tb_user WHERE user_seq = 2")
                .query(String.class).single();
        assertThat(raw).isEqualTo("ko/my-drive");
    }

    @Test
    @Transactional
    @DisplayName("같은 트랜잭션 안에서 JdbcClient 가 바꾼 값을 jOOQ 가 본다(커넥션 공유) — 테스트 롤백")
    void sharesTransactionWithJdbcClient() {
        // 시드는 'N'. is_enforced 는 테넌트 행에서 'N' 고정(CHECK)이라 is_public_allowed 로 확인
        jdbcClient.sql("UPDATE tb_share_setting SET is_public_allowed = 'Y' WHERE tenant_seq = 1").update();

        Boolean publicAllowed = dsl.select(TB_SHARE_SETTING.IS_PUBLIC_ALLOWED).from(TB_SHARE_SETTING)
                .where(TB_SHARE_SETTING.TENANT_SEQ.eq(1)).fetchOne(TB_SHARE_SETTING.IS_PUBLIC_ALLOWED);

        assertThat(publicAllowed).isTrue();
    }
}

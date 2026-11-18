package com.thinkfree.drive.email.template.repository;

import com.thinkfree.drive.common.RepositoryTest;
import com.thinkfree.drive.email.template.data.EmailTemplateData;
import com.thinkfree.drive.email.template.enums.EmailTemplateKey;
import com.thinkfree.drive.email.template.query.EmailTemplateFindQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EmailTemplate 이관 회귀 — {@link DefaultEmailTemplateQueryRepository}(구 EmailTemplateMapper.xml findByTemplateKeyAndLocale), JPQL 생성자 식({@link com.thinkfree.drive.email.template.data.EmailTemplateData} — 구 XML 과 같은 제목·본문 두 컬럼).
 * 시드와 겹치지 않는 locale_tag(xx-AA / xx-BB) 로 행 3개를 직접 INSERT. 트랜잭션 롤백.
 * 검증 포인트 = 키(enum) × Locale.toLanguageTag 단건의 제목·본문, 같은 키 다른 locale 격리, 없는 조합·null 입력은 null.
 */
@DisplayName("DefaultEmailTemplateQueryRepository — JPA(PostgreSQL Testcontainers)")
class DefaultEmailTemplateQueryRepositoryTest extends RepositoryTest {

    private static final Locale XX_AA = Locale.forLanguageTag("xx-AA");
    private static final Locale XX_BB = Locale.forLanguageTag("xx-BB");

    @Autowired
    private EmailTemplateQueryRepository repository;
    @Autowired
    private JdbcTemplate jdbc;
    @BeforeEach
    void setUp() {
        insert("SHARE_FOLDER", "xx-AA", "subject-folder-aa", "body-folder-aa", "2026-02-01 10:20:30", "2026-02-02 11:22:33");
        insert("SHARE_FOLDER", "xx-BB", "subject-folder-bb", "body-folder-bb", "2026-02-01 10:20:30", null);
        insert("PASSWORD_RESET", "xx-AA", "subject-reset-aa", "body-reset-aa", "2026-02-01 10:20:30", null);
    }

    private int insert(String key, String tag, String subject, String body, String registerDate, String updateDate) {
        return jdbc.queryForObject("""
                INSERT INTO tb_email_template (template_key, locale_tag, subject_template, body_template, register_user_seq, register_date, update_user_seq, update_date)
                VALUES (?, ?, ?, ?, 1, ?, ?, ?) RETURNING email_template_seq
                """, Integer.class, key, tag, subject, body, Timestamp.valueOf(registerDate), updateDate == null ? null : 2, updateDate == null ? null : Timestamp.valueOf(updateDate));
    }

    private static EmailTemplateFindQuery q(EmailTemplateKey key, Locale locale) {
        return EmailTemplateFindQuery.builder().templateKey(key).locale(locale).build();
    }

    @Test
    @DisplayName("키 × locale 단건 — 제목·본문(JPQL 생성자 식 DTO), 같은 키의 다른 locale·다른 키와 격리")
    void findByTemplateKeyAndLocale_mapping() {
        EmailTemplateData t = repository.findByTemplateKeyAndLocale(q(EmailTemplateKey.SHARE_FOLDER, XX_AA));

        assertThat(t).isNotNull();
        assertThat(t.getSubjectTemplate()).isEqualTo("subject-folder-aa");
        assertThat(t.getBodyTemplate()).isEqualTo("body-folder-aa");

        EmailTemplateData bb = repository.findByTemplateKeyAndLocale(q(EmailTemplateKey.SHARE_FOLDER, XX_BB));
        assertThat(bb.getSubjectTemplate()).isEqualTo("subject-folder-bb");
        assertThat(bb.getBodyTemplate()).isEqualTo("body-folder-bb");
        assertThat(repository.findByTemplateKeyAndLocale(q(EmailTemplateKey.PASSWORD_RESET, XX_AA)).getBodyTemplate()).isEqualTo("body-reset-aa");
    }

    @Test
    @DisplayName("없는 조합·null 입력 — null(구 XML 은 = NULL 로 0건), locale 은 toLanguageTag 로 비교(언어만 있는 Locale 은 불일치)")
    void findByTemplateKeyAndLocale_missingAndNull() {
        assertThat(repository.findByTemplateKeyAndLocale(q(EmailTemplateKey.PASSWORD_RESET, XX_BB))).isNull();
        assertThat(repository.findByTemplateKeyAndLocale(q(EmailTemplateKey.SHARE_FILE, XX_AA))).isNull();
        assertThat(repository.findByTemplateKeyAndLocale(q(EmailTemplateKey.SHARE_FOLDER, Locale.forLanguageTag("xx")))).isNull();
        assertThat(repository.findByTemplateKeyAndLocale(q(null, XX_AA))).isNull();
        assertThat(repository.findByTemplateKeyAndLocale(q(EmailTemplateKey.SHARE_FOLDER, null))).isNull();
    }
}

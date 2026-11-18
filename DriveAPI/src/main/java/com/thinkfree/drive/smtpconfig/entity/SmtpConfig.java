package com.thinkfree.drive.smtpconfig.entity;

import com.thinkfree.drive.global.jpa.audit.BaseAuditEntity;
import com.thinkfree.drive.global.jpa.SmtpPasswordConverter;
import com.thinkfree.drive.smtpconfig.enums.AuthType;
import com.thinkfree.drive.smtpconfig.enums.EncryptionType;
import com.thinkfree.drive.smtpconfig.enums.SmtpPassword;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * tb_smtp_config — 테넌트별 SMTP 발송 설정(테넌트당 1건 전제: {@code findByTenantSeq} 단건).
 * <p>
 * JPA 매핑(§1 v2 "JPA 우선", JdbcClient 0): INSERT 는 {@link #create} + {@link #changeSettings} → {@code save}, UPDATE 는 쓰기 리포 {@code findById} 로 관리 엔티티 로드(테넌트 판정은 Service) → {@link #changeSettings} → 변경 감지,
 * 조회는 JPQL 생성자 식 — {@code SmtpConfigJpaRepository}.
 * <ul>
 *   <li>enum 2종은 {@code @Enumerated(STRING)}(DB varchar(10) = 이름), 비밀번호는 {@link SmtpPasswordConverter}(구 {@code SmtpPasswordTypeHandler} 동형), 날짜 2종은 Instant(Hibernate 기본 매핑)</li>
 *   <li>감사 4컬럼은 {@link BaseAuditEntity}(Spring Data Auditing), register_date 는 Auditing(구 XML 은 DB DEFAULT 에 맡겼음 — 같은 현재 시각)</li>
 *   <li><b>update_date 는 {@code insertable = false}</b> — 구 XML INSERT 가 컬럼을 넣지 않아 DB DEFAULT CURRENT_TIMESTAMP 가 들어갔던 동작 유지(저장 직후 메모리 값은 null, 읽는 곳 없음).
 *       UPDATE 때는 Auditing 이 update_user_seq(시큐리티 사용자)·update_date(현재 시각)를 채운다(구 XML UPDATE 는 update_date 를 손대지 않았음 — MySQL 의 ON UPDATE CURRENT_TIMESTAMP 가 PG DDL 에 없어 멈춰 있던 값이라 JPA 경로에서 바로잡음)</li>
 * </ul>
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tb_smtp_config")
@AttributeOverride(name = "updateDate", column = @Column(name = "update_date", insertable = false))
public class SmtpConfig extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer smtpConfigSeq;      // SMTP 설정 seq
    private Integer tenantSeq;          // 소속 테넌트
    private String clientName;          // 고객사명
    private String host;                // SMTP 서버 주소
    private Integer port;               // SMTP 포트 번호
    @Enumerated(EnumType.STRING)
    private EncryptionType encryptionType;      // 보안 방식 (SSL, TLS, NONE)
    @Enumerated(EnumType.STRING)
    private AuthType authType;            // 인증 방식 (IDPW, OAuth)
    private String username;            // SMTP 사용자 계정
    @Convert(converter = SmtpPasswordConverter.class)
    private SmtpPassword encPwd;              // SMTP 비밀번호 (암호화 저장)
    private String oauthInfo;           // OAuth 인증 정보 (선택)
    private String fromAddress;         // 발신자 이메일
    private String testReceiverEmail;   // 테스트용 수신자 이메일

    /** 신규 등록용 빈 설정 — 값은 {@link #changeSettings} 로 채운 뒤 저장한다. 등록자는 선언 값(시큐리티 사용자가 있으면 Auditing 이 덮어씀), 등록일은 Auditing·수정일은 DB DEFAULT. */
    public static SmtpConfig create(Integer tenantSeq, Integer registerUserSeq) {
        SmtpConfig e = new SmtpConfig();
        e.tenantSeq = tenantSeq;
        e.assignRegisterAudit(registerUserSeq, null);
        return e;
    }

    /**
     * 설정 값 10컬럼 변경(등록·수정 공용). 관리 엔티티에서 부르면 커밋 때 변경 감지로 UPDATE 1문 — 수정자·수정일은 Auditing 이 채운다(손으로 쓰지 않음).
     * 값이 전부 같으면 UPDATE 가 나가지 않는다({@link SmtpPassword} 는 값 비교).
     */
    public void changeSettings(String clientName, String host, Integer port, EncryptionType encryptionType, AuthType authType,
                               String username, SmtpPassword encPwd, String oauthInfo, String fromAddress, String testReceiverEmail) {
        this.clientName = clientName;
        this.host = host;
        this.port = port;
        this.encryptionType = encryptionType;
        this.authType = authType;
        this.username = username;
        this.encPwd = encPwd;
        this.oauthInfo = oauthInfo;
        this.fromAddress = fromAddress;
        this.testReceiverEmail = testReceiverEmail;
    }
}

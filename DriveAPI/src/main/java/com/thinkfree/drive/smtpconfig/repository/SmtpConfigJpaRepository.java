package com.thinkfree.drive.smtpconfig.repository;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import com.thinkfree.drive.smtpconfig.data.SmtpConfigData;
import com.thinkfree.drive.smtpconfig.entity.SmtpConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * tb_smtp_config Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultSmtpConfigQueryRepository}(읽기)·{@link DefaultSmtpConfigRepository}(쓰기) 만 주입받아 위임한다.
 * 읽기 단건 2종은 JPQL 생성자 식({@link SmtpConfigData} — 엔티티를 영속 컨텍스트에 올리지 않음), INSERT → save, UPDATE → findById 관리 엔티티 + 변경 감지.
 */
interface SmtpConfigJpaRepository extends JpaRepository<SmtpConfig, Integer> {

    /** 전 컬럼 DTO 선택 머리 — encPwd 는 {@code SmtpPasswordConverter} 가 적용된 값 */
    String SELECT_DATA = "select new com.thinkfree.drive.smtpconfig.data.SmtpConfigData(s.smtpConfigSeq, s.tenantSeq, s.clientName, s.host, s.port, "
            + "s.encryptionType, s.authType, s.username, s.encPwd, s.oauthInfo, s.fromAddress, s.testReceiverEmail, "
            + "s.registerUserSeq, s.registerDate, s.updateUserSeq, s.updateDate) from SmtpConfig s ";

    /** PK 한 행 — 테넌트 판정은 Query 리포에서 Java 로 */
    @Query(SELECT_DATA + "where s.smtpConfigSeq = :smtpConfigSeq")
    Optional<SmtpConfigData> findDataBySmtpConfigSeq(@Param("smtpConfigSeq") Integer smtpConfigSeq);

    /** 테넌트당 1건 전제 — 2건 이상이면 IncorrectResultSizeDataAccessException(구 MyBatis selectOne 의 TooManyResultsException 과 같은 계약) */
    @Query(SELECT_DATA + "where s.tenantSeq = :tenantSeq")
    Optional<SmtpConfigData> findDataByTenantSeq(@Param("tenantSeq") Integer tenantSeq);

    /** 테넌트 삭제 — 이 테넌트의 행 전부(조건부 다건이라 JPQL @Modifying) */
    @Transactional
    @Modifying
    @Query("delete from SmtpConfig s where s.tenantSeq = :tenantSeq")
    int deleteByTenantSeq(@Param("tenantSeq") Integer tenantSeq);
}

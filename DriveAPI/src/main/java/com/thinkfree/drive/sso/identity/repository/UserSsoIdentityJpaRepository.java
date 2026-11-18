package com.thinkfree.drive.sso.identity.repository;

import com.thinkfree.drive.sso.identity.data.UserSsoIdentityData;
import com.thinkfree.drive.sso.identity.entity.UserSsoIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * tb_user_sso_identity Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultUserSsoIdentityQueryRepository}(읽기)·{@link DefaultUserSsoIdentityRepository}(쓰기) 만 주입받아 위임한다.
 * <ul>
 *   <li>{@code findWithUser} = JPQL 생성자 식(이 프로젝트 첫 적용) — {@code User} 엔티티를 {@code join … on} 으로 묶어 DTO 6필드 투영(구 XML INNER JOIN TB_USER 동형). UNIQUE (sso_provider_seq, subject, tenant_seq) 라 단건</li>
 *   <li>갱신·삭제는 JPQL {@code @Modifying}(리포 기본 트랜잭션이 readOnly 라 메서드에 {@code @Transactional} 필수)</li>
 * </ul>
 */
interface UserSsoIdentityJpaRepository extends JpaRepository<UserSsoIdentity, Long> {

    @Query("select new com.thinkfree.drive.sso.identity.data.UserSsoIdentityData(i.ssoIdentitySeq, i.userSeq, u.tenantSeq, u.status, u.mfaStatus, u.signupType) "
            + "from UserSsoIdentity i join User u on u.userSeq = i.userSeq "
            + "where i.ssoProviderSeq = :ssoProviderSeq and i.tenantSeq = :tenantSeq and i.subject = :subject")
    Optional<UserSsoIdentityData> findWithUser(@Param("ssoProviderSeq") Integer ssoProviderSeq, @Param("tenantSeq") Integer tenantSeq, @Param("subject") String subject);

    /** 로그인마다 로드 없이 끝내는 단일 컬럼 갱신 — 변경 감지(로드 → set)로 바꿀 이유가 없어 JPQL {@code @Modifying} 유지 */
    @Transactional
    @Modifying
    @Query("update UserSsoIdentity i set i.lastCallbackAt = :lastCallbackAt where i.ssoIdentitySeq = :ssoIdentitySeq")
    int updateLastCallbackAt(@Param("ssoIdentitySeq") Long ssoIdentitySeq, @Param("lastCallbackAt") Instant lastCallbackAt);

    /** 사용자 삭제·탈퇴 시 identity 정리(조건부 다건 삭제라 JPQL {@code @Modifying}) — 빈 목록 가드는 호출부(Default 리포) */
    @Transactional
    @Modifying
    @Query("delete from UserSsoIdentity i where i.userSeq in :userSeqList")
    int deleteByUserSeqIn(@Param("userSeqList") List<Integer> userSeqList);
}

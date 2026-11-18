package com.thinkfree.drive.superadmin.loginsetting.repository;

import com.thinkfree.drive.superadmin.loginsetting.data.SuperAdminLoginSettingData;
import com.thinkfree.drive.superadmin.loginsetting.entity.SuperAdminLoginSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * tb_super_admin_login_setting Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultSuperAdminLoginSettingQueryRepository} 만 주입받아 위임한다(JPQL 생성자 식).
 * 쓰기(upsert 2종)는 JdbcClient({@link DefaultSuperAdminLoginSettingRepository}).
 */
interface SuperAdminLoginSettingJpaRepository extends JpaRepository<SuperAdminLoginSetting, Integer> {

    /** PK 한 행(싱글턴) — DTO 로 받아 엔티티를 영속 컨텍스트에 올리지 않는다 */
    @Query("select new com.thinkfree.drive.superadmin.loginsetting.data.SuperAdminLoginSettingData(s.superAdminLoginSettingSeq, s.loginType, s.isEnforced, "
            + "s.registerUserSeq, s.registerDate, s.updateUserSeq, s.updateDate) from SuperAdminLoginSetting s where s.superAdminLoginSettingSeq = :seq")
    Optional<SuperAdminLoginSettingData> findData(@Param("seq") Integer seq);
}

package com.thinkfree.drive.superadmin.loginsetting.repository;

import com.thinkfree.drive.superadmin.loginsetting.data.SuperAdminLoginSettingData;
import com.thinkfree.drive.superadmin.loginsetting.entity.SuperAdminLoginSetting;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * tb_super_admin_login_setting 읽기 — JPQL 생성자 식({@link SuperAdminLoginSettingJpaRepository}, 구 SuperAdminLoginSettingMapper.xml 의 find)으로 {@link SuperAdminLoginSettingData} 를 바로 받는다.
 * 엔티티를 영속 컨텍스트에 올리지 않으므로 항상 DB 현재 값이고 detach 불필요. 쓰기는 {@link DefaultSuperAdminLoginSettingRepository}.
 */
@Repository
@RequiredArgsConstructor
public class DefaultSuperAdminLoginSettingQueryRepository implements SuperAdminLoginSettingQueryRepository {

    private final SuperAdminLoginSettingJpaRepository jpaRepository;

    @Transactional(readOnly = true)
    @Override
    public SuperAdminLoginSettingData find() {
        return jpaRepository.findData(SuperAdminLoginSetting.SINGLETON_SEQ).orElse(null);
    }
}

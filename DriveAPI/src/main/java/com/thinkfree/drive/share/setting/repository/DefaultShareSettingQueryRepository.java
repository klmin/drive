package com.thinkfree.drive.share.setting.repository;

import com.thinkfree.drive.share.setting.data.ShareSettingData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * tb_share_setting 읽기 — JPQL 생성자 식({@link ShareSettingJpaRepository}, 구 ShareSettingMapper.xml 의 findGlobal·findByTenant)으로 {@link ShareSettingData}(Y/N 원문 보관 record)를 바로 받는다.
 * 엔티티를 영속 컨텍스트에 올리지 않으므로 항상 DB 현재 값(같은 트랜잭션의 upsert(JdbcClient) 뒤에도)이고 detach 불필요. 쓰기(insert·upsert 5종)는 {@link DefaultShareSettingRepository}.
 */
@Repository
@RequiredArgsConstructor
public class DefaultShareSettingQueryRepository implements ShareSettingQueryRepository {

    private final ShareSettingJpaRepository jpaRepository;

    @Transactional(readOnly = true)
    @Override
    public ShareSettingData findGlobal() {
        return jpaRepository.findGlobal().orElse(null);
    }

    @Transactional(readOnly = true)
    @Override
    public ShareSettingData findByTenant(int tenantSeq) {
        return jpaRepository.findByTenant(tenantSeq).orElse(null);
    }
}

package com.thinkfree.drive.basedomain.repository;

import com.thinkfree.drive.basedomain.entity.BaseDomain;

/**
 * tb_base_domain 쓰기 — {@code DefaultBaseDomainService} 가 주입. 읽기는 {@link BaseDomainQueryRepository}.
 */
public interface BaseDomainRepository {

    void upsert(BaseDomain baseDomain);
}

package com.thinkfree.drive.basedomain.repository;

import com.thinkfree.drive.basedomain.data.BaseDomainData;

/**
 * tb_base_domain 읽기 — {@code DefaultBaseDomainFindService} 가 주입. 쓰기(upsert)는 {@link BaseDomainRepository}.
 */
public interface BaseDomainQueryRepository {

    /** 현재 베이스 도메인(단일 베이스 운영 — seq 1). 없으면 null */
    BaseDomainData findCurrent();

    /** PK 단건, 없으면 null */
    BaseDomainData findByBaseDomainSeq(Integer baseDomainSeq);
}

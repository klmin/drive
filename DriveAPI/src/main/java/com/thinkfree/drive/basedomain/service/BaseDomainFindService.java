package com.thinkfree.drive.basedomain.service;

import com.thinkfree.drive.basedomain.data.BaseDomainData;

public interface BaseDomainFindService {

    /** 현재 베이스 도메인. 미설정 시 null. */
    BaseDomainData findCurrent();

    /** 현재 베이스 도메인. 미설정 시 예외. */
    BaseDomainData findCurrentOrThrow();

    /** seq 로 베이스 도메인 조회. 없으면 예외. (테넌트 등록/수정 시 클라가 보낸 baseDomainSeq 검증용) */
    BaseDomainData findByBaseDomainSeqOrThrow(Integer baseDomainSeq);
}

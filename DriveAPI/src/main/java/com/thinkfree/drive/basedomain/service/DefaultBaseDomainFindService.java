package com.thinkfree.drive.basedomain.service;

import com.thinkfree.drive.basedomain.data.BaseDomainData;
import com.thinkfree.drive.basedomain.exception.BaseDomainErrorCode;
import com.thinkfree.drive.basedomain.repository.BaseDomainQueryRepository;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultBaseDomainFindService implements BaseDomainFindService {

    private final BaseDomainQueryRepository baseDomainQueryRepository;

    @Override
    public BaseDomainData findCurrent() {
        return baseDomainQueryRepository.findCurrent();
    }

    @Override
    public BaseDomainData findCurrentOrThrow() {
        BaseDomainData current = baseDomainQueryRepository.findCurrent();
        DriveAssert.throwIfNull(current, BaseDomainErrorCode.BASE_DOMAIN_NOT_FOUND);
        return current;
    }

    @Override
    public BaseDomainData findByBaseDomainSeqOrThrow(Integer baseDomainSeq) {
        BaseDomainData base = baseDomainQueryRepository.findByBaseDomainSeq(baseDomainSeq);
        DriveAssert.throwIfNull(base, BaseDomainErrorCode.BASE_DOMAIN_NOT_FOUND);
        return base;
    }
}

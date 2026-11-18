package com.thinkfree.drive.resource.service.impl;

import com.thinkfree.drive.global.jdbc.JdbcAudit;
import com.thinkfree.drive.resource.aspect.exception.ResourceLockErrorCode;
import com.thinkfree.drive.resource.converter.ResourceConverter;
import com.thinkfree.drive.resource.exception.ResourceErrorCode;
import com.thinkfree.drive.resource.query.ResourceLockExpireQuery;
import com.thinkfree.drive.resource.repository.ResourceQueryRepository;
import com.thinkfree.drive.resource.repository.ResourceRepository;
import com.thinkfree.drive.resource.service.ResourceLockUnlockService;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DefaultResourceLockUnlockService implements ResourceLockUnlockService {

    private final ResourceRepository resourceRepository;
    private final ResourceQueryRepository resourceQueryRepository;
    private final ResourceConverter resourceConverter;

    /**
     * 잠금 — 관리 엔티티 변경 감지(W10, 구 리포 안 로드·flush·detach). 수정 감사는 Auditing — 시큐리티 사용자가 없는 경로(웹오피스)만 로그인 사용자를 수정자로(결정 R10).
     * 뒤에 같은 행을 JdbcClient 로 읽는 곳 없음(호출부 트랜잭션이 여기서 끝남) → flush 없음. 없는 행은 LOCK_FAIL.
     */
    @Override
    public void lockResource(long resourceSeq, int userSeq, int loginUserSeq, Instant updateDate) {
        var resource = resourceRepository.findByUserSeqAndResourceSeq(userSeq, resourceSeq);
        DriveAssert.throwIfNull(resource, ResourceErrorCode.LOCK_FAIL);
        resource.applyLockState(Boolean.TRUE, Instant.now(), updaterWithoutSecurity(loginUserSeq));
    }

    /** 해제 — {@link #lockResource} 와 같은 방식. 이미 풀려 있으면 UPDATE 없음. 없는 행은 UNLOCK_FAIL */
    @Override
    public void unlockResource(long resourceSeq, int userSeq, int loginUserSeq, Instant updateDate) {
        var resource = resourceRepository.findByUserSeqAndResourceSeq(userSeq, resourceSeq);
        DriveAssert.throwIfNull(resource, ResourceErrorCode.UNLOCK_FAIL);
        resource.applyLockState(Boolean.FALSE, null, updaterWithoutSecurity(loginUserSeq));
    }

    /** 시큐리티 사용자가 있으면 Auditing 이 채우므로 null(감사 컬럼만 바뀌는 UPDATE 를 만들지 않기 위해 이때만 지정) */
    private static Integer updaterWithoutSecurity(int loginUserSeq) {
        return JdbcAudit.currentUserSeq() == null ? loginUserSeq : null;
    }

    @Override
    public void checkLockedResource(long resourceSeq, int userSeq) {
        var query = resourceConverter.toResourceLockUnlockQuery(resourceSeq, userSeq, null, null, null, true);
        var isLocked = resourceQueryRepository.isLockedResource(query);
        DriveAssert.throwIfTrue(isLocked, ResourceLockErrorCode.RESOURCE_LOCKED);
    }
    
    @Override
    public int updateExpiredLocks(ResourceLockExpireQuery query) {
        return resourceRepository.updateExpiredLocks(query);
    }
    
}

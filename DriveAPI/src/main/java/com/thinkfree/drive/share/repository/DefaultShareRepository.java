package com.thinkfree.drive.share.repository;

import com.thinkfree.drive.share.entity.Share;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.share.query.ShareDeleteQuery;
import com.thinkfree.drive.share.query.ShareDeleteResourceQuery;
import com.thinkfree.drive.share.query.ShareDeleteResourcesQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * tb_share 쓰기 — 전부 JPA 위임(구 ShareMapper.xml 의 insert 1·update 1·delete 6). 읽기는 {@link DefaultShareQueryRepository}.
 * <p>
 * {@code save} = {@link ShareJpaRepository#save}(IDENTITY 라 save 순간 INSERT — identity PK·등록 감사가 엔티티에 채워진다, flush·detach 없음 W9).
 * 수정 = {@link #findByUserSeqAndShareSeq} 로 받은 관리 엔티티를 Service 가 {@link Share#changeShare} 로 고치고 커밋 때 변경 감지(엔티티 {@code @PartitionKey} 로 UPDATE 에도 user_seq 조건, W9 — 구 update 의 flush·detach·감사 write-back 삭제).
 * DELETE 6종(공유·리소스·리소스 IN·사용자 IN·테넌트·테넌트+공유 유형)은 JPQL {@code @Modifying} 유지(PK 단건은 로드 없이 삭제 행 수로 판정, 나머지는 조건부 다건·서브쿼리) — 테넌트는 User 서브쿼리, shareTypes(name() 목록)는 enum 으로 바꿔 넘긴다.
 * IN 목록이 비면 쿼리 없이 0.
 */
@Repository
@RequiredArgsConstructor
public class DefaultShareRepository implements ShareRepository {

    private final ShareJpaRepository shareJpaRepository;

    /** IDENTITY 라 save 순간 INSERT — 반환 엔티티(관리 상태)에 share_seq·등록 감사가 채워진다. 같은 tx 의 대상 INSERT(JDBC 배치)보다 먼저 DB 에 들어가 있다. */
    @Override
    public Share save(Share share) {
        return shareJpaRepository.save(share);
    }

    /** 고칠 행 로드(관리 엔티티, 파티션 키 user_seq 조건), 없으면 null */
    @Override
    public Share findByUserSeqAndShareSeq(int userSeq, long shareSeq) {
        return shareJpaRepository.findByUserSeqAndShareSeq(userSeq, shareSeq).orElse(null);
    }

    @Override
    public int delete(ShareDeleteQuery q) {
        return shareJpaRepository.deleteByUserSeqAndShareSeq(q.getUserSeq(), q.getShareSeq());
    }

    @Override
    public int deleteByResource(ShareDeleteResourceQuery q) {
        return shareJpaRepository.deleteByUserSeqAndResourceSeq(q.getUserSeq(), q.getResourceSeq());
    }

    @Override
    public int deleteByResources(ShareDeleteResourcesQuery q) {
        if (q.getResourceSeqList() == null || q.getResourceSeqList().isEmpty()) {
            return 0;
        }
        return shareJpaRepository.deleteByUserSeqAndResourceSeqIn(q.getUserSeq(), q.getResourceSeqList());
    }

    @Override
    public int deleteByUserSeqList(List<Integer> userSeqList) {
        if (userSeqList == null || userSeqList.isEmpty()) {
            return 0;
        }
        return shareJpaRepository.deleteByUserSeqIn(userSeqList);
    }

    @Override
    public int deleteByTenant(Integer tenantSeq) {
        return shareJpaRepository.deleteByTenant(tenantSeq);
    }

    /** shareTypes 는 ShareType name() 목록(호출부 계약) — enum 파라미터로 바꿔 넘긴다. */
    @Override
    public int deleteByTenantAndShareTypes(Integer tenantSeq, List<String> shareTypes) {
        if (shareTypes == null || shareTypes.isEmpty()) {
            return 0;
        }
        return shareJpaRepository.deleteByTenantAndShareTypes(tenantSeq, shareTypes.stream().map(ShareType::valueOf).toList());
    }
}

package com.thinkfree.drive.share.repository;

import com.thinkfree.drive.share.entity.Share;
import com.thinkfree.drive.share.query.ShareDeleteQuery;
import com.thinkfree.drive.share.query.ShareDeleteResourceQuery;
import com.thinkfree.drive.share.query.ShareDeleteResourcesQuery;

import java.util.List;

/**
 * 쓰기 + 쓰기에 묶인 조회(Service 가 주입) — INSERT 는 JPA {@code save}(identity PK·감사 컬럼이 엔티티에 채워짐), 수정은 {@link #findByUserSeqAndShareSeq} 관리 엔티티의 변경 감지,
 * DELETE 는 JPQL. 읽기는 {@link ShareQueryRepository}.
 */
public interface ShareRepository {

    Share save(Share share);
    /** 고칠 행 로드(관리 엔티티 — 변경 감지 참여), 없으면 null */
    Share findByUserSeqAndShareSeq(int userSeq, long shareSeq);
    int delete(ShareDeleteQuery query);
    int deleteByResource(ShareDeleteResourceQuery query);
    int deleteByResources(ShareDeleteResourcesQuery query);
    int deleteByUserSeqList(List<Integer> userSeqList);
    int deleteByTenant(Integer tenantSeq);
    int deleteByTenantAndShareTypes(Integer tenantSeq, List<String> shareTypes);
}

package com.thinkfree.drive.trash.repository;

import com.thinkfree.drive.trash.entity.Trash;
import com.thinkfree.drive.trash.query.TrashDeleteQuery;
import com.thinkfree.drive.trash.query.TrashDeleteResourcesQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * tb_trash 쓰기 — JPA 위임(§1 v2, 구 TrashMapper.xml 의 save·delete·deleteByResources·deleteByUserSeqList). 읽기는 {@link DefaultTrashQueryRepository}.
 * <p>
 * {@code save} 는 Spring Data {@code save}(IDENTITY 라 저장 순간 INSERT, 구 XML {@code useGeneratedKeys trash_seq} 와 같이 키 회수, register_* 는 {@code Spring Data Auditing}). 삭제 3종은 JPQL {@code @Modifying} — 전부 user_seq 조건 포함(파티션 프루닝), 다건 또는 로드 없는 키 삭제(삭제 행 수로 판정)라 load→remove 로 바꾸지 않음.
 * {@code deleteByResources} 는 구 {@code MyBatisBatchSessionExecutor} task(`TrashMapper::deleteByResources`) 자리 — 휴지통 정리 배치가 사용자 그룹별로 호출한다. 이로써 MyBatis 배치 executor 사용처가 0 이 되어 {@code mybatis/batch} 패키지를 제거했다.
 */
@Repository
@RequiredArgsConstructor
public class DefaultTrashRepository implements TrashRepository {

    private final TrashJpaRepository jpaRepository;

    @Override
    public int save(Trash trash) {
        jpaRepository.save(trash);
        return 1;
    }

    @Override
    public int delete(TrashDeleteQuery q) {
        return jpaRepository.deleteByUserSeqAndResourceSeq(q.getUserSeq(), q.getResourceSeq());
    }

    @Override
    public int deleteByResources(TrashDeleteResourcesQuery q) {
        if (q.getResourceSeqList() == null || q.getResourceSeqList().isEmpty()) {
            return 0;
        }
        return jpaRepository.deleteByUserSeqAndResourceSeqIn(q.getUserSeq(), q.getResourceSeqList());
    }

    @Override
    public int deleteByUserSeqList(List<Integer> userSeqList) {
        if (userSeqList == null || userSeqList.isEmpty()) {
            return 0;
        }
        return jpaRepository.deleteByUserSeqIn(userSeqList);
    }
}

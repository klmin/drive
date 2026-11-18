package com.thinkfree.drive.trash.repository;

import com.thinkfree.drive.trash.entity.Trash;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * tb_trash Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultTrashRepository} 만 주입받아 위임한다.
 * DELETE JPQL 은 파티션(HASH user_seq) 프루닝을 위해 항상 {@code userSeq} 조건을 건다(구 XML 동일). {@code @Modifying} 메서드는 {@code @Transactional} 필수(리포 기본 readOnly 트랜잭션의 DELETE 를 PG 가 거부).
 */
interface TrashJpaRepository extends JpaRepository<Trash, Long> {

    @Transactional
    @Modifying
    @Query("delete from Trash t where t.userSeq = :userSeq and t.resourceSeq = :resourceSeq")
    int deleteByUserSeqAndResourceSeq(@Param("userSeq") Integer userSeq, @Param("resourceSeq") Long resourceSeq);

    @Transactional
    @Modifying
    @Query("delete from Trash t where t.userSeq = :userSeq and t.resourceSeq in :resourceSeqList")
    int deleteByUserSeqAndResourceSeqIn(@Param("userSeq") Integer userSeq, @Param("resourceSeqList") List<Long> resourceSeqList);

    @Transactional
    @Modifying
    @Query("delete from Trash t where t.userSeq in :userSeqList")
    int deleteByUserSeqIn(@Param("userSeqList") List<Integer> userSeqList);
}

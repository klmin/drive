package com.thinkfree.drive.user.role.repository;

import com.thinkfree.drive.user.enums.RoleType;
import com.thinkfree.drive.user.role.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * tb_user_role Spring Data JPA 선언 인터페이스 — **패키지 전용**. {@link DefaultUserRoleQueryRepository}(읽기)만 주입받아 위임한다.
 * 조회는 JPQL 스칼라·튜플(엔티티를 영속성 컨텍스트에 올리지 않음). 부여·회수는 jOOQ({@link DefaultUserRoleRepository}).
 */
interface UserRoleJpaRepository extends JpaRepository<UserRole, UserRole.Key> {

    @Query("select r.roleType from UserRole r where r.userSeq = :userSeq")
    List<RoleType> findRoleTypesByUserSeq(@Param("userSeq") Integer userSeq);

    @Query("select count(r) from UserRole r where r.userSeq = :userSeq and r.roleType = :roleType")
    long countByUserSeqAndRoleType(@Param("userSeq") Integer userSeq, @Param("roleType") RoleType roleType);

    /** [userSeq(Integer), roleType(RoleType)] */
    @Query("select r.userSeq, r.roleType from UserRole r where r.userSeq in :userSeqs")
    List<Object[]> findRoleRowsByUserSeqs(@Param("userSeqs") Collection<Integer> userSeqs);
}

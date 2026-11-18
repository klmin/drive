package com.thinkfree.drive.starred.entity;

import com.thinkfree.drive.global.jpa.audit.BaseRegisterEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.PartitionKey;

import java.time.Instant;

/**
 * tb_starred — JPA 매핑은 INSERT(save) 전용. 변경 메서드가 없는 불변 엔티티라 영속 컨텍스트에 두어도 flush 때 UPDATE 가 나가지 않는다.
 * register_date 는 Instant(Hibernate 기본 매핑), 감사는 Spring Data Auditing.
 */
@SuperBuilder
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tb_starred")
public class Starred extends BaseRegisterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long starredSeq;
    @PartitionKey   // tb_starred PARTITION BY HASH (user_seq) — 엔티티 기반 UPDATE·DELETE 가 생기면 WHERE 에 user_seq 를 붙여 파티션을 좁힌다(지금은 INSERT 전용, 삭제는 JPQL — W3 10-13)
    private Integer userSeq;
    private Long resourceSeq;
    private Integer sharedByUserSeq;

}

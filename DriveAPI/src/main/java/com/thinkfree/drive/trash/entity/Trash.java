package com.thinkfree.drive.trash.entity;

import com.thinkfree.drive.global.jpa.audit.BaseRegisterEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.PartitionKey;

import java.time.Instant;

/**
 * tb_trash — 휴지통 행(사용자 × 리소스). JPA 매핑(§1 v2): INSERT 는 {@code save}(IDENTITY 라 저장 순간 INSERT, identity 키·리스너 register_*), 삭제 3종은 JPQL, 목록(조인 DTO·동적 정렬·커서)만 JdbcClient.
 * <p>
 * DB 는 {@code PARTITION BY HASH (user_seq)} + PK (user_seq, trash_seq) 복합이지만 identity 는 trash_seq 하나(전역 유일)라 JPA 식별자는 {@code trashSeq} 단일(Notification 과 같은 결정).
 * DELETE JPQL 은 파티션 프루닝을 위해 항상 {@code user_seq} 조건을 함께 건다(구 XML 과 동일). {@code registerDate} 는 Instant(Hibernate 기본 매핑).
 */
@SuperBuilder
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tb_trash")
public class Trash extends BaseRegisterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long trashSeq;
    @PartitionKey   // tb_trash PARTITION BY HASH (user_seq) — 엔티티 기반 UPDATE·DELETE 가 생기면 WHERE 에 user_seq 를 붙여 파티션을 좁힌다(지금은 INSERT 전용, 삭제는 JPQL — W3 10-13)
    private Integer userSeq;
    private Long resourceSeq;

}

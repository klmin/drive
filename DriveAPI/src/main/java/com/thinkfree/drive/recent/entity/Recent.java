package com.thinkfree.drive.recent.entity;

import com.thinkfree.drive.global.jpa.audit.BaseRegisterEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.PartitionKey;

import java.time.Instant;

/**
 * tb_recent — 최근 문서(HASH(user_seq) 파티션, PK (user_seq, recent_seq)). recent_seq 는 identity 라 단독 {@code @Id}(tb_share 와 같은 매핑).
 * 등록 감사는 {@link BaseRegisterEntity}. update_date 는 감사가 아니라 "최근 열어본 일시" 도메인 값(converter 가 채움) — {@code @LastModifiedDate} 를 붙이지 않는다.
 * 저장은 upsert(JdbcClient)뿐이라 JPA 로 INSERT·UPDATE 하지 않는다. 등록 감사 write-back 은 상위 클래스의 {@code assignRegisterAudit}.
 */
@SuperBuilder
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tb_recent")
public class Recent extends BaseRegisterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long recentSeq;
    @PartitionKey   // tb_recent PARTITION BY HASH (user_seq) — 엔티티 기반 UPDATE·DELETE 가 생기면 WHERE 에 user_seq 를 붙여 파티션을 좁힌다(지금은 INSERT 전용, 삭제는 JPQL — W3 10-13)
    private Integer userSeq;
    private Long resourceSeq;
    private Integer sharedByUserSeq;
    private Instant updateDate;
}

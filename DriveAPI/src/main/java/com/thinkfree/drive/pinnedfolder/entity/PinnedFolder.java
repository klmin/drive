package com.thinkfree.drive.pinnedfolder.entity;

import com.thinkfree.drive.global.jpa.audit.BaseRegisterTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.PartitionKey;

import java.time.Instant;

/**
 * tb_pinned_folder — JPA 매핑은 INSERT(save) 전용. 변경 메서드가 없는 불변 엔티티라 영속 컨텍스트에 두어도 flush 때 UPDATE 가 나가지 않는다.
 * register_date 는 Instant(Hibernate 기본 TIMESTAMP_UTC) 라 컨버터 불필요, 값은 Spring Data Auditing 가 채운다(등록자 컬럼 없음).
 */
@SuperBuilder
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tb_pinned_folder")
public class PinnedFolder extends BaseRegisterTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long pinnedFolderSeq;
    @PartitionKey   // tb_pinned_folder PARTITION BY HASH (user_seq) — 엔티티 기반 UPDATE·DELETE 가 생기면 WHERE 에 user_seq 를 붙여 파티션을 좁힌다(지금은 INSERT 전용, 삭제는 JPQL — W3 10-13)
    private Integer userSeq;
    private Long resourceSeq;
    private Integer sharedByUserSeq;
}

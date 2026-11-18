package com.thinkfree.drive.share.target.entity;

import com.thinkfree.drive.global.jpa.audit.BaseAuditEntity;
import jakarta.persistence.Convert;
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
import org.hibernate.type.YesNoConverter;

import java.time.Instant;

/**
 * tb_share_target — 공유 대상(HASH(user_seq) 파티션, PK (user_seq, share_target_seq)). share_target_seq 는 identity 라 단독 {@code @Id}(tb_share 와 같은 매핑).
 * Y/N 권한 6컬럼은 {@link YesNoConverter}, 감사 4필드는 {@link BaseAuditEntity}. 저장은 배치 INSERT / ON CONFLICT upsert(JdbcClient)뿐이라 JPA 로 INSERT·UPDATE 하지 않는다
 * (upsert 의 update_* 는 converter 가 넣은 값 그대로, 등록 감사는 리포가 바인딩만 — write-back 없음 W9). {@link PartitionKey} 는 파티션 테이블 마커(§4-1 — 지금 엔티티 기반 UPDATE·DELETE 는 없음).
 */
@SuperBuilder
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tb_share_target")
public class ShareTarget extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long shareTargetSeq;
    @PartitionKey
    private Integer userSeq;
    private Long shareSeq;
    private Long resourceSeq;
    private Integer sharedByUserSeq;
    private Instant shareDate;
    @Convert(converter = YesNoConverter.class)
    private Boolean canView;
    @Convert(converter = YesNoConverter.class)
    private Boolean canEdit;
    @Convert(converter = YesNoConverter.class)
    private Boolean canDownload;
    @Convert(converter = YesNoConverter.class)
    private Boolean canUpload;
    @Convert(converter = YesNoConverter.class)
    private Boolean canDelete;
    @Convert(converter = YesNoConverter.class)
    private Boolean canShare;
}

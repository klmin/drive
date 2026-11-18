package com.thinkfree.drive.resource.version.entity;

import com.thinkfree.drive.global.jpa.audit.BaseAuditEntity;
import com.thinkfree.drive.resource.version.enums.ResourceVersionStatus;
import com.thinkfree.drive.valid.DriveAssert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.PartitionKey;

import java.time.Instant;

import static com.thinkfree.drive.constants.DriveConstants.PATH_SEPARATOR;

/**
 * tb_resource_version — 리소스 버전(HASH(user_seq) 파티션, PK (user_seq, resource_version_seq)). resource_version_seq 는 identity 라 단독 {@code @Id}(tb_share 와 같은 매핑).
 * 감사 4필드는 {@link BaseAuditEntity}. INSERT 는 JPA save(IDENTITY — 즉시 INSERT, 관리 상태 유지), 저장 이름·상태 UPDATE 는 변경 감지(W8 — {@link PartitionKey} 로 WHERE 에 user_seq, {@link DynamicUpdate} 로 바뀐 컬럼만, 감사 컬럼은 Auditing), 번호 일괄 이동·소유자 이전은 JPQL {@code @Modifying}(조건부 다건·감사 값은 {@code JdbcAudit} 규칙).
 * <p>
 * <b>잠금·만료 판정·다른 트랜잭션으로 넘기는 값은 엔티티가 아니라 {@code ResourceVersionRowData}</b> — 관리 엔티티는 고칠 행 로드에서만 나온다(받은 엔티티를 고치면 커밋 때 UPDATE 가 나가므로 결과 조립은 값으로).
 * status·resource_save_name 은 INSERT 에 넣지 않는다(구 INSERT 와 같이 DB DEFAULT 'PENDING' 으로 시작해 업로드 완료 후 SUCCESS, 저장 이름은 PK 를 받은 뒤 UPDATE).
 */
@SuperBuilder
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@DynamicUpdate
@Table(name = "tb_resource_version")
public class ResourceVersion extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long resourceVersionSeq;
    @PartitionKey
    private Integer userSeq;
    private Integer ownerSeq;
    private Long resourceSeq;
    @Column(insertable = false)
    private String resourceSaveName;
    private Integer versionNumber;
    private Long sizeByte;
    @Enumerated(EnumType.STRING)
    @Column(insertable = false)
    private ResourceVersionStatus status;
    private Instant versionRegisterDate;

    /** 저장 이름 확정(원본 키 + "/" + PK) — save 직후 관리 엔티티에 호출, 커밋 때 변경 감지 UPDATE(W8) */
    public void initResourceSaveName() {
        DriveAssert.throwIfNoText("resourceSaveName is null", resourceSaveName);
        this.resourceSaveName += PATH_SEPARATOR + resourceVersionSeq;
    }

    /** INSERT 전 번호 조정(버전 한도 초과로 가장 오래된 버전을 밀어낼 때) — 아직 저장 안 한 새 엔티티에만 */
    public void decreaseVersionNumber() {
        this.versionNumber--;
    }

    /** 상태 변경 — 관리 엔티티(쓰기 리포 findByUserSeqAndResourceVersionSeq)에 호출, 커밋 때 변경 감지(감사는 Auditing, W8) */
    public void changeStatus(ResourceVersionStatus status) {
        this.status = status;
    }
}

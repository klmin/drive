package com.thinkfree.drive.notification.entity;

import com.thinkfree.drive.global.jpa.audit.BaseAuditEntity;
import com.thinkfree.drive.global.jpa.audit.*;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.PartitionKey;
import org.hibernate.type.YesNoConverter;

import java.time.Instant;

/**
 * tb_notification — 사용자별 알림 행. JPA 매핑(§1 v2): 전체 읽음·삭제는 JPQL {@code @Modifying}, 단건 읽음은 변경 감지(10-08), count 는 JPQL. 알림 N건 INSERT 는 IDENTITY 라 Hibernate 가 배치하지 못해 {@code NamedParameterJdbcTemplate.batchUpdate}(생성 키 미회수), 목록 조회(3테이블 조인 DTO·커서)는 JdbcClient.
 * <p>
 * DB 는 {@code PARTITION BY HASH (user_seq)} + PK (notification_seq, user_seq) 복합이지만 identity 는 notification_seq 하나(전역 유일)라 JPA 식별자는 {@code notificationSeq} 단일로 둔다.
 * 그래서 UPDATE/DELETE JPQL 은 파티션 프루닝을 위해 항상 {@code user_seq} 조건을 함께 건다(구 XML 과 동일). {@code is_read} char(1) Y/N 은 {@link YesNoConverter}.
 * 변경 감지 UPDATE(단건 읽음 처리)는 {@link PartitionKey} 로 WHERE 에 user_seq 가 붙고, {@link DynamicUpdate} 로 바뀐 컬럼만 쓴다(잠금 없이도 다른 컬럼을 덮어쓰지 않음, 같은 컬럼은 나중 쓰기 우선 — 구 JPQL 동일).
 * <b>변경 감지 경로에서 감사 컬럼(update_*)을 직접 바꾸지 않는다</b> — Auditing 에 맡긴다. 감사 컬럼만 dirty 가 됐다가 Auditing 이 DB 값과 같게 되돌리면 바꿀 컬럼이 0 인 동적 UPDATE 에 파티션 키를 바인딩하다 NPE(Hibernate 6.6~main, AbstractMutationCoordinator#bindPartitionColumnValueBindings 가 생략된 테이블을 확인하지 않음). 감사 값을 지정·복원해야 하는 쓰기는 JPQL/JdbcClient. 단, {@code em.find} 는 user_seq 를 모르므로 로드는 JPQL(user_seq 조건)로.
 */
@SuperBuilder
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@DynamicUpdate
@Table(name = "tb_notification")
public class Notification extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationSeq;
    @PartitionKey
    private Integer userSeq;
    private Long notificationEventSeq;
    @Convert(converter = YesNoConverter.class)
    private Boolean isRead;
    private Instant readDatetime;

    /** 2-phase 저장 — 이벤트 저장 뒤 생성 키를 알림에 연결(저장 전 호출). */
    public void changeNotificationEventSeq(Long notificationEventSeq) {
        this.notificationEventSeq = notificationEventSeq;
    }

    /** 단건 읽음/안 읽음 — 서비스가 쓰기 리포 findByUserSeqAndNotificationSeq 의 관리 엔티티에서 호출, 커밋 때 변경 감지. 감사 컬럼은 flush 때 Auditing. */
    public void changeReadState(Boolean isRead, Instant readDatetime) {
        this.isRead = isRead;
        this.readDatetime = readDatetime;
    }
}

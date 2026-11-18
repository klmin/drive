package com.thinkfree.drive.notification.event.entity;

import com.thinkfree.drive.global.jpa.audit.BaseRegisterEntity;
import com.thinkfree.drive.notification.event.enums.ActionType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * tb_notification_event — 알림 이벤트(알림 N건이 참조). JPA 매핑(§1 v2): INSERT 는 {@code save}(IDENTITY 라 저장 순간 INSERT)(identity 키·리스너 register_*), 삭제는 JPQL.
 * {@code description} 은 jsonb 컬럼 — 문자열(직렬화된 payload)을 {@code @JdbcTypeCode(SqlTypes.JSON)} 으로 그대로 넣는다(구 XML 의 {@code CAST(#{description} AS JSONB)} 자리, 방언이 처리하므로 이식 가능).
 * 변경 메서드 없음. 구 MyBatis 2-phase 배치(이벤트 저장 → 생성 키 → 알림 저장)는 {@code DefaultNotificationRepository} 가 JPA 로 수행.
 */
@SuperBuilder
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tb_notification_event")
public class NotificationEvent extends BaseRegisterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationEventSeq;
    private Long resourceSeq;
    private Integer sharedByUserSeq;
    @Enumerated(EnumType.STRING)
    private ActionType actionType;
    @JdbcTypeCode(SqlTypes.JSON)
    private String description;
    private Integer actorUserSeq;
    private Instant occurredDatetime;
}

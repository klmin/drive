package com.thinkfree.drive.global.jpa;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.type.YesNoConverter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 테스트 전용 엔티티 — Y/N Boolean 컨버터({@link YesNoConverter}) 의 DB 왕복 확인용({@link JpaBootstrapTest}).
 * 부트스트랩 시점에 {@code @Entity} 를 붙인 Tenant·ApiKey 에는 char(1) 컬럼이 없어 tb_share_setting 에 최소 컬럼만 매핑한다.
 * ShareSetting 리포가 JPA 로 넘어가면 그 엔티티로 대체하고 삭제.
 */
@Entity
@Table(name = "tb_share_setting")
@EntityListeners(AuditingEntityListener.class)
@Builder
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class YesNoProbeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer shareSettingSeq;
    private Integer tenantSeq;
    @Convert(converter = YesNoConverter.class)
    private Boolean isEnforced;
    @Convert(converter = YesNoConverter.class)
    private Boolean isExternalShareAllowed;
    @Convert(converter = YesNoConverter.class)
    private Boolean isSpecificUsersAllowed;
    @Convert(converter = YesNoConverter.class)
    private Boolean isOrgLinkAllowed;
    @Convert(converter = YesNoConverter.class)
    private Boolean isPublicAllowed;
    @CreatedBy
    private Integer registerUserSeq;
    @CreatedDate
    private Instant registerDate;
}

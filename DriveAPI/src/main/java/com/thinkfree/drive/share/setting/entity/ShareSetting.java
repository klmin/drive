package com.thinkfree.drive.share.setting.entity;

import com.thinkfree.drive.global.jpa.audit.BaseAuditEntity;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.type.YesNoConverter;

/**
 * 공유 설정 엔티티. 단일 테이블 {@code tb_share_setting} 에 매핑된다.
 * <ul>
 *     <li>tenantSeq == null : 슈퍼어드민(글로벌) 행. {@code isEnforced} 가 Y/N 토글</li>
 *     <li>tenantSeq != null : 테넌트별 행. {@code isEnforced} 는 항상 false('N') (DB CHECK 제약)</li>
 * </ul>
 * Boolean 필드는 'Y'/'N'(char(1)) — {@link YesNoConverter}. 쓰기는 {@code DefaultShareSettingRepository} — upsert 5종은 JdbcClient(감사는 {@code JdbcAudit}), 기본 행 insert 는 save(감사는 Spring Data Auditing),
 * 조회는 JPQL 생성자 식({@code DefaultShareSettingQueryRepository} — ShareSettingData). 감사 4필드는 {@link BaseAuditEntity}. 변경 메서드 없음.
 */
@SuperBuilder
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tb_share_setting")
public class ShareSetting extends BaseAuditEntity {

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
}

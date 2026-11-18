package com.thinkfree.drive.license.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * tb_license — 테넌트별 라이선스 blob(bytea). 테넌트당 1행(UNIQUE tenant_seq). 감사 컬럼 없음(created_at/updated_at 은 DB NOW()).
 * <p>
 * JPA 매핑(§1 v2): 읽기 3종은 {@code LicenseJpaRepository}(단건 DTO 생성자 식·updated_at 투영·tenant_seq 목록), **쓰기(upsert ON CONFLICT)는 JdbcClient**({@code DefaultLicenseRepository}) —
 * created_at/updated_at 을 DB NOW() 로 두던 구 어노테이션 매퍼 동형. 변경 메서드 없음(읽기 전용 엔티티).
 * name 컬럼은 읽지도 쓰지도 않아 스키마에서 뺐다(이전에 만든 DB 에는 남아 있을 수 있음 — 매핑하지 않으므로 영향 없음).
 * 날짜는 Instant(timestamptz 절대 시각, Hibernate 기본 매핑).
 * <p>
 * auth_* 는 온라인 인증 결과다. 오프라인 라이선스는 authStatus 가 null 이다. updated_at(파일 수정 시각)과는 따로 바뀐다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tb_license")
public class License {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer licenseSeq;
    private Integer tenantSeq;
    private byte[] data;
    private Instant createdAt;
    private Instant updatedAt;
    private String authStatus;
    private Integer authFailCount;
    private Instant authCheckedAt;
    private Instant authSuccessAt;
    private Instant authInitAt;
    private String authSignedData;
}

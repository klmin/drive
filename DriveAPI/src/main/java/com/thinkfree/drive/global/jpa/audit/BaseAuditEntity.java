package com.thinkfree.drive.global.jpa.audit;

import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;

/**
 * 감사 상위 클래스 3단 — 등록일·등록자 + 수정자(update_user_seq)·수정일(update_date).
 * <p>
 * 수정 감사는 변경 감지로 UPDATE 가 나갈 때만 채운다. INSERT 때는 비워 둔다({@code modifyOnCreate = false}).
 */
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@MappedSuperclass
public abstract class BaseAuditEntity extends BaseRegisterEntity {

    @LastModifiedBy
    private Integer updateUserSeq;

    @LastModifiedDate
    private Instant updateDate;

    /** JdbcClient 쓰기 경로의 수정 감사 값 write-back. */
    public void changeUpdateMetadata(Integer updateUserSeq, Instant updateDate) {
        this.updateUserSeq = updateUserSeq;
        this.updateDate = updateDate;
    }
}

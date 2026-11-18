package com.thinkfree.drive.share.entity;

import com.thinkfree.drive.global.jpa.audit.BaseAuditEntity;
import com.thinkfree.drive.global.jpa.audit.*;
import com.thinkfree.drive.permission.support.PermissionHolder;
import com.thinkfree.drive.share.enums.ShareType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.PartitionKey;
import org.hibernate.type.YesNoConverter;

import java.time.Instant;

/**
 * tb_share(HASH(user_seq) 파티션) — INSERT(save — IDENTITY 라 즉시 INSERT) + 단건 수정은 Service 가 쓰기 리포에서 로드한 관리 엔티티의 변경 감지({@link #changeShare}, W9) + 벌크 DELETE 는 JPQL.
 * Y/N char(1) 은 {@link YesNoConverter}, Instant 3필드는 Hibernate 기본 매핑, 감사는 Spring Data Auditing.
 * 식별자는 identity share_seq 단독이라 {@link PartitionKey} 로 변경 감지 UPDATE 의 WHERE 에 user_seq 를 붙이고({@code em.find} 는 user_seq 를 모르므로 로드는 JPQL), {@link DynamicUpdate} 로 바뀐 컬럼만 쓴다(잠금 없이도 다른 컬럼을 덮어쓰지 않음, 같은 컬럼은 나중 쓰기 우선 — 구 JPQL 동일).
 * <b>변경 감지 경로에서 감사 컬럼(update_*)을 직접 바꾸지 않는다</b> — Auditing 에 맡긴다. 감사 컬럼만 dirty 가 됐다가 Auditing 이 DB 값과 같게 되돌리면 바꿀 컬럼이 0 인 동적 UPDATE 에 파티션 키를 바인딩하다 NPE(Hibernate 6.6~main, AbstractMutationCoordinator#bindPartitionColumnValueBindings 가 생략된 테이블을 확인하지 않음). 감사 값을 지정·복원해야 하는 쓰기는 JPQL/JdbcClient.
 */
@SuperBuilder
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@DynamicUpdate
@Table(name = "tb_share")
public class Share extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long shareSeq;
    @PartitionKey
    private Integer userSeq;
    private Long resourceSeq;
    @Enumerated(EnumType.STRING)
    private ShareType shareType;
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
    private String password;
    @Convert(converter = YesNoConverter.class)
    private Boolean isPasswordEnabled;
    private Instant expireDate;

    /**
     * 공유 유형 변경(+ 링크 공유 ORG_LINK/PUBLIC_LINK 면 권한 6종, 구 XML {@code <set>}) — Service 가 쓰기 리포에서 로드한 관리 엔티티에 호출, 커밋 때 변경 감지(감사는 Auditing, W9).
     * 링크 공유가 아니면 권한 컬럼은 그대로. 값이 같으면 UPDATE 가 나가지 않는다.
     */
    public void changeShare(ShareType shareType, PermissionHolder permissions) {
        this.shareType = shareType;
        if (shareType == ShareType.ORG_LINK || shareType == ShareType.PUBLIC_LINK) {
            this.canView = permissions.getCanView();
            this.canEdit = permissions.getCanEdit();
            this.canDownload = permissions.getCanDownload();
            this.canUpload = permissions.getCanUpload();
            this.canDelete = permissions.getCanDelete();
            this.canShare = permissions.getCanShare();
        }
    }
}

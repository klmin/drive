package com.thinkfree.drive.resource.entity;

import com.thinkfree.drive.global.jpa.audit.BaseAuditEntity;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.util.StringUtil;
import com.thinkfree.drive.valid.DriveAssert;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.PartitionKey;
import org.hibernate.type.YesNoConverter;

import java.time.Instant;

import static com.thinkfree.drive.constants.DriveConstants.DEFAULT_VERSION_NUMBER;
import static com.thinkfree.drive.constants.DriveConstants.PATH_SEPARATOR;

/**
 * tb_resource — 파일·폴더(HASH(user_seq) 파티션, PK (user_seq, resource_seq)). resource_seq 는 identity 라 단독 {@code @Id}(tb_share 와 같은 매핑).
 * 감사 4필드는 {@link BaseAuditEntity}. INSERT 는 {@code DefaultResourceRepository.save}(저장 뒤 관리 상태 — 이어서 고친 저장 이름·폴더 경로는 커밋 때 변경 감지),
 * 단건 업무 수정(저장 이름·폴더 경로·버전 갱신·이름 변경·이동(소유자 변경 포함)·복원·파일 휴지통 이동·잠금/해제·현재 버전·공유 토큰)은 Service 가 쓰기 리포에서 받은 관리 엔티티를 변경 메서드로 고치고 커밋 때 변경 감지(W10),
 * 덮어쓰기·되돌리기·조건부 다건은 JPQL {@code @Modifying}, 동적 SET·UPDATE … FROM·문자열 경로 치환·집계 잠금은 JdbcClient — 수정 감사는 Auditing·{@code JdbcAudit} 같은 규칙(롤백·되돌리기는 이전 값 그대로).
 * <p>
 * 식별자는 identity resource_seq 단독이라 {@link PartitionKey} 로 변경 감지 UPDATE 의 WHERE 에 user_seq 를 붙이고({@code em.find} 는 user_seq 를 모르므로 로드는 JPQL), {@link DynamicUpdate} 로 바뀐 컬럼만 쓴다(잠금 없이도 다른 컬럼을 덮어쓰지 않음, 같은 컬럼은 나중 쓰기 우선 — 구 JPQL 동일).
 * <b>변경 감지 경로에서 감사 컬럼(update_*)을 직접 바꾸지 않는다</b> — Auditing 에 맡긴다. 감사 컬럼만 dirty 가 됐다가 Auditing 이 DB 값과 같게 되돌리면 바꿀 컬럼이 0 인 동적 UPDATE 에 파티션 키를 바인딩하다 NPE(Hibernate 6.6~main, AbstractMutationCoordinator#bindPartitionColumnValueBindings 가 생략된 테이블을 확인하지 않음). 감사 값을 지정·복원해야 하는 쓰기는 JPQL/JdbcClient.
 * <b>저장할 생각이 없는 메모리 변경을 하지 않는다</b> — 관리 엔티티라 어떤 변경 메서드든 커밋 때 UPDATE 로 나간다(W10 — 구 detach 엔티티 수정·되돌리기 제거). 감사 컬럼을 바꾸는 예외는 시큐리티 없는 경로의 수정자 지정 2곳(버전 갱신·잠금)뿐.
 * INSERT 는 {@link DynamicInsert}(null 필드는 컬럼째 빼서 DB DEFAULT — 구 파일/폴더 INSERT 두 벌의 컬럼 차이를 대신함)이고,
 * 구 INSERT 가 넣지 않던 컬럼(저장 이름·삭제 부모·현재 버전·삭제 여부·잠금)은 {@code insertable = false}. resource_name_numeric_part·resource_name_text_part 는 생성 컬럼이라 매핑하지 않는다.
 */
@SuperBuilder
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@DynamicInsert
@DynamicUpdate
@Table(name = "tb_resource")
public class Resource extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long resourceSeq;
    @PartitionKey
    private Integer userSeq;
    private Integer ownerSeq;
    private Integer latestOwnerSeq;
    @Enumerated(EnumType.STRING)
    private ResourceType resourceType;
    private Long parentFolderSeq;
    @Column(name = "delete_parent_folder_seq", insertable = false)
    private Long deleteFolderSeq;
    private String resourceName;
    private String originalFileName;
    @Column(insertable = false)
    private String resourceSaveName;
    private String fullPathSeq;
    private String fullPath;
    @Column(insertable = false)
    private Integer currentVersionNumber;
    private Instant versionRegisterDate;
    private int copyNumber;
    @Convert(converter = YesNoConverter.class)
    @Column(insertable = false)
    private Boolean isDelete;
    @Convert(converter = YesNoConverter.class)
    @Column(insertable = false)
    private Boolean isLocked;
    @Column(insertable = false)
    private Instant lockedDate;
    private String ext;
    private String contentType;
    private Long sizeByte;
    private String shareToken;

    /**
     * INSERT 에서 빠지는 컬럼({@code insertable = false})의 메모리 값을 DB DEFAULT 와 맞춘다(현재 버전 1·삭제 N·잠금 N) — 저장 뒤에도 관리 상태로 남아(W10)
     * 같은 트랜잭션에서 이 인스턴스를 다시 받으면(1차 캐시) 이 값을 읽기 때문. persist 전에 정해져 스냅샷에도 같은 값이라 UPDATE 가 나가지 않는다.
     */
    @PrePersist
    void alignInsertDefaults() {
        if (currentVersionNumber == null) {
            currentVersionNumber = DEFAULT_VERSION_NUMBER;
        }
        if (isDelete == null) {
            isDelete = Boolean.FALSE;
        }
        if (isLocked == null) {
            isLocked = Boolean.FALSE;
        }
    }

    public void initResourceSaveName() {
        DriveAssert.throwIfAnyNull("userSeq or resourceSeq is null", userSeq, resourceSeq);
        this.resourceSaveName = userSeq + PATH_SEPARATOR + resourceSeq;
    }
    /** 업로드 세션이 정한 불변 스토리지 key({tenantSeq}/{세션 uuid}) — 계산하지 않고 받은 값 그대로 저장한다. */
    public void applyResourceSaveName(String storageKey) {
        DriveAssert.throwIfAnyNull("storageKey is null", storageKey);
        this.resourceSaveName = storageKey;
    }
    public void changeCopyResource(String baseFilename, String originalFileName, int copyNumber) {
        DriveAssert.throwIfAnyNoText("baseFilename or originalFileName is empty", baseFilename, originalFileName);
        this.resourceName = baseFilename;
        this.originalFileName = originalFileName;
        this.copyNumber = copyNumber;
    }
    public void initFolderPath(){
        DriveAssert.throwIfNoText(resourceName, "resourceName is empty");
        DriveAssert.throwIfNull(resourceSeq, "resourceSeq is null");
        this.fullPathSeq = StringUtil.toEmptyIfBlank(fullPathSeq) + resourceSeq + PATH_SEPARATOR;
        this.fullPath = StringUtil.toEmptyIfBlank(fullPath) + resourceName + PATH_SEPARATOR;
    }
    public void replaceFullPath(String fullPath) {
        DriveAssert.throwIfNoText(fullPath, "fullPath is empty");
        int lastSlashIndex = fullPath.lastIndexOf("/");
        int secondLastSlashIndex = fullPath.lastIndexOf("/", lastSlashIndex - 1);
        this.fullPath = fullPath.substring(0, secondLastSlashIndex + 1) + resourceName + "/";
    }
    public void moveToParentFolder(Long parentFolderSeq) {
        this.parentFolderSeq = parentFolderSeq;
    }
    public void changeFolderPath(String fullPath, String fullPathSeq) {
        DriveAssert.throwIfAnyNoText("fullPath or fullPathSeq is empty", fullPath, fullPathSeq);
        this.fullPath = fullPath;
        this.fullPathSeq = fullPathSeq;
    }
    public void applyShareToken(String shareToken) {
        DriveAssert.throwIfNoText(shareToken, "shareToken is empty");
        this.shareToken = shareToken;
    }

    // ------------------------------------------------------------------ 관리 엔티티 변경(W10 — Service 가 쓰기 리포에서 받은 엔티티를 고치고 커밋 때 변경 감지)

    /**
     * 버전 갱신 — 현재 버전(한도 초과면 그대로)·버전 등록일·크기·최종 소유자.
     * 감사 컬럼을 건드리는 유일한 예외: 버전 등록자가 있으면 수정자로 둔다 — 웹오피스 저장처럼 시큐리티 사용자가 없는 경로에서 Auditing 이 수정자를 모르기 때문
     * (시큐리티 사용자가 있으면 flush 때 Auditing 이 덮어씀). 버전 등록일이 매번 새 값이라 업무 컬럼이 항상 dirty 여서 감사 컬럼만 바뀌는 UPDATE 는 생기지 않는다.
     */
    public void applyVersionUpdate(Boolean isLimitExceeded, Integer versionRegisterUserSeq, Long newSizeByte, Integer newLatestOwnerSeq, Instant versionRegisterDate) {
        if (!isLimitExceeded) {
            this.currentVersionNumber++;
        }
        this.versionRegisterDate = versionRegisterDate;
        this.sizeByte = newSizeByte;
        this.latestOwnerSeq = newLatestOwnerSeq;
        if (versionRegisterUserSeq != null) {
            changeUpdateMetadata(versionRegisterUserSeq, getUpdateDate());
        }
    }

    /** 버전 삭제 후 현재 버전 번호 -1 */
    public void decreaseVersionNumber() {
        this.currentVersionNumber--;
    }

    /** 현재 버전 번호 지정(전체 버전 삭제 후 기본값) */
    public void applyCurrentVersion(Integer currentVersionNumber) {
        this.currentVersionNumber = currentVersionNumber;
    }

    /** 파일 이름 변경 — 이름·원본 파일명·복사본 번호(값 검사 없음 — 구 JPQL rename 과 같음) */
    public void rename(String resourceName, String originalFileName, int copyNumber) {
        this.resourceName = resourceName;
        this.originalFileName = originalFileName;
        this.copyNumber = copyNumber;
    }

    /** 소유자 변경(다른 사용자 폴더로 이동) — 파티션 키({@link PartitionKey}) 변경도 변경 감지로 나간다(WHERE 에 이전 user_seq, PG 가 행을 다른 파티션으로 옮김 — 계획 부록 D-5) */
    public void changeUserSeq(Integer userSeq) {
        this.userSeq = userSeq;
    }

    /** 휴지통 복원(자기 자신) — 삭제 부모를 비우고 삭제 여부 N(부모·이름·경로는 호출부가 먼저 바꿈) */
    public void restore() {
        this.deleteFolderSeq = null;
        this.isDelete = Boolean.FALSE;
    }

    /**
     * 잠금 여부·잠금 시각(해제면 null). 시큐리티 사용자가 없는 경로(웹오피스)는 로그인 사용자를 수정자로 둔다(10-08 부터의 예외 — 결정 R10).
     * 잠금 상태가 그대로면(이미 풀린 걸 다시 해제 등) 같은 수정자일 때 바뀐 컬럼이 없어 UPDATE 없음.
     */
    public void applyLockState(Boolean isLocked, Instant lockedDate, Integer updateUserSeqWithoutSecurity) {
        this.isLocked = isLocked;
        this.lockedDate = lockedDate;
        if (updateUserSeqWithoutSecurity != null) {
            changeUpdateMetadata(updateUserSeqWithoutSecurity, getUpdateDate());
        }
    }

    /** 삭제 여부(파일 휴지통 이동) */
    public void applyDeleteState(Boolean isDelete) {
        this.isDelete = isDelete;
    }
}

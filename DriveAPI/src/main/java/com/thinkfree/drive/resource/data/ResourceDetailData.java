package com.thinkfree.drive.resource.data;

import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * 리소스 한 행(공유 토큰 제외) — 읽기 전용 값. 화면·판단·감사 공용(PK 한 행이라 용도별로 나누지 않음, 단계 3 User 와 같은 방침).
 * getter 이름은 엔티티 {@code Resource} 와 같다(그 값을 읽는 기존 로그 문 무변경). 목록용 {@link ResourceData} 와는 별개.
 */
@Getter
@AllArgsConstructor // 공개 전체 생성자는 JPQL 생성자 식용(ResourceJpaRepository#SELECT_DETAIL) — 필드 순서를 바꾸면 JPQL 도 맞출 것
public class ResourceDetailData implements ResourceIdentity {

    private Long resourceSeq;
    private Integer userSeq;
    private Integer ownerSeq;
    private Integer latestOwnerSeq;
    private ResourceType resourceType;
    private Long parentFolderSeq;
    private Long deleteFolderSeq;
    private String resourceName;
    private String originalFileName;
    private String resourceSaveName;
    private String fullPathSeq;
    private String fullPath;
    private Integer currentVersionNumber;
    private Instant versionRegisterDate;
    private int copyNumber;
    private Boolean isDelete;
    private Boolean isLocked;
    private Instant lockedDate;
    private String ext;
    private String contentType;
    private Long sizeByte;
    private Integer registerUserSeq;
    private Instant registerDate;
    private Integer updateUserSeq;
    private Instant updateDate;
}

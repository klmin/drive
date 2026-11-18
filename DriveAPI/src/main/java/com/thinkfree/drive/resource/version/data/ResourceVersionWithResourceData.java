package com.thinkfree.drive.resource.version.data;

import com.thinkfree.drive.resource.version.enums.ResourceVersionStatus;
import lombok.*;

import java.time.Instant;

/** 공개 전체 생성자는 JPQL 생성자 식용(ResourceVersionJpaRepository#findWithResource, 5차 패스) — 필드 순서를 바꾸면 JPQL 도 맞출 것 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceVersionWithResourceData {
    private Long resourceVersionSeq;
    private Integer userSeq;
    private Long resourceSeq;
    private String resourceName;
    private String versionResourceSaveName;
    private String resourceSaveName;
    private Integer versionNumber;
    private Long versionSizeByte;
    private Long sizeByte;
    private ResourceVersionStatus status;
    private Instant versionRegisterDate;
    private Integer registerUserSeq;
    private Instant registerDate;
}

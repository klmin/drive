package com.thinkfree.drive.resource.query;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Builder
@Getter
public class ResourceVersionRollbackQuery {
    
    private Long resourceSeq;
    private Integer userSeq;
    private Integer latestOwnerSeq;
    private Long resourceVersionSeq;
    private Integer currentVersionNumber;
    private Instant versionRegisterDate;
    private Long sizeByte;
    private Integer updateUserSeq;
    private Instant updateDate;
    /**
     * true = 버전 저장 실패 롤백 — 저장 전 수정자·수정일을 NULL 포함 그대로 복원한다(새 파일은 update_* 가 NULL).
     * false = 미완료 버전 정리(cleanVersionIfExists) — 버전 행의 값이 있을 때만 덮는다.
     */
    private boolean restoreUpdateMetadata;

}

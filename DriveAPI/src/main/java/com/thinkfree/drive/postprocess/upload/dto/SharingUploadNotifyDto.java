package com.thinkfree.drive.postprocess.upload.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class SharingUploadNotifyDto {
    private Long parentFolderSeq;
    private Long resourceSeq;
    private String folderName;
    private String fileName;
    private Integer userSeq;
    private String userName;
    private Integer actorUserSeq;
    private Instant occurredDatetime;
    private Boolean isDelete;
}

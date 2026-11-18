package com.thinkfree.drive.postprocess.orchestrator.dto;

import java.time.Instant;

public record PostProcessUploadDto
(
    Long parentFolderSeq,
    long resourceSeq,
    String folderName,
    String fileName,
    int userSeq,
    String userName,
    int actorUserSeq,
    Instant occurredDatetime,
    boolean isDelete
)
{
}

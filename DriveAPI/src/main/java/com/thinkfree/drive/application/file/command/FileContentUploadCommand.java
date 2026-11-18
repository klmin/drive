package com.thinkfree.drive.application.file.command;

/**
 * drive_upload_file 입력 — 내용은 textContent(UTF-8 평문) 또는 base64Content(비UTF-8 바이너리) 중 정확히 하나.
 * contentType·크기는 받지 않는다 — 확장자 기반 판정(Tika)과 디코딩 결과 바이트가 서버 계산이 더 정확하다.
 */
public record FileContentUploadCommand
(
    String fileName,
    String textContent,
    String base64Content,
    Long parentFolderSeq,
    Integer targetUserSeq
)
{
}

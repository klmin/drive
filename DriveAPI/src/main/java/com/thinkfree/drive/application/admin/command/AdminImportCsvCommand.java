package com.thinkfree.drive.application.admin.command;

import org.springframework.web.multipart.MultipartFile;

/**
 * CSV 사용자 일괄 등록 커맨드 — 웹 요청(AdminImportCsvRequest)과 필드 동형 (se → sendEmail).
 */
public record AdminImportCsvCommand
(
    MultipartFile file,
    Boolean sendEmail
)
{
}

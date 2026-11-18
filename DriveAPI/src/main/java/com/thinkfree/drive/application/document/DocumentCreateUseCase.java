package com.thinkfree.drive.application.document;

import com.thinkfree.drive.application.document.command.DocumentCreateCommand;
import com.thinkfree.drive.application.document.converter.DocumentCommandConverter;
import com.thinkfree.drive.application.document.info.DocumentCreateInfo;
import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 진입점(내부 API·MCP) 공용 새 문서 생성 usecase — 빈 템플릿(word/excel/ppt/note)으로 문서 파일을 만든다.
 * 템플릿 classpath와 문서명이 LocaleContextHolder 로케일을 따른다 — MCP 경로는 어스펙트가 userLanguage로 세팅.
 * 입출력은 application 소유의 Command/Info만 사용하며, 어댑터 객체(api.v1 request/response)를 알지 않는다.
 * 권한 검사는 어댑터 책임 — 웹은 controller aspect(@CheckResourcePermission), MCP는 툴의 명시 호출.
 * 쿼터 예약·실패 롤백·감사(FILE_CREATE)는 도메인 서비스(DefaultDocumentService)에 내장돼 있어 두 경로 모두 자동 커버.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentCreateUseCase {

    private final DocumentCommandConverter documentCommandConverter;
    private final DocumentService documentService;

    @AuditLog
    public DocumentCreateInfo create(DocumentCreateCommand command, int requestUserSeq) {
        var dto = documentCommandConverter.toCreateDto(command, requestUserSeq);
        var result = documentService.create(dto);
        return new DocumentCreateInfo(result.getResourceSeq(), result.getUserSeq());
    }

}

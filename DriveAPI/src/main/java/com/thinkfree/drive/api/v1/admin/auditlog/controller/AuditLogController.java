package com.thinkfree.drive.api.v1.admin.auditlog.controller;

import com.thinkfree.drive.api.page.request.PagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.auditlog.request.AuditLogSearchRequest;
import com.thinkfree.drive.api.v1.admin.auditlog.response.AuditLogCsvExport;
import com.thinkfree.drive.api.v1.admin.auditlog.response.AuditLogItemResponse;
import com.thinkfree.drive.api.v1.admin.auditlog.usecase.AuditLogQueryUseCase;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 감사 로그(거버넌스 증적) 조회 — 관리자용. 거버넌스 서버 조회 API를 Drive가 프록시한다
 * (프론트는 거버넌스를 직접 호출하지 않음: HMAC secret은 서버 사이드).
 * {@code governance.audit.enabled=true}일 때만 노출된다.
 */
@Tag(name = "Admin - Audit logs")
@RestController
@RequestMapping("/api/v1/admins/audit-logs")
@ConditionalOnProperty(prefix = "governance.audit", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogQueryUseCase auditLogQueryUseCase;

    @ExternalApi
    @Operation(summary = "List tenant audit events",
            description = "Returns a page of audit events matching the requested user, date, category, action, and channel filters.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AuditLogItemResponse>>> list(@ModelAttribute AuditLogSearchRequest request,
                                                                       @ModelAttribute @Valid PagingRequest pagingRequest) {
        var response = auditLogQueryUseCase.search(request, pagingRequest);
        return ApiResponse.success(response.getList(), response.getPage());
    }

    /**
     * 파일 전체를 힙에 들지 않도록 조각을 받는 즉시 흘려보낸다 — 크기를 미리 모르므로 Content-Length는 없다.
     * StreamingResponseBody(서블릿 비동기) 대신 요청 스레드에서 직접 쓰는 이유는
     * {@code spring.mvc.async.request-timeout}(5분)을 받지 않기 위해서다 — 기간이 길면 그보다 오래 걸린다.
     */
    @ExternalApi
    @Operation(summary = "Export tenant audit events",
            description = "Downloads matching audit events as CSV. The export covers at most 92 days and defaults to the most recent 92 days.")
    @GetMapping("/export")
    public void export(@ModelAttribute AuditLogSearchRequest request, HttpServletResponse response) throws IOException {
        AuditLogCsvExport export = auditLogQueryUseCase.export(request);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(export.fileName(), StandardCharsets.UTF_8)
                .build();
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition.toString());
        response.setContentType("text/csv");
        export.body().writeTo(response.getOutputStream());
    }
}

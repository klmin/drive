package com.thinkfree.drive.api.v1.upload.controller;

import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.upload.request.UploadSessionCreateRequest;
import com.thinkfree.drive.api.v1.upload.response.UploadSessionCancelResponse;
import com.thinkfree.drive.api.v1.upload.response.UploadSessionCompleteResponse;
import com.thinkfree.drive.api.v1.upload.response.UploadSessionCreateResponse;
import com.thinkfree.drive.api.v1.upload.response.UploadSessionResponse;
import com.thinkfree.drive.application.upload.UploadSessionUseCase;
import com.thinkfree.drive.application.upload.command.UploadSessionStartCommand;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.upload.session.data.UploadSessionData;
import com.thinkfree.drive.upload.session.enums.UploadSessionStatus;
import com.thinkfree.drive.upload.session.properties.UploadSessionProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * 청크 업로드(서버 경유 멀티파트) — docs/design/upload-session-redesign.md §5.3.
 * 파트 PUT 은 본문을 메모리·임시 파일에 담지 않고 요청 스트림을 스토리지로 그대로 흘린다({@code @RequestBody byte[]} 금지).
 * 단일 요청 기준(32MB) 미만 파일은 기존 {@code POST /api/v1/files} 를 쓴다.
 */
@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
@Tag(name = "Uploads")
public class UploadSessionController {

    private final UploadSessionUseCase uploadSessionUseCase;
    private final UploadSessionProperty uploadSessionProperty;

    @Operation(summary = "Start a chunked upload",
            description = "Creates an upload session for a large file and returns the part plan. Send each part with PUT, then call complete.")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<UploadSessionCreateResponse>> startUpload(@Valid @RequestBody UploadSessionCreateRequest request,
                                                                           @AuthenticationPrincipal CustomUserDetails details) {
        var command = new UploadSessionStartCommand(request.sharedByUserSeq(), request.parentFolderSeq(), request.resourceSeq(),
                request.fileName(), request.size(), request.saveType());
        var started = uploadSessionUseCase.start(command, details.getUserSeq());
        return ApiResponse.created(new UploadSessionCreateResponse(started.sessionId().toString(), started.partSize(), started.partCount(),
                uploadSessionProperty.maxConcurrentPartsPerSession()));
    }

    @Operation(summary = "Upload a part",
            description = "Uploads one part as a raw byte stream (application/octet-stream). Content-Length must equal the planned part size. Re-sending the same part number replaces it.")
    @PutMapping(value = "/{sessionId}/parts/{partNumber}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<ApiResponse<Void>> uploadPart(@PathVariable UUID sessionId, @PathVariable int partNumber,
                                                        HttpServletRequest request,
                                                        @AuthenticationPrincipal CustomUserDetails details) throws IOException {
        // getContentLengthLong: 헤더가 없거나 chunked 면 -1 → 411
        uploadSessionUseCase.uploadPart(sessionId, partNumber, request.getContentLengthLong(), request.getInputStream(), details.getUserSeq());
        return ApiResponse.success();
    }

    @Operation(summary = "Get an upload session",
            description = "Returns the session status and, while it is PENDING, the part numbers already uploaded so a client can resume by sending only the missing parts.")
    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<UploadSessionResponse>> getUploadSession(@PathVariable UUID sessionId, @AuthenticationPrincipal CustomUserDetails details) {
        var status = uploadSessionUseCase.status(sessionId, details.getUserSeq());
        return ApiResponse.success(toResponse(status.session(), status.uploadedParts()));
    }

    @Operation(summary = "List my upload sessions", description = "Lists my chunked upload sessions with the given status (PENDING by default), newest first.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<UploadSessionResponse>>> listUploadSessions(@RequestParam(required = false) UploadSessionStatus status,
                                                                         @AuthenticationPrincipal CustomUserDetails details) {
        var sessions = uploadSessionUseCase.list(status, details.getUserSeq());
        return ApiResponse.success(sessions.stream().map(s -> toResponse(s, List.of())).toList());
    }

    @Operation(summary = "Complete a chunked upload",
            description = "Verifies all parts, assembles the file and saves it. Calling it again for a completed session returns the same result.")
    @PostMapping("/{sessionId}/complete")
    public ResponseEntity<ApiResponse<UploadSessionCompleteResponse>> completeUpload(@PathVariable UUID sessionId, @AuthenticationPrincipal CustomUserDetails details) {
        var completed = uploadSessionUseCase.complete(sessionId, details.getUserSeq());
        return ApiResponse.success(new UploadSessionCompleteResponse(completed.result().resourceSeq(), completed.result().resourceName()));
    }

    @Operation(summary = "Cancel a chunked upload",
            description = "Cancels the upload and discards uploaded parts. Succeeds even if the session has already ended.")
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<UploadSessionCancelResponse>> cancelUpload(@PathVariable UUID sessionId, @AuthenticationPrincipal CustomUserDetails details) {
        var status = uploadSessionUseCase.cancel(sessionId, details.getUserSeq());
        return ApiResponse.success(new UploadSessionCancelResponse(status.name()));
    }

    private static UploadSessionResponse toResponse(UploadSessionData s, List<Integer> uploadedParts) {
        return new UploadSessionResponse(s.uploadSessionId().toString(), s.status().name(), s.failReason() == null ? null : s.failReason().name(),
                s.saveType().name(), s.fileName(), s.sizeByte(), s.parentFolderSeq(), s.resourceSeq(), s.partSize(), s.partCount(), uploadedParts,
                s.resultResourceSeq(), s.resultName(), s.lastActivity(), s.createDate());
    }
}

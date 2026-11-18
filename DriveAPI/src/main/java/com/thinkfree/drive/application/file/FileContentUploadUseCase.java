package com.thinkfree.drive.application.file;

import com.thinkfree.drive.application.file.command.FileContentUploadCommand;
import com.thinkfree.drive.application.file.converter.FileCommandConverter;
import com.thinkfree.drive.application.file.info.FileContentUploadInfo;
import com.thinkfree.drive.application.file.properties.FileContentProperty;
import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.common.file.multipart.CustomMultipartFile;
import com.thinkfree.drive.file.write.orchestrator.FileWriteOrchestrator;
import com.thinkfree.drive.messagesource.service.MessageSourceService;
import com.thinkfree.drive.util.MapStructUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static com.thinkfree.drive.messagesource.keys.MessageKeys.FILE_WRITE_CONTENT_REQUIRED;
import static com.thinkfree.drive.messagesource.keys.MessageKeys.FILE_WRITE_INVALID_BASE64;
import static com.thinkfree.drive.messagesource.keys.MessageKeys.FILE_WRITE_SIZE_EXCEEDED;

/**
 * 진입점 공용 파일 내용 업로드 usecase — LLM이 넘긴 내용(평문 또는 base64)을 새 파일로 저장한다(drive_upload_file).
 * 상한은 디코딩 후 바이트 단일 게이트(file.content.write-max-size) — 내용이 모델 출력 토큰(≤128K)을 통과해야
 * 도착하므로 문자 수 게이트는 두지 않는다(모델 출력이 먼저 잘린다). contentType은 확장자 기반 서버 판정
 * (Tika detect(파일명) — 내용 감지가 아니라 확장자 사전 조회. 웹 업로드의 브라우저 유래 값과 일관되고,
 * 미리보기·아이콘·편집 판정이 전부 확장자 기준이라 내용 감지는 오히려 메타만 어긋나게 한다).
 * 저장 파이프라인(중복 넘버링·쿼터·크기 정책·후처리)은 FileWriteOrchestrator + DUPLICATE 핸들러 재사용 —
 * 검증 메시지는 LocaleContextHolder 로케일을 따른다(MCP 경로는 어스펙트가 userLanguage로 세팅).
 * 권한 검사(UPLOAD)는 어댑터 책임 — MCP는 툴의 명시 호출. 덮어쓰기(기존 파일 내용 수정)는 범위 밖(v1 신규 생성만).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileContentUploadUseCase {

    // detect(파일명)만 사용 — 매직바이트 감지가 아니라 확장자 → MIME 사전 조회라 스레드 세이프한 파사드 재사용
    private static final Tika TIKA = new Tika();

    private final FileCommandConverter fileCommandConverter;
    private final FileWriteOrchestrator fileWriteOrchestrator;
    private final ResourceAuditService resourceAuditService;
    private final FileContentProperty fileContentProperty;
    private final MessageSourceService messageSourceService;

    @AuditLog
    public FileContentUploadInfo upload(FileContentUploadCommand command, int requestUserSeq) {

        int userSeq = MapStructUtil.resolveUserSeq(command.targetUserSeq(), requestUserSeq);
        long maxBytes = fileContentProperty.writeMaxSize().toBytes();

        boolean hasText = StringUtils.hasText(command.textContent());
        boolean hasBinary = StringUtils.hasText(command.base64Content());
        if (hasText == hasBinary) { // 둘 다 옴 / 둘 다 없음 — 상호배타 위반
            throw new IllegalArgumentException(messageSourceService.getMessage(FILE_WRITE_CONTENT_REQUIRED));
        }

        byte[] bytes = hasText
                ? command.textContent().getBytes(StandardCharsets.UTF_8)
                : decodeBase64Bounded(command.base64Content(), maxBytes);
        if (bytes == null || bytes.length > maxBytes) {
            throw new IllegalArgumentException(messageSourceService.getMessage(FILE_WRITE_SIZE_EXCEEDED,
                    new Object[]{ fileContentProperty.writeMaxSize().toKilobytes() + "KB" }));
        }

        String contentType = detectContentType(command.fileName(), hasText);
        MultipartFile file = new CustomMultipartFile(bytes, command.fileName(), command.fileName(), contentType);
        var dto = fileCommandConverter.toContentUploadDto(command.parentFolderSeq(), userSeq, requestUserSeq,
                command.fileName(), contentType, file);

        var result = fileWriteOrchestrator.write(dto, requestUserSeq);

        resourceAuditService.record(EventName.FILE_UPLOAD, result.resourceSeq(), userSeq);

        // 확장자 = 저장한 ext(번호가 붙은 최종 이름이 아니라)
        return new FileContentUploadInfo(result.resourceSeq(), result.resourceName(), (long) bytes.length, userSeq, dto.getExt());
    }

    /**
     * 상한+1바이트만 디코딩해 초과를 싸게 판정한다(read의 readBounded 대칭) — 초과분은 아예 디코딩하지 않아
     * 상한 검사 전에 메모리를 소진하는 일이 없다. MIME 디코더는 개행·공백을 무시한다(LLM 출력 관용).
     * 잘못된 base64(모델 출력이 max_tokens에서 잘린 경우가 실전 주 경로)는 검증 실패로 접는다.
     */
    private byte[] decodeBase64Bounded(String base64, long maxBytes) {
        InputStream in = Base64.getMimeDecoder()
                .wrap(new ByteArrayInputStream(base64.getBytes(StandardCharsets.US_ASCII)));
        try (in) {
            byte[] bytes = in.readNBytes((int) maxBytes + 1);
            return bytes.length > maxBytes ? null : bytes; // null = 초과 신호 (호출부에서 크기 초과로 판정)
        } catch (IOException e) {
            throw new IllegalArgumentException(messageSourceService.getMessage(FILE_WRITE_INVALID_BASE64));
        }
    }

    private String detectContentType(String fileName, boolean isText) {
        String detected = TIKA.detect(fileName);
        // 확장자 사전에 없는 확장자(hwp 등)는 octet-stream — 평문으로 받은 내용이면 text/plain이 실체에 가깝다
        if (isText && MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(detected)) {
            return MediaType.TEXT_PLAIN_VALUE;
        }
        return detected;
    }

}

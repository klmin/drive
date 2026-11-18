package com.thinkfree.drive.application.file;

import com.thinkfree.drive.application.file.command.FileReadCommand;
import com.thinkfree.drive.application.file.converter.FileCommandConverter;
import com.thinkfree.drive.application.file.info.FileReadInfo;
import com.thinkfree.drive.application.file.properties.FileContentProperty;
import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.messagesource.service.MessageSourceService;
import com.thinkfree.drive.resource.data.ResourceDetailData;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.util.MapStructUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;

import static com.thinkfree.drive.messagesource.keys.MessageKeys.FILE_READ_EXTRACT_EMPTY;
import static com.thinkfree.drive.messagesource.keys.MessageKeys.FILE_READ_EXTRACT_FAILED;
import static com.thinkfree.drive.messagesource.keys.MessageKeys.FILE_READ_OFFSET_OUT_OF_RANGE;
import static com.thinkfree.drive.messagesource.keys.MessageKeys.FILE_READ_SIZE_EXCEEDED;
import static com.thinkfree.drive.messagesource.keys.MessageKeys.FILE_READ_UNSUPPORTED_FORMAT;

/**
 * 진입점 공용 파일 읽기 usecase — 파일 본문을 평문 텍스트로 반환한다(LLM 소비용).
 * 텍스트 계열은 문자 디코딩, 오피스/PDF 계열은 Tika 추출 — 두 경로 모두 결과는 평문 text다.
 * 스코프·감사(FILE_PREVIEW)는 FileDownloadUseCase.view(웹 미리보기)와 동형이되, 반환물이
 * 스트림이 아니라 텍스트라는 점만 다르다. 추출 불가(미지원 형식·크기 초과·파싱 실패)는
 * 실패가 아니라 사유가 담긴 정상 결과다 — 화면 안내(웹 링크) 폴백은 어댑터 책임.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileReadUseCase {

    // 본문을 평문으로 반환하는 확장자 — 미리보기 룰셋의 텍스트 계열(일반 텍스트+코드/마크업) ∪ 프런트 txt군
    // + csv/tsv(화면은 오피스로 열지만 실체는 평문). 전부 파서 없이 디코딩만으로 읽히는 것들이다.
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "log", "md", "json", "xml", "ini", "conf", "html", "htm", "css", "php", "java", "py", "c",
            "cpp", "js", "ts", "tsv", "csv", "yaml", "yml");

    // 본문을 Tika로 추출해 반환하는 확장자 — 실체가 zip/바이너리라 디코딩이 불가능한 문서 계열.
    // rtf는 평문 디코딩도 되지만 서식 마크업이 섞여 나와 추출 계열로 분류한다.
    // hwp(v5)는 Tika 내장 전용 파서(HwpV5Parser, OLE2 판별)가 있어 편입 — jar 실측으로 존재 확인(3.2.3).
    // cell/show(OOXML 기반설)·hwpx(전용 파서 없음 — zip 상위 타입 폴백의 범용 XML 추출 기대)는 실험적 편입.
    // 실험 계열은 실패하면 추출 실패 reason 폴백으로 안전하게 떨어진다(실물 샘플 실측 필요).
    // Apple(pages 등)은 Tika 미지원이라 여기 없다 — 추출 불가로 분류돼 웹 링크 폴백.
    private static final Set<String> EXTRACT_EXTENSIONS = Set.of(
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "pdf", "rtf",
            "hwp", "hwpx", "cell", "show");

    // Tika 파사드는 스레드 세이프 — 기본 반환 상한(10만자)을 해제한다(원본 10MB 상한이 메모리를 이미 결속)
    private static final Tika TIKA = createTika();

    private final FileCommandConverter fileCommandConverter;
    private final ResourceFindService resourceFindService;
    private final StorageService storageService;
    private final ResourceAuditService resourceAuditService;
    // LLM 컨텍스트 보호 상한(file.content.*) — 원본 바이트는 추출 시도 자체를 막고, 반환 문자 수는 offset 이어읽기로 보완한다
    private final FileContentProperty fileContentProperty;
    // reason·검증 메시지는 LocaleContextHolder 로케일을 따른다 — MCP 경로는 인터셉터가 userLanguage로 세팅
    private final MessageSourceService messageSourceService;

    @AuditLog
    public FileReadInfo read(FileReadCommand command, int requestUserSeq) {

        int userSeq = MapStructUtil.resolveUserSeq(command.sharedByUserSeq(), requestUserSeq);

        // 웹 미리보기(view)와 동일 스코프 — FILE 한정(폴더 seq는 여기서 걸러진다), isDelete=ANY
        var query = fileCommandConverter.toFindQuery(command.resourceSeq(), userSeq, null);
        var resource = resourceFindService.findDetail(query);

        String extension = resource.getExt() == null ? null : resource.getExt().toLowerCase();

        boolean isText = extension != null && TEXT_EXTENSIONS.contains(extension);
        boolean isExtract = extension != null && EXTRACT_EXTENSIONS.contains(extension);
        if (!isText && !isExtract) {
            return unavailable(resource, extension, userSeq, messageSourceService.getMessage(FILE_READ_UNSUPPORTED_FORMAT));
        }
        if (resource.getSizeByte() != null && resource.getSizeByte() > fileContentProperty.readMaxSize().toBytes()) {
            return unavailable(resource, extension, userSeq, messageSourceService.getMessage(FILE_READ_SIZE_EXCEEDED));
        }

        byte[] bytes = readBounded(resource.getResourceSaveName());
        if (bytes == null) { // DB sizeByte와 실제 크기가 어긋난 경우의 안전망
            return unavailable(resource, extension, userSeq, messageSourceService.getMessage(FILE_READ_SIZE_EXCEEDED));
        }

        String text;
        if (isText) {
            text = decode(bytes);
        } else {
            text = extract(bytes);
            if (text == null) { // 암호화·손상 등 파싱 실패
                return unavailable(resource, extension, userSeq, messageSourceService.getMessage(FILE_READ_EXTRACT_FAILED));
            }
            if (text.isBlank()) { // 스캔본 PDF 등 — 글자가 픽셀로만 존재해 추출 결과가 비는 경우
                return unavailable(resource, extension, userSeq, messageSourceService.getMessage(FILE_READ_EXTRACT_EMPTY));
            }
        }

        // 본문이 실제로 노출되는 경우만 웹 미리보기와 동일하게 FILE_PREVIEW 적재
        resourceAuditService.record(EventName.FILE_PREVIEW, resource, userSeq);

        int offset = command.offset() == null ? 0 : command.offset();
        if (offset < 0 || (offset > 0 && offset >= text.length())) {
            throw new IllegalArgumentException(
                    messageSourceService.getMessage(FILE_READ_OFFSET_OUT_OF_RANGE, new Object[]{ text.length() }));
        }
        int end = Math.min(text.length(), offset + fileContentProperty.readMaxChars());
        String slice = text.substring(offset, end);

        return new FileReadInfo(resource.getResourceName(), extension, resource.getSizeByte(), userSeq,
                slice, offset, slice.length(), text.length(), end < text.length(), null);
    }

    /** 상한 초과 시 null — 스토리지 실제 크기가 상한을 넘는지 경계 1바이트로 판별한다 */
    private byte[] readBounded(String resourceSaveName) {
        int maxBytes = Math.toIntExact(fileContentProperty.readMaxSize().toBytes());
        var fileData = storageService.getFileData(resourceSaveName);
        try (var content = fileData.content()) {
            byte[] bytes = content.readNBytes(maxBytes + 1);
            return bytes.length > maxBytes ? null : bytes;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Tika createTika() {
        Tika tika = new Tika();
        tika.setMaxStringLength(-1);
        return tika;
    }

    /**
     * Tika 추출 — 실패(암호화·손상·미지원 변형)는 예외 전파 대신 null로 접어 사유 있는 정상 결과로 흘린다.
     * 손상 파일에서 파서가 TikaException 외에 비검사 예외를 던지는 경우가 있어 Exception 전체를 접는다.
     */
    private String extract(byte[] bytes) {
        try {
            return TIKA.parseToString(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            log.info("본문 텍스트 추출 실패", e);
            return null;
        }
    }

    // 평문 디코딩(다국어 3단: UTF-8 엄격 검증 → ICU 신뢰도 문턱 → MS949 기본값) — 근거·실측은 PlainTextDecoder 참조
    private String decode(byte[] bytes) {
        return PlainTextDecoder.decode(bytes, fileContentProperty.charsetConfidenceThreshold());
    }

    private FileReadInfo unavailable(ResourceDetailData resource, String extension, int ownerUserSeq, String reason) {
        return new FileReadInfo(resource.getResourceName(), extension, resource.getSizeByte(), ownerUserSeq,
                null, null, null, null, null, reason);
    }

}

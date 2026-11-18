package com.thinkfree.drive.application.file;

import com.thinkfree.drive.application.file.command.FileDownloadCommand;
import com.thinkfree.drive.application.file.converter.FileCommandConverter;
import com.thinkfree.drive.application.file.info.FileDownloadContentInfo;
import com.thinkfree.drive.application.file.properties.FileContentProperty;
import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.common.file.model.FileData;
import com.thinkfree.drive.messagesource.service.MessageSourceService;
import com.thinkfree.drive.resource.data.ResourceDetailData;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.util.MapStructUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;

import static com.thinkfree.drive.messagesource.keys.MessageKeys.FILE_DOWNLOAD_SIZE_EXCEEDED;

/**
 * 진입점 공용 파일 내용 다운로드 usecase — 파일 원본 바이트를 base64 텍스트로 반환한다(LLM 경유 전달용).
 * 스코프·감사(FILE_DOWNLOAD)는 FileDownloadUseCase.download(웹 다운로드)와 동형이되, 반환물이
 * 스트림이 아니라 base64 문자열이라는 점만 다르다. FileReadUseCase(평문 추출)와 달리 형식 제한은
 * 없고 크기 상한(file.content.download-max-size, 구글 커넥터 10MB 동형)만 있다 — 초과는 실패가
 * 아니라 사유가 담긴 정상 결과다. 화면 안내(웹 링크) 폴백은 어댑터 책임.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileDownloadContentUseCase {

    private final FileCommandConverter fileCommandConverter;
    private final ResourceFindService resourceFindService;
    private final StorageService storageService;
    private final ResourceAuditService resourceAuditService;
    private final FileContentProperty fileContentProperty;
    // reason 메시지는 LocaleContextHolder 로케일을 따른다 — MCP 경로는 인터셉터가 userLanguage로 세팅
    private final MessageSourceService messageSourceService;

    @AuditLog
    public FileDownloadContentInfo downloadContent(FileDownloadCommand command, int requestUserSeq) {

        int userSeq = MapStructUtil.resolveUserSeq(command.sharedByUserSeq(), requestUserSeq);

        // 웹 다운로드(download)와 동일 스코프 — FILE 한정(폴더 seq는 여기서 걸러진다), isDelete=ANY
        var query = fileCommandConverter.toFindQuery(command.resourceSeq(), userSeq, null);
        var resource = resourceFindService.findDetail(query);

        String extension = resource.getExt() == null ? null : resource.getExt().toLowerCase();

        if (resource.getSizeByte() != null && resource.getSizeByte() > fileContentProperty.downloadMaxSize().toBytes()) {
            return unavailable(resource, extension, userSeq);
        }

        var fileData = storageService.getFileData(resource.getResourceSaveName());
        byte[] bytes = readBounded(fileData);
        if (bytes == null) { // DB sizeByte와 실제 크기가 어긋난 경우의 안전망
            return unavailable(resource, extension, userSeq);
        }

        // 내용이 실제로 노출되는 경우만 웹 다운로드와 동일하게 FILE_DOWNLOAD 적재
        resourceAuditService.record(EventName.FILE_DOWNLOAD, resource, userSeq);

        return new FileDownloadContentInfo(resource.getResourceName(), extension, resource.getSizeByte(), userSeq,
                fileData.contentType(), Base64.getEncoder().encodeToString(bytes), null);
    }

    /** 상한 초과 시 null — 스토리지 실제 크기가 상한을 넘는지 경계 1바이트로 판별한다 */
    private byte[] readBounded(FileData fileData) {
        int maxBytes = Math.toIntExact(fileContentProperty.downloadMaxSize().toBytes());
        try (var content = fileData.content()) {
            byte[] bytes = content.readNBytes(maxBytes + 1);
            return bytes.length > maxBytes ? null : bytes;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private FileDownloadContentInfo unavailable(ResourceDetailData resource, String extension, int ownerUserSeq) {
        return new FileDownloadContentInfo(resource.getResourceName(), extension, resource.getSizeByte(), ownerUserSeq,
                null, null, messageSourceService.getMessage(FILE_DOWNLOAD_SIZE_EXCEEDED));
    }

}

package com.thinkfree.drive.application.file;

import com.thinkfree.drive.application.file.command.FileDownloadCommand;
import com.thinkfree.drive.application.file.converter.FileCommandConverter;
import com.thinkfree.drive.application.file.info.FileStreamInfo;
import com.thinkfree.drive.application.resource.info.ResourceDownloadLinkInfo;
import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.download.service.DownloadTokenService;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.util.MapStructUtil;
import com.thinkfree.drive.util.ResourceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 진입점 공용 파일 다운로드 usecase — 단일 파일 스트림 작성·미리보기·다운로드 토큰 발급
 * (전송 결합분 분리, FolderDownloadUseCase 등가 패턴).
 * 스트림은 FileStreamInfo(전송 중립)로 반환하며, ContentDisposition·MediaType 조립과
 * StreamingResponseBody 래핑·다운로드 URL 조립은 어댑터의 converter 책임이다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileDownloadUseCase {

    private final FileCommandConverter fileCommandConverter;
    private final ResourceFindService resourceFindService;
    private final StorageService storageService;
    private final DownloadTokenService downloadTokenService;
    private final ResourceAuditService resourceAuditService;

    public FileStreamInfo download(FileDownloadCommand command, int requestUserSeq) {
        return streamInternal(command.resourceSeq(), command.sharedByUserSeq(), requestUserSeq, null);
    }

    public FileStreamInfo downloadByToken(String token) {
        var payload = downloadTokenService.validateAndParse(token);

        var command = new FileDownloadCommand(payload.resourceSeq(), payload.sharedByUserSeq());
        return download(command, payload.requestUserSeq());
    }

    /** 인텔릭트 문서 열기 — 다운로드와 같은 스트림을 내려주고 FILE_OPEN으로 기록한다. */
    @AuditLog
    public FileStreamInfo open(FileDownloadCommand command, int requestUserSeq) {
        return streamInternal(command.resourceSeq(), command.sharedByUserSeq(), requestUserSeq, EventName.FILE_OPEN);
    }

    @AuditLog
    public FileStreamInfo view(FileDownloadCommand command, int requestUserSeq){
        return streamInternal(command.resourceSeq(), command.sharedByUserSeq(), requestUserSeq, EventName.FILE_PREVIEW);
    }

    @AuditLog
    public ResourceDownloadLinkInfo createSignedUrl(FileDownloadCommand command, int requestUserSeq) {

        Long resourceSeq = command.resourceSeq();
        Integer sharedByUserSeq = command.sharedByUserSeq();
        int userSeq = MapStructUtil.resolveUserSeq(sharedByUserSeq, requestUserSeq);

        // 1) 파일 존재 + 권한 체크 (DB 기반)
        var query = fileCommandConverter.toFindQuery(resourceSeq, userSeq, null);
        var resource = resourceFindService.findDetail(query);

        resourceAuditService.record(EventName.FILE_DOWNLOAD, resource, userSeq);

        // 2) 토큰 발급 (HMAC 기반 signed payload) — 다운로드 URL 조립은 어댑터가 한다
        String token = downloadTokenService.createSignedUrl(resourceSeq, userSeq, sharedByUserSeq, requestUserSeq);

        return new ResourceDownloadLinkInfo(token);
    }

    private FileStreamInfo streamInternal(Long resourceSeq, Integer sharedByUserSeq, int requestUserSeq, EventName eventName) {

        int userSeq = MapStructUtil.resolveUserSeq(sharedByUserSeq, requestUserSeq);

        var query = fileCommandConverter.toFindQuery(resourceSeq, userSeq, null);
        var resource = resourceFindService.findDetail(query);

        if (eventName != null) {
            resourceAuditService.record(eventName, resource, userSeq);
        }

        var fileData = storageService.getFileData(resource.getResourceSaveName());

        var fileStream = ResourceUtil.createFileStream(fileData);

        return new FileStreamInfo(resource.getResourceName(), fileData.contentLength(), fileData.contentType(), fileStream::writeTo);
    }

}

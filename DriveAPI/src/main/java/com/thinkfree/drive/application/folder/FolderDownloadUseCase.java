package com.thinkfree.drive.application.folder;

import com.thinkfree.drive.application.folder.command.FolderDownloadCommand;
import com.thinkfree.drive.application.folder.converter.FolderCommandConverter;
import com.thinkfree.drive.application.resource.converter.ResourceToZipEntryConverter;
import com.thinkfree.drive.application.resource.info.ResourceDownloadLinkInfo;
import com.thinkfree.drive.application.resource.info.ResourceZipStreamInfo;
import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.download.service.DownloadTokenService;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.util.DateUtil;
import com.thinkfree.drive.util.MapStructUtil;
import com.thinkfree.drive.util.ResourceUtil;
import com.thinkfree.drive.util.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 진입점 공용 폴더 다운로드 usecase — zip 스트림 작성과 다운로드 토큰 발급 (전송 결합분 분리, ResourceDownloadUseCase 등가 패턴).
 * 스트림은 ResourceZipStreamInfo.Writer(전송 중립)로 반환하며, StreamingResponseBody 래핑과
 * 다운로드 URL 조립은 어댑터의 converter 책임이다. zip Info/컨버터는 application.resource 소유분을 공용한다
 * (같은 계층의 값 객체 재사용 — usecase 간 호출 아님).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FolderDownloadUseCase {

    private final FolderCommandConverter folderCommandConverter;
    private final ResourceToZipEntryConverter resourceToZipEntryConverter;
    private final ResourceFindService resourceFindService;
    private final StorageService storageService;
    private final DownloadTokenService downloadTokenService;
    private final ResourceAuditService resourceAuditService;

    public ResourceZipStreamInfo download(FolderDownloadCommand command, int requestUserSeq) {

        int userSeq = MapStructUtil.resolveUserSeq(command.sharedByUserSeq(), requestUserSeq);

        var query = folderCommandConverter.toHierarchyQuery(List.of(command.resourceSeq()), userSeq);
        var list = resourceFindService.findResourceHierarchy(query);

        var now = DateUtil.format(Instant.now(), DriveConstants.YYYYMMDD_HHMMSS);
        var zipName = StringUtil.append(list.get(0).getResourceName(), DriveConstants.HYPHEN, now, DriveConstants.ZIP_EXTENSION);

        var entries = resourceToZipEntryConverter.toDownloadZipEntries(list);
        var zipStream = ResourceUtil.createZipStream(entries, storageService::getFileData);

        return new ResourceZipStreamInfo(zipName, zipStream::writeTo);
    }

    public ResourceZipStreamInfo downloadByToken(String token) {
        var payload = downloadTokenService.validateAndParse(token);

        var command = new FolderDownloadCommand(payload.resourceSeq(), payload.sharedByUserSeq());
        return download(command, payload.requestUserSeq());
    }

    @AuditLog
    public ResourceDownloadLinkInfo createSignedUrl(FolderDownloadCommand command, int requestUserSeq) {

        int userSeq = MapStructUtil.resolveUserSeq(command.sharedByUserSeq(), requestUserSeq);

        // 1) 파일 존재 + 권한 체크 (DB 기반)
        var query = folderCommandConverter.toHierarchyQuery(List.of(command.resourceSeq()), userSeq);
        var list = resourceFindService.findResourceHierarchy(query);

        var folder = list.get(0);
        resourceAuditService.record(EventName.FOLDER_DOWNLOAD, folder, userSeq);

        // 2) 토큰 발급 (HMAC 기반 signed payload) — 다운로드 URL 조립은 어댑터가 한다
        String token = downloadTokenService.createSignedUrl(command.resourceSeq(), userSeq, command.sharedByUserSeq(), requestUserSeq);

        return new ResourceDownloadLinkInfo(token);
    }

}

package com.thinkfree.drive.application.version;

import com.thinkfree.drive.application.file.info.FileStreamInfo;
import com.thinkfree.drive.application.resource.info.ResourceDownloadLinkInfo;
import com.thinkfree.drive.application.version.command.VersionDownloadCommand;
import com.thinkfree.drive.application.version.converter.VersionCommandConverter;
import com.thinkfree.drive.download.service.DownloadTokenService;
import com.thinkfree.drive.resource.version.service.ResourceVersionFindService;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.util.MapStructUtil;
import com.thinkfree.drive.util.ResourceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 진입점 공용 버전 다운로드 usecase — 버전 스트림 작성·다운로드 토큰 발급
 * (전송 결합분 분리, FileDownloadUseCase 등가 패턴).
 * 스트림은 FileStreamInfo(전송 중립)로 반환하며, ContentDisposition·MediaType 조립과
 * StreamingResponseBody 래핑·다운로드 URL 조립은 어댑터의 converter 책임이다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VersionDownloadUseCase {

    private final VersionCommandConverter versionCommandConverter;
    private final ResourceVersionFindService resourceVersionFindService;
    private final StorageService storageService;
    private final DownloadTokenService downloadTokenService;

    public FileStreamInfo download(VersionDownloadCommand command, int requestUserSeq) {
        int userSeq = MapStructUtil.resolveUserSeq(command.sharedByUserSeq(), requestUserSeq);
        var query = versionCommandConverter.toFindQuery(command.resourceVersionSeq(), command.resourceSeq(), userSeq);
        var data = resourceVersionFindService.findResourceVersionWithResource(query);
        var fileData = storageService.getFileData(data.getVersionResourceSaveName());
        var fileStream = ResourceUtil.createFileStream(fileData);
        return new FileStreamInfo(data.getResourceName(), fileData.contentLength(), fileData.contentType(), fileStream::writeTo);
    }

    public FileStreamInfo downloadByToken(String token) {
        var payload = downloadTokenService.validateAndParseVersion(token);

        var command = new VersionDownloadCommand(payload.resourceSeq(), payload.resourceVersionSeq(), payload.sharedByUserSeq());
        return download(command, payload.requestUserSeq());
    }

    public ResourceDownloadLinkInfo createSignedUrl(VersionDownloadCommand command, int requestUserSeq) {

        int userSeq = MapStructUtil.resolveUserSeq(command.sharedByUserSeq(), requestUserSeq);

        // 1) 버전 존재 + 권한 체크 (DB 기반)
        var query = versionCommandConverter.toFindQuery(command.resourceVersionSeq(), command.resourceSeq(), userSeq);
        resourceVersionFindService.findResourceVersionWithResource(query);

        // 2) 토큰 발급 (HMAC 기반 signed payload) — 다운로드 URL 조립은 어댑터가 한다
        String token = downloadTokenService.createSignedUrlForVersion(command.resourceSeq(), command.resourceVersionSeq(), command.sharedByUserSeq(), requestUserSeq);

        return new ResourceDownloadLinkInfo(token);
    }

}

package com.thinkfree.drive.application.resource;

import com.thinkfree.drive.application.resource.command.ResourceDownloadCommand;
import com.thinkfree.drive.application.resource.converter.ResourceCommandConverter;
import com.thinkfree.drive.application.resource.converter.ResourceToZipEntryConverter;
import com.thinkfree.drive.application.resource.info.ResourceDownloadLinkInfo;
import com.thinkfree.drive.application.resource.info.ResourceZipStreamInfo;
import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.download.service.DownloadTokenService;
import com.thinkfree.drive.download.token.MultiDownloadPayload;
import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.permission.enums.SharePermissionType;
import com.thinkfree.drive.permission.service.ResourcePermissionService;
import com.thinkfree.drive.permission.support.RequestActor;
import com.thinkfree.drive.resource.data.ResourceHierarchyData;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.util.DateUtil;
import com.thinkfree.drive.util.ResourceUtil;
import com.thinkfree.drive.util.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 진입점 공용 다운로드 usecase — zip 스트림 작성과 다운로드 토큰 발급.
 * 스트림은 ResourceZipStreamInfo.Writer(전송 중립)로 반환하며, StreamingResponseBody 래핑과
 * 다운로드 URL 조립은 각 어댑터의 converter 책임이다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceDownloadUseCase {

    private final ResourceCommandConverter resourceCommandConverter;
    private final ResourceToZipEntryConverter resourceToZipEntryConverter;
    private final ResourceFindService resourceFindService;
    private final StorageService storageService;
    private final ResourcePermissionService resourcePermissionService;
    private final DownloadTokenService downloadTokenService;
    private final ResourceAuditService resourceAuditService;

    public ResourceZipStreamInfo download(ResourceDownloadCommand command, int requestUserSeq) {
        var entries = resourceToZipEntryConverter.toDownloadZipEntries(resolveHierarchy(command, requestUserSeq));
        var now = DateUtil.format(Instant.now(), DriveConstants.YYYYMMDD_HHMMSS);
        var zipName = StringUtil.append(DriveConstants.DEFAULT_ZIP_NAME, DriveConstants.HYPHEN, now, DriveConstants.ZIP_EXTENSION);
        var zipStream = ResourceUtil.createZipStream(entries, storageService::getFileData);
        return new ResourceZipStreamInfo(zipName, zipStream::writeTo);
    }

    public ResourceZipStreamInfo downloadMultiByToken(String token) {

        var payload = downloadTokenService.validateAndParseMulti(token);

        int requestUserSeq = payload.requestUserSeq();
        List<ResourceDownloadCommand.DownloadItem> items = payload.rl().stream()
                .map(it -> new ResourceDownloadCommand.DownloadItem(it.ss(), it.rs()))
                .toList();

        return download(new ResourceDownloadCommand(items), requestUserSeq);
    }

    @AuditLog
    public ResourceDownloadLinkInfo createSignedUrl(ResourceDownloadCommand command, int requestUserSeq, boolean anonymous) {
        // 1) 파일 존재 + 권한 체크 (DB 기반)
        var sharedItems = command.items().stream()
                .filter(item -> requestUserSeq != item.sharedByUserSeq())
                .toList();
        if (!sharedItems.isEmpty()) {
            var sharedPartitionKeys = sharedItems.stream()
                    .map(d -> new PartitionKey(d.sharedByUserSeq(), d.resourceSeq()))
                    .toList();
            resourcePermissionService.checkPermissions(SharePermissionType.DOWNLOAD, sharedPartitionKeys,
                    RequestActor.of(requestUserSeq, anonymous), null);
        }
        var hierarchy = resolveHierarchy(command, requestUserSeq);

        // 사용자가 직접 선택한 항목(depth=0)만 fan-out. 폴더 선택 시 딸려온 하위 파일/폴더(depth>0)는 대상 아님.
        var auditTargets = hierarchy.stream().filter(item -> item.getDepth() == 0).toList();
        resourceAuditService.recordAll(auditTargets, requestUserSeq,
                item -> item.getIsFolder() ? EventName.FOLDER_DOWNLOAD : EventName.FILE_DOWNLOAD);

        // 2) 토큰 발급 — 다운로드 URL 조립은 어댑터가 한다
        var tokenItems = command.items().stream()
                .map(it -> new MultiDownloadPayload.DownloadItem(it.sharedByUserSeq(), it.resourceSeq()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new ResourceDownloadLinkInfo(downloadTokenService.createSignedUrlForMulti(tokenItems, requestUserSeq));
    }

    private List<ResourceHierarchyData> resolveHierarchy(ResourceDownloadCommand command, int requestUserSeq) {
        boolean hasSharedItems = command.items().stream().anyMatch(item -> requestUserSeq != item.sharedByUserSeq());

        if (!hasSharedItems) {
            var resourceSeqList = command.items().stream().map(ResourceDownloadCommand.DownloadItem::resourceSeq).toList();
            return resourceFindService.findResourceHierarchy(
                    resourceCommandConverter.toHierarchyQuery(resourceSeqList, requestUserSeq));
        }

        var partitionKeys = command.items().stream()
                .map(d -> new PartitionKey(d.sharedByUserSeq(), d.resourceSeq()))
                .toList();
        return resourceFindService.findResourceHierarchyByPartitionKeys(partitionKeys, null);
    }

}

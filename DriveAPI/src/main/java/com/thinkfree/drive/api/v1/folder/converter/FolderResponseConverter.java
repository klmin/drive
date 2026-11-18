package com.thinkfree.drive.api.v1.folder.converter;

import com.thinkfree.drive.api.response.DownloadLinkResponse;
import com.thinkfree.drive.api.response.StreamingDownloadResponse;
import com.thinkfree.drive.api.v1.folder.response.FolderUploadResponse;
import com.thinkfree.drive.application.folder.info.FolderCreateInfo;
import com.thinkfree.drive.application.resource.info.ResourceDownloadLinkInfo;
import com.thinkfree.drive.application.resource.info.ResourceZipStreamInfo;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.util.ResourceUtil;
import org.mapstruct.Mapper;

/**
 * application Info → 웹 응답 변환 전담. 다운로드 URL 조립·StreamingResponseBody 복원은 어댑터 몫(ResourceResponseConverter 등가).
 */
@Mapper(componentModel = "spring")
public interface FolderResponseConverter {

    FolderUploadResponse toUploadResponse(FolderCreateInfo info);

    default DownloadLinkResponse toDownloadLinkResponse(ResourceDownloadLinkInfo info) {
        return DownloadLinkResponse.Folder(info.downloadToken());
    }

    default StreamingDownloadResponse toStreamingDownloadResponse(ResourceZipStreamInfo info) {
        return new StreamingDownloadResponse(
                ResourceUtil.createContentDisposition(info.zipFileName()),
                null,
                DriveConstants.APPLICATION_ZIP,
                info.writer()::writeTo);
    }

}

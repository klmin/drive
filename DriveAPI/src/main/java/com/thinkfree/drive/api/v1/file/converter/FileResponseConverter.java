package com.thinkfree.drive.api.v1.file.converter;

import com.thinkfree.drive.api.response.DownloadLinkResponse;
import com.thinkfree.drive.api.response.StreamingDownloadResponse;
import com.thinkfree.drive.api.v1.file.response.FileCopyResponse;
import com.thinkfree.drive.api.v1.file.response.FileRenameResponse;
import com.thinkfree.drive.api.v1.file.response.FileUploadResponse;
import com.thinkfree.drive.application.file.info.FileCopyInfo;
import com.thinkfree.drive.application.file.info.FileStreamInfo;
import com.thinkfree.drive.application.file.info.FileUploadInfo;
import com.thinkfree.drive.application.resource.info.ResourceDownloadLinkInfo;
import com.thinkfree.drive.application.resource.info.ResourceRenameInfo;
import com.thinkfree.drive.util.ResourceUtil;
import org.mapstruct.Mapper;
import org.springframework.http.MediaType;

/**
 * application Info → 웹 응답 변환 전담. 다운로드 URL 조립·StreamingResponseBody 복원은 어댑터 몫(FolderResponseConverter 등가).
 */
@Mapper(componentModel = "spring")
public interface FileResponseConverter {

    FileUploadResponse toUploadResponse(FileUploadInfo info);

    FileRenameResponse toRenameResponse(ResourceRenameInfo info);

    FileCopyResponse toCopyResponse(FileCopyInfo info);

    default DownloadLinkResponse toDownloadLinkResponse(ResourceDownloadLinkInfo info) {
        return DownloadLinkResponse.File(info.downloadToken());
    }

    default StreamingDownloadResponse toStreamingDownloadResponse(FileStreamInfo info) {
        return new StreamingDownloadResponse(
                ResourceUtil.createContentDisposition(info.fileName()),
                info.contentLength(),
                MediaType.parseMediaType(info.contentType()),
                info.writer()::writeTo);
    }

}

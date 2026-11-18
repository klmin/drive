package com.thinkfree.drive.api.v1.resource.version.converter;

import com.thinkfree.drive.api.response.DownloadLinkResponse;
import com.thinkfree.drive.api.response.StreamingDownloadResponse;
import com.thinkfree.drive.api.v1.resource.version.response.ResourceVersionListResponse;
import com.thinkfree.drive.application.file.info.FileStreamInfo;
import com.thinkfree.drive.application.resource.info.ResourceDownloadLinkInfo;
import com.thinkfree.drive.application.version.info.VersionListInfo;
import com.thinkfree.drive.util.ResourceUtil;
import org.mapstruct.Mapper;
import org.springframework.http.MediaType;

import java.util.List;

/**
 * application Info → 웹 응답 변환 전담. 다운로드 URL 조립·StreamingResponseBody 복원은 어댑터 몫(FileResponseConverter 등가).
 */
@Mapper(componentModel = "spring")
public interface ResourceVersionResponseConverter {

    List<ResourceVersionListResponse> toListResponses(List<VersionListInfo> infos);

    default DownloadLinkResponse toDownloadLinkResponse(Long resourceSeq, ResourceDownloadLinkInfo info) {
        return DownloadLinkResponse.Version(resourceSeq, info.downloadToken());
    }

    default StreamingDownloadResponse toStreamingDownloadResponse(FileStreamInfo info) {
        return new StreamingDownloadResponse(
                ResourceUtil.createContentDisposition(info.fileName()),
                info.contentLength(),
                MediaType.parseMediaType(info.contentType()),
                info.writer()::writeTo);
    }

}

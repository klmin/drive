package com.thinkfree.drive.api.v1.resource.converter;

import com.thinkfree.drive.api.page.CursorApiPage;
import com.thinkfree.drive.api.response.DownloadLinkResponse;
import com.thinkfree.drive.api.response.StreamingDownloadResponse;
import com.thinkfree.drive.api.v1.resource.response.ResourceDetailResponse;
import com.thinkfree.drive.api.v1.resource.response.ResourceExistsResponse;
import com.thinkfree.drive.api.v1.resource.response.ResourceListResponse;
import com.thinkfree.drive.api.v1.resource.response.ResourceShareLinkResponse;
import com.thinkfree.drive.api.v1.resource.response.ResourceTreeResponse;
import com.thinkfree.drive.application.page.CursorPageInfo;
import com.thinkfree.drive.application.resource.info.ResourceDetailInfo;
import com.thinkfree.drive.application.resource.info.ResourceDownloadLinkInfo;
import com.thinkfree.drive.application.resource.info.ResourceExistsInfo;
import com.thinkfree.drive.application.resource.info.ResourceListInfo;
import com.thinkfree.drive.application.resource.info.ResourceShareLinkInfo;
import com.thinkfree.drive.application.resource.info.ResourceTreeInfo;
import com.thinkfree.drive.application.resource.info.ResourceZipStreamInfo;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.util.ResourceUtil;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * application Info → 웹 응답 변환 전담. 도메인 result → Info 조립은 ResourceInfoConverter가 담당한다.
 */
@Mapper(componentModel = "spring")
public interface ResourceResponseConverter {

    ResourceExistsResponse toExistsResponse(ResourceExistsInfo info);

    ResourceListResponse toListResponse(ResourceListInfo info);

    List<ResourceListResponse> toListResponse(List<ResourceListInfo> infos);

    ResourceDetailResponse toDetailResponse(ResourceDetailInfo info);

    List<ResourceTreeResponse> toResourceTreeResponse(List<ResourceTreeInfo> infos);

    ResourceShareLinkResponse toShareLinkResponse(ResourceShareLinkInfo info);

    default CursorApiPage<?> toCursorApiPage(CursorPageInfo<?> info) {
        return CursorApiPage.of(info.pageSize(), info.nextCursorValue(), info.nextCursor(), info.nextCursorType(), info.hasNext());
    }

    default StreamingDownloadResponse toStreamingDownloadResponse(ResourceZipStreamInfo info) {
        return new StreamingDownloadResponse(
                ResourceUtil.createContentDisposition(info.zipFileName()),
                null,
                DriveConstants.APPLICATION_ZIP,
                info.writer()::writeTo);
    }

    default DownloadLinkResponse toDownloadLinkResponse(ResourceDownloadLinkInfo info) {
        return DownloadLinkResponse.Multi(info.downloadToken());
    }

}

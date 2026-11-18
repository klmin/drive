package com.thinkfree.drive.application.recent;

import com.thinkfree.drive.application.page.CursorPageInfo;
import com.thinkfree.drive.application.recent.command.RecentListCommand;
import com.thinkfree.drive.application.recent.command.RecentSaveCommand;
import com.thinkfree.drive.application.recent.converter.RecentCommandConverter;
import com.thinkfree.drive.application.recent.converter.RecentInfoConverter;
import com.thinkfree.drive.application.recent.info.RecentListInfo;
import com.thinkfree.drive.recent.data.RecentResourceShareData;
import com.thinkfree.drive.recent.service.RecentFindService;
import com.thinkfree.drive.recent.service.RecentService;
import com.thinkfree.drive.resource.converter.ResourceConverter;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.util.MapStructUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 진입점(내부 API·외부 API·MCP) 공용 최근 문서함 usecase.
 * 입출력은 application 소유의 Command/Info만 사용하며, 어댑터 객체(api.v1 request/response)를 알지 않는다.
 * save는 웹에서는 front가 열람/생성 시 호출하고, MCP에서는 열람·생성 툴(read_file 등)이 어댑터에서 조합 호출한다.
 */
@Service
@RequiredArgsConstructor
public class RecentUseCase {

    private final RecentCommandConverter recentCommandConverter;
    private final RecentInfoConverter recentInfoConverter;
    private final ResourceConverter resourceConverter;
    private final RecentService recentService;
    private final ResourceFindService resourceFindService;
    private final RecentFindService recentFindService;

    public CursorPageInfo<RecentListInfo> findAllPaginated(RecentListCommand command, int requestUserSeq) {
        var query = recentCommandConverter.toListQuery(command, requestUserSeq);
        var list = recentFindService.findAll(query);
        return CursorPageInfo.of(recentInfoConverter.toListInfos(list), list, command.pageSize(),
                RecentResourceShareData::getRecentUpdateDate, RecentResourceShareData::getResourceSeq, null);
    }

    public void save(RecentSaveCommand command, int requestUserSeq) {
        int userSeq = MapStructUtil.resolveUserSeq(command.sharedByUserSeq(), requestUserSeq);
        var query = resourceConverter.toFindQuery(command.resourceSeq(), userSeq, null, null);
        resourceFindService.findDetail(query);
        var saveDto = recentCommandConverter.toSaveDto(command, userSeq, requestUserSeq);

        recentService.save(saveDto);
    }

}

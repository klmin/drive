package com.thinkfree.drive.application.starred;

import com.thinkfree.drive.application.page.CursorPageInfo;
import com.thinkfree.drive.application.starred.command.StarredListCommand;
import com.thinkfree.drive.application.starred.command.StarredSaveCommand;
import com.thinkfree.drive.application.starred.converter.StarredCommandConverter;
import com.thinkfree.drive.application.starred.converter.StarredInfoConverter;
import com.thinkfree.drive.application.starred.info.StarredListInfo;
import com.thinkfree.drive.common.search.enums.SortField;
import com.thinkfree.drive.resource.converter.ResourceConverter;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.starred.data.StarredResourceShareData;
import com.thinkfree.drive.starred.service.StarredFindService;
import com.thinkfree.drive.starred.service.StarredService;
import com.thinkfree.drive.util.MapStructUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 진입점(내부 API·외부 API·MCP) 공용 중요문서함 usecase.
 * 입출력은 application 소유의 Command/Info만 사용하며, 어댑터 객체(api.v1 request/response)를 알지 않는다.
 * save/delete는 웹에서는 즐겨찾기 토글이 호출하고, MCP에서는 편집 툴(edit_file)이 어댑터에서 조합 호출한다.
 */
@Service
@RequiredArgsConstructor
public class StarredUseCase {

    private final StarredCommandConverter starredCommandConverter;
    private final StarredInfoConverter starredInfoConverter;
    private final ResourceConverter resourceConverter;
    private final StarredService starredService;
    private final ResourceFindService resourceFindService;
    private final StarredFindService starredFindService;

    public CursorPageInfo<StarredListInfo> findAllPaginated(StarredListCommand command, int requestUserSeq) {
        var query = starredCommandConverter.toListQuery(command, requestUserSeq);
        var list = starredFindService.findAll(query);

        // SQL은 sortBy·sortOrder 미지정 시 NAME ASC로 정렬하므로 커서 값 추출도 같은 기본값을 따른다
        SortField sortField = command.sortBy() != null ? command.sortBy() : SortField.NAME;
        return CursorPageInfo.of(starredInfoConverter.toListInfos(list), list, command.pageSize(),
                sortField.getExtractor()::apply, StarredResourceShareData::getResourceSeq, StarredResourceShareData::getResourceType);
    }

    /** @return 생성된 중요문서함 sequence — 등록 직후 목록 재조회 없이 해당 행만 갱신할 수 있도록 반환 */
    public long save(StarredSaveCommand command, int requestUserSeq) {
        int userSeq = MapStructUtil.resolveUserSeq(command.sharedByUserSeq(), requestUserSeq);
        var query = resourceConverter.toFindQuery(command.resourceSeq(), userSeq, null, null);
        resourceFindService.findDetail(query);
        var saveDto = starredCommandConverter.toSaveDto(command, userSeq, requestUserSeq);

        return starredService.save(saveDto);
    }

    public void delete(long starredSeq, int userSeq) {
        starredService.delete(starredSeq, userSeq);
    }

}

package com.thinkfree.drive.api.v1.pinnedfolder.usecase;

import com.thinkfree.drive.api.v1.pinnedfolder.converter.PinnedFolderRequestConverter;
import com.thinkfree.drive.api.v1.pinnedfolder.converter.PinnedFolderResponseConverter;
import com.thinkfree.drive.api.v1.pinnedfolder.request.PinnedFolderSaveRequest;
import com.thinkfree.drive.api.v1.pinnedfolder.response.PinnedFolderListResponse;
import com.thinkfree.drive.pinnedfolder.service.PinnedFolderFindService;
import com.thinkfree.drive.pinnedfolder.service.PinnedFolderService;
import com.thinkfree.drive.resource.converter.ResourceConverter;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.util.MapStructUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PinnedFolderUseCase {
    
    private final PinnedFolderRequestConverter pinnedFolderRequestConverter;
    private final PinnedFolderResponseConverter pinnedFolderResponseConverter;
    private final ResourceConverter resourceConverter;
    private final ResourceFindService resourceFindService;
    private final PinnedFolderService pinnedFolderService;
    private final PinnedFolderFindService pinnedFolderFindService;
    
    public List<PinnedFolderListResponse> listPinnedFolders(int userSeq){
        var pinnedFolderListQuery = pinnedFolderRequestConverter.toPinnedFolderListQuery(userSeq);
        var list = pinnedFolderFindService.findPinnedFolders(pinnedFolderListQuery);
        return pinnedFolderResponseConverter.toPinnedFolderListResponse(list);
    }
    
    public void pinFolder(PinnedFolderSaveRequest request, int userSeq){
        var sharedByUserSeq = MapStructUtil.resolveUserSeq(request.ss(), userSeq);
        var pinnedFolderSaveDto = pinnedFolderRequestConverter.toPinnedFolderSaveDto(request, userSeq, sharedByUserSeq);
        var resourceFindQuery = resourceConverter.toFindQuery(pinnedFolderSaveDto.getResourceSeq(), sharedByUserSeq, ResourceType.FOLDER, false);
        resourceFindService.findDetail(resourceFindQuery);
        pinnedFolderService.pinFolder(pinnedFolderSaveDto);
    }
    
    public void unpinFolder(Long pinnedFolderSeq, int userSeq){
        var pinnedFolderDeleteQuery = pinnedFolderRequestConverter.toPinnedFolderDeleteQuery(userSeq, pinnedFolderSeq);
        pinnedFolderService.unpinFolder(pinnedFolderDeleteQuery);
    }
    
}

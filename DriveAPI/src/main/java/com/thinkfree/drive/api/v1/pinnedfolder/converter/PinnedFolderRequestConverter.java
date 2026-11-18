package com.thinkfree.drive.api.v1.pinnedfolder.converter;

import com.thinkfree.drive.api.v1.pinnedfolder.request.PinnedFolderSaveRequest;
import com.thinkfree.drive.pinnedfolder.dto.PinnedFolderSaveDto;
import com.thinkfree.drive.pinnedfolder.query.PinnedFolderDeleteQuery;
import com.thinkfree.drive.pinnedfolder.query.PinnedFolderListQuery;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PinnedFolderRequestConverter {
    
    PinnedFolderListQuery toPinnedFolderListQuery(Integer userSeq);
    
    @Mapping(target = "resourceSeq", source = "request.rs")
    @Mapping(target = "sharedByUserSeq", source = "sharedByUserSeq")
    PinnedFolderSaveDto toPinnedFolderSaveDto(PinnedFolderSaveRequest request, int userSeq, int sharedByUserSeq);
    
    PinnedFolderDeleteQuery toPinnedFolderDeleteQuery(Integer userSeq, Long pinnedFolderSeq);
    
}

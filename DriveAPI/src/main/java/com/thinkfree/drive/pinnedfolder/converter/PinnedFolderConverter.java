package com.thinkfree.drive.pinnedfolder.converter;

import com.thinkfree.drive.pinnedfolder.dto.PinnedFolderSaveDto;
import com.thinkfree.drive.pinnedfolder.entity.PinnedFolder;
import com.thinkfree.drive.pinnedfolder.query.PinnedFolderCountQuery;
import com.thinkfree.drive.pinnedfolder.query.PinnedFolderExistsQuery;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PinnedFolderConverter {
    
    PinnedFolderCountQuery toPinnedFolderCountQuery(Integer userSeq);
    PinnedFolderExistsQuery toPinnedFolderExistsQuery(Integer userSeq, Long resourceSeq, Integer sharedByUserSeq);
    PinnedFolder toSavePinnedFolder(PinnedFolderSaveDto dto);
}

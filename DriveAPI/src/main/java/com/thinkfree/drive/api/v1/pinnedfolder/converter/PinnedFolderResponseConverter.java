package com.thinkfree.drive.api.v1.pinnedfolder.converter;

import com.thinkfree.drive.api.v1.pinnedfolder.response.PinnedFolderListResponse;
import com.thinkfree.drive.pinnedfolder.data.PinnedFolderData;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PinnedFolderResponseConverter {
    
    List<PinnedFolderListResponse> toPinnedFolderListResponse(List<PinnedFolderData> list);
  
    
}

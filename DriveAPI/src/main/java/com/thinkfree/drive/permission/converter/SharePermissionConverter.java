package com.thinkfree.drive.permission.converter;

import com.thinkfree.drive.permission.query.SharePermissionQuery;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SharePermissionConverter {
    
    SharePermissionQuery toSharePermissionQuery(Long shareSeq, Integer userSeq, Integer requestUserSeq, Boolean isDelete);
    
}

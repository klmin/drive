package com.thinkfree.drive.cleanup.user.converter;

import com.thinkfree.drive.cleanup.user.service.dto.UserCleanUpDto;
import com.thinkfree.drive.user.query.UserDeleteMarkQuery;
import com.thinkfree.drive.user.query.UserPendingDeleteTargetQuery;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.util.List;

@Mapper(componentModel = "spring", imports = {Instant.class})
public interface UserCleanUpConverter {
    
    UserPendingDeleteTargetQuery toUserPendingDeleteTargetQuery(UserCleanUpDto dto);
    
    @Mapping(target="deleteDate", expression = "java(Instant.now())")
    UserDeleteMarkQuery toUserDeleteMarkQuery(List<Integer> userSeqList, Integer updateUserSeq, boolean requirePendingDelete);
    
}

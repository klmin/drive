package com.thinkfree.drive.cleanup.trash.converter;

import com.thinkfree.drive.cleanup.trash.query.TrashCleanUpByDateQuery;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

/** 사용자 조건(TrashCleanUpByUserQuery)은 시각·개수 제한이 붙어 서비스에서 직접 만든다. */
@Mapper(componentModel = "spring")
public interface TrashCleanUpConverter {

    @Mapping(target = "isDelete", constant = "true")
    TrashCleanUpByDateQuery toTrashCleanUpByDateQuery(Instant thresholdDate);

}

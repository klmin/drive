package com.thinkfree.drive.cleanup.trash.converter;

import com.thinkfree.drive.cleanup.trash.query.TrashCleanUpByDateQuery;
import com.thinkfree.drive.cleanup.trash.query.TrashCleanUpByUserQuery;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

@Mapper(componentModel = "spring")
public interface TrashCleanUpConverter {

    @Mapping(target = "isDelete", constant = "true")
    TrashCleanUpByDateQuery toTrashCleanUpByDateQuery(Instant thresholdDate);

    @Mapping(target = "isDelete", constant = "true")
    TrashCleanUpByUserQuery toTrashCleanUpByUserQuery(Integer userSeq);

}

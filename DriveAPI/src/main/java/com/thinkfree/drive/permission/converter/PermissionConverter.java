package com.thinkfree.drive.permission.converter;

import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.permission.query.ResourcePermissionQuery;
import com.thinkfree.drive.permission.query.ResourcesPermissionQuery;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PermissionConverter {

    ResourcePermissionQuery toResourcePermissionQuery(Integer userSeq, PartitionKey key, Boolean isDelete);
    ResourcesPermissionQuery toResourcesPermissionQuery(Integer userSeq, List<PartitionKey> keys, Boolean isDelete);

}

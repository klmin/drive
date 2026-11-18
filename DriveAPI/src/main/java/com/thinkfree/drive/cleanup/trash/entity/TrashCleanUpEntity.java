package com.thinkfree.drive.cleanup.trash.entity;

import com.thinkfree.drive.partition.UserResourceGroup;
import com.thinkfree.drive.postprocess.orchestrator.dto.PostProcessTrashDto;

import java.util.List;
import java.util.Map;

public record TrashCleanUpEntity(
        Map<Integer, Long> userTotalSizeMap,
        List<PostProcessTrashDto> postProcessTrashDtoList,
        List<UserResourceGroup> userResourceGroupList,
        List<String> deleteKeyList
) {
}

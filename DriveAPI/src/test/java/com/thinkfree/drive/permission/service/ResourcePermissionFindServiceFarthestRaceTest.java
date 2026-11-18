package com.thinkfree.drive.permission.service;

import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.permission.converter.PermissionConverter;
import com.thinkfree.drive.permission.data.ResourcePermissionData;
import com.thinkfree.drive.permission.data.ResourceShareChainData;
import com.thinkfree.drive.permission.data.ResourceShareChainItem;
import com.thinkfree.drive.permission.repository.ResourcePermissionQueryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResourcePermissionFindService 권한·사슬 조회 사이 삭제 경합")
class ResourcePermissionFindServiceFarthestRaceTest {

    private static final int OWNER = 7;

    @Mock
    private ResourcePermissionQueryRepository resourcePermissionQueryRepository;

    @Mock
    private PermissionConverter permissionConverter;

    @InjectMocks
    private ResourcePermissionFindService resourcePermissionFindService;

    @Test
    @DisplayName("권한 조회엔 있고 사슬 조회엔 없는 리소스(그 사이 삭제)는 예외 없이 맵에서 빠진다")
    void skipsResourceMissingFromChain() {
        var kept = new PartitionKey(OWNER, 1L);
        var deleted = new PartitionKey(OWNER, 2L);

        given(resourcePermissionQueryRepository.findResourcePermissions(any())).willReturn(List.of(
                ResourcePermissionData.builder().userSeq(OWNER).resourceSeq(1L).build(),
                ResourcePermissionData.builder().userSeq(OWNER).resourceSeq(2L).build()));
        // 두 번째 조회 시점엔 2번이 이미 삭제돼 사슬이 1번 것만 온다
        given(resourcePermissionQueryRepository.findResourcesShareChain(any())).willReturn(List.of(
                ResourceShareChainData.builder().userSeq(OWNER).resourceSeq(1L)
                        .chains(List.of(ResourceShareChainItem.builder().userSeq(OWNER).resourceSeq(1L).ancestorResourceSeq(1L).build()))
                        .build()));

        var map = resourcePermissionFindService.findResourcesPermissionWithFarthestMap(OWNER, List.of(kept, deleted), false);

        assertThat(map).containsOnlyKeys(kept);
        assertThat(map.get(deleted)).isNull();
    }
}

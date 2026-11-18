package com.thinkfree.drive.cleanup.trash.data;

import com.thinkfree.drive.partition.UserResourceGroup;
import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 휴지통 영구삭제 대상(사용자 1명) — 리소스 목록 → 버전 목록 3단. {@code DefaultTrashCleanUpQueryRepository} 가 행을 묶어 만든다(구 MyBatis resultMap collection).
 * {@code resourceTargets}·{@code resourceVersionTargets} 는 비어 있어도 null 이 아니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TrashCleanUpTargetsData {

    private Integer userSeq;

    private String userName;

    private Integer tenantSeq;

    private List<TrashCleanUpResourceItem> resourceTargets;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class TrashCleanUpResourceItem {
        private Integer userSeq;
        private Long resourceSeq;
        private ResourceType resourceType;
        private Long sizeByte;
        private String resourceSaveName;
        private String resourceName;
        private List<TrashCleanUpResourceVersionItem> resourceVersionTargets;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class TrashCleanUpResourceVersionItem {
        private Long resourceVersionSeq;
        private Long sizeByte;
        private String resourceSaveName;
    }

    public UserResourceGroup getUserResourceGroup() {
        List<Long> resourceSeqList = this.resourceTargets.stream()
                .map(TrashCleanUpResourceItem::getResourceSeq)
                .collect(Collectors.toList());

        return new UserResourceGroup(this.userSeq, resourceSeqList);
    }

}

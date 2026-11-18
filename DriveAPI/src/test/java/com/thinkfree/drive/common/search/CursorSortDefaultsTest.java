package com.thinkfree.drive.common.search;

import com.thinkfree.drive.application.resource.command.ResourceSearchCommand;
import com.thinkfree.drive.application.share.command.SharedListCommand;
import com.thinkfree.drive.application.starred.command.StarredListCommand;
import com.thinkfree.drive.application.trash.command.TrashListCommand;
import com.thinkfree.drive.common.search.enums.SortField;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.resource.data.ResourceData;
import com.thinkfree.drive.resource.enums.ResourceSortField;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.share.target.enums.ShareTargetSortField;
import com.thinkfree.drive.trash.enums.TrashSortField;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 목록 커서가 끝나지 않던 두 경우의 회귀 — SIZE 정렬의 폴더 커서 값, 정렬 방향 누락. */
class CursorSortDefaultsTest {

    @Test
    void sizeCursorValueOfFolderIsZero() {
        ResourceData folder = ResourceData.builder().resourceType(ResourceType.FOLDER).build();
        ResourceData file = ResourceData.builder().resourceType(ResourceType.FILE).sizeByte(42L).build();

        assertThat(ResourceSortField.SIZE.getExtractor().apply(folder)).isEqualTo(0L);
        assertThat(SortField.SIZE.getExtractor().apply(folder)).isEqualTo(0L);
        assertThat(ShareTargetSortField.SIZE.getExtractor().apply(folder)).isEqualTo(0L);
        assertThat(TrashSortField.SIZE.getExtractor().apply(folder)).isEqualTo(0L);
        assertThat(ResourceSortField.SIZE.getExtractor().apply(file)).isEqualTo(42L);
    }

    @Test
    void sortOrderDefaultsToAscOnlyWhenSortByGiven() {
        assertThat(ResourceSearchCommand.builder().sortBy(ResourceSortField.SIZE).build().sortOrder()).isEqualTo(SortOrder.ASC);
        assertThat(SharedListCommand.builder().sortBy(ShareTargetSortField.SHARE).build().sortOrder()).isEqualTo(SortOrder.ASC);
        assertThat(StarredListCommand.builder().sortBy(SortField.UPDATE).build().sortOrder()).isEqualTo(SortOrder.ASC);
        assertThat(TrashListCommand.builder().sortBy(TrashSortField.SIZE).build().sortOrder()).isEqualTo(SortOrder.ASC);

        assertThat(ResourceSearchCommand.builder().sortBy(ResourceSortField.NAME).sortOrder(SortOrder.DESC).build().sortOrder()).isEqualTo(SortOrder.DESC);
        assertThat(ResourceSearchCommand.builder().build().sortOrder()).isNull();
        assertThat(TrashListCommand.builder().sortOrder(SortOrder.DESC).build().sortBy()).isNull();
    }
}

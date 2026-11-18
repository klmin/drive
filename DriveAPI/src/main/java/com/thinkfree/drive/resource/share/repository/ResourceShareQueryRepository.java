package com.thinkfree.drive.resource.share.repository;

import com.thinkfree.drive.resource.query.ResourceListQuery;
import com.thinkfree.drive.resource.share.data.ResourceSharedData;

import java.util.List;

/**
 * 공유받은 폴더 안 목록 읽기(소유자 = {@code userSeq}, 뷰어 = {@code requestUserSeq}) — {@code DefaultResourceShareFindService} 가 주입. 쓰기 없음.
 * 동적 조건·커서 DTO 목록이라 JdbcClient.
 */
public interface ResourceShareQueryRepository {

    /** 소유자의 parentFolderSeq 바로 아래 목록(뷰어의 별표·최근열람 포함). 정렬·커서·필터는 {@code ResourceJooq}·{@code ResourceCursorJooq}. */
    List<ResourceSharedData> findAllByShared(ResourceListQuery query);
}

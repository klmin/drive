package com.thinkfree.drive.resource.global.repository;

import com.thinkfree.drive.resource.global.data.ResourceGlobalData;
import com.thinkfree.drive.resource.query.ResourceListQuery;

import java.util.List;

/**
 * 전체 목록(내 리소스 전부 + 공유받은 리소스와 그 하위) 읽기 — {@code DefaultResourceGlobalFindService} 가 주입. 쓰기 없음.
 * 재귀 CTE·UNION·동적 조건·커서 DTO 목록이라 JdbcClient.
 */
public interface ResourceGlobalQueryRepository {

    /** 뷰어({@code userSeq}) 기준 내 것 + 공유받은 것(하위 포함, 중첩 공유는 1회), 각 구간과 합친 결과에 같은 정렬·커서·LIMIT. */
    List<ResourceGlobalData> findAll(ResourceListQuery query);
}

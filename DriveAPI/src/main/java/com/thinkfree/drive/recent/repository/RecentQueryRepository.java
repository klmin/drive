package com.thinkfree.drive.recent.repository;

import com.thinkfree.drive.recent.data.RecentData;
import com.thinkfree.drive.recent.query.RecentListQuery;

import java.util.List;

/**
 * 읽기 전용(FindService 가 주입). 최근 문서 목록(열람일 DESC 커서·필터) — JdbcClient 구현. 쓰기(upsert·DELETE)는 {@link RecentRepository}.
 */
public interface RecentQueryRepository {

    /** 최근 문서 — 수신자(userSeq) 기준, update_date DESC·resource_seq DESC, 커서(update_date, resource_seq)·필터, 공유자 노출 가능 상태 */
    List<RecentData> findAll(RecentListQuery query);
}

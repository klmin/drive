package com.thinkfree.drive.starred.repository;

import com.thinkfree.drive.starred.data.StarredData;
import com.thinkfree.drive.starred.query.StarredListQuery;

import java.util.List;

/**
 * 읽기 전용(FindService 가 주입). 별표 목록(커서·정렬·필터) — JdbcClient 구현. 쓰기(INSERT 는 JPA 위임·DELETE 는 JdbcClient)는 {@link StarredRepository}.
 */
public interface StarredQueryRepository {

    /** 별표 목록 — 수신자(userSeq) 기준, 폴더 먼저·정렬(NAME 자연정렬 기본 ASC / SIZE / UPDATE)·커서 게이트·필터, 공유자 노출 가능 상태 */
    List<StarredData> findAll(StarredListQuery query);
}

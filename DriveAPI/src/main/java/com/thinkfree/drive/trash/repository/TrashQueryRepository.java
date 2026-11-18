package com.thinkfree.drive.trash.repository;

import com.thinkfree.drive.trash.data.TrashData;
import com.thinkfree.drive.trash.query.TrashListQuery;

import java.util.List;

/**
 * tb_trash 읽기(휴지통 목록) — {@code DefaultTrashFindService} 가 주입. 쓰기는 {@link TrashRepository}.
 */
public interface TrashQueryRepository {

    /** 사용자 휴지통 목록 — 리소스·소유자·삭제자·등록/수정자·부모 폴더 조인, 타입/삭제일 범위/확장자/삭제자 아이디 필터, 정렬(NAME 자연정렬·SIZE·REGISTER=삭제일) + resource_seq tie-break 커서, LIMIT */
    List<TrashData> findAll(TrashListQuery query);
}

package com.thinkfree.drive.pinnedfolder.repository;

import com.thinkfree.drive.pinnedfolder.data.PinnedFolderData;
import com.thinkfree.drive.pinnedfolder.query.PinnedFolderExistsQuery;
import com.thinkfree.drive.pinnedfolder.query.PinnedFolderListQuery;

import java.util.List;

/**
 * 읽기 전용({@code PinnedFolderFindService} 가 주입). 고정 폴더 목록·존재 여부 — JdbcClient 구현.
 * 쓰기(INSERT 는 JPA 위임, FOR UPDATE 건수·DELETE 는 JdbcClient)는 {@link PinnedFolderRepository}.
 */
public interface PinnedFolderQueryRepository {

    /** 사용자의 고정 폴더 — 리소스 조인(공유자 기준), 등록 순 */
    List<PinnedFolderData> findPinnedFolders(PinnedFolderListQuery query);
    boolean existsPinnedFolder(PinnedFolderExistsQuery query);
}

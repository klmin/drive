package com.thinkfree.drive.pinnedfolder.repository;

import com.thinkfree.drive.pinnedfolder.data.PinnedFolderData;
import com.thinkfree.drive.pinnedfolder.query.PinnedFolderExistsQuery;
import com.thinkfree.drive.pinnedfolder.query.PinnedFolderListQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * tb_pinned_folder 읽기 — 전부 JPQL(목록 = 리소스 엔티티 조인 생성자 식(5차 패스 C), 존재 확인). 쓰기는 {@link DefaultPinnedFolderRepository}.
 * 목록은 커서 없이 등록일·seq 순 고정, 리소스 조인은 공유자(shared_by_user_seq) 기준(구 XML 동일).
 */
@Repository
@RequiredArgsConstructor
public class DefaultPinnedFolderQueryRepository implements PinnedFolderQueryRepository {

    private final PinnedFolderJpaRepository jpaRepository;

    @Override
    public List<PinnedFolderData> findPinnedFolders(PinnedFolderListQuery q) {
        return jpaRepository.findPinnedFolders(q.getUserSeq());
    }

    @Override
    public boolean existsPinnedFolder(PinnedFolderExistsQuery q) {
        return jpaRepository.existsPinned(q.getUserSeq(), q.getResourceSeq(), q.getSharedByUserSeq());
    }
}

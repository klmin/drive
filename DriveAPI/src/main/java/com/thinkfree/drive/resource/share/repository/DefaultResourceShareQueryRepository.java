package com.thinkfree.drive.resource.share.repository;

import com.thinkfree.drive.resource.query.ResourceListQuery;
import com.thinkfree.drive.resource.repository.ResourceJooq;
import com.thinkfree.drive.resource.share.data.ResourceSharedData;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.thinkfree.drive.global.jooq.JooqConditions.eq;
import static com.thinkfree.drive.resource.repository.ResourceJooq.*;
import static org.jooq.impl.DSL.val;

/**
 * 공유받은 폴더 안 목록 — jOOQ(구 ResourceShareMapper.xml findAllByShared, 10-30 작업 B P1 에서 JdbcClient → jOOQ).
 * <p>
 * 내 드라이브 목록({@code DefaultResourceQueryRepository#findAll})과 같은 조인·커서·정렬({@link ResourceJooq})에 별표·최근열람만 뷰어(requestUserSeq) 기준.
 * WHERE = 소유자(user_seq)·is_delete·parent_folder_seq(항상 비교, null 이면 0건 — 구 XML 동형) + resource-base-where + 소유 계정 노출 가능.
 * OPEN 커서의 열람 그룹 판정은 뷰어의 tb_recent(shared_by = 소유자)로 — ORDER BY 의 g 조인과 같은 키. 구 CommonMapper 조각은 소유자의 tb_recent 를 봐서 파일 구간 안에서 페이지가 끊기면 다음 페이지가 비던 버그가 있었다(리포 테스트 OPEN ASC 왕복에서 발견, 10-06 수정).
 */
@Repository
@RequiredArgsConstructor
public class DefaultResourceShareQueryRepository implements ResourceShareQueryRepository {

    private final DSLContext dsl;

    @Override
    public List<ResourceSharedData> findAllByShared(ResourceListQuery q) {
        var page = q.getPage();
        return viewerJoin(dsl.select(A.RESOURCE_SEQ, A.USER_SEQ, A.OWNER_SEQ, OWNER_ID, OWNER_NAME, OWNER_STATUS, OWNER_TENANT_SEQ, OWNER_TENANT_NAME,
                                A.RESOURCE_TYPE, A.RESOURCE_NAME, A.EXT, A.SIZE_BYTE, A.IS_LOCKED, IS_STARRED, STARRED_SEQ,
                                REGISTER_USER_ID, REGISTER_USER_NAME, REGISTER_USER_STATUS, A.REGISTER_DATE,
                                UPDATE_USER_ID, UPDATE_USER_NAME, UPDATE_USER_STATUS, A.UPDATE_DATE, RECENT_UPDATE_DATE)
                        .from(A), q.getRequestUserSeq())
                .where(eq(A.USER_SEQ, q.getUserSeq()))
                .and(eq(A.IS_DELETE, q.getIsDelete()))
                .and(eq(A.PARENT_FOLDER_SEQ, q.getParentFolderSeq()))
                .and(baseWhere(q, recentCursorRank(q.getRequestUserSeq(), q.getUserSeq())))
                .and(visibleByActiveUser(A.USER_SEQ))
                .orderBy(orderBy(q.getSortBy(), q.getSortOrder(), G.UPDATE_DATE))
                .limit(val(page == null ? null : page.getPageSize(), SQLDataType.INTEGER))
                .fetch(r -> ResourceSharedData.builder()
                        .resourceSeq(r.get(A.RESOURCE_SEQ))
                        .userSeq(r.get(A.USER_SEQ))
                        .ownerSeq(r.get(A.OWNER_SEQ))
                        .ownerId(r.get(OWNER_ID))
                        .ownerName(r.get(OWNER_NAME))
                        .ownerStatus(r.get(OWNER_STATUS))
                        .ownerTenantSeq(r.get(OWNER_TENANT_SEQ))
                        .ownerTenantName(r.get(OWNER_TENANT_NAME))
                        .resourceType(r.get(A.RESOURCE_TYPE))
                        .resourceName(r.get(A.RESOURCE_NAME))
                        .ext(r.get(A.EXT))
                        .sizeByte(r.get(A.SIZE_BYTE))
                        .isLocked(Boolean.TRUE.equals(r.get(A.IS_LOCKED)))
                        .isStarred(r.get(IS_STARRED))
                        .starredSeq(r.get(STARRED_SEQ))
                        .registerUserId(r.get(REGISTER_USER_ID))
                        .registerUserName(r.get(REGISTER_USER_NAME))
                        .registerUserStatus(r.get(REGISTER_USER_STATUS))
                        .registerDate(r.get(A.REGISTER_DATE))
                        .updateUserId(r.get(UPDATE_USER_ID))
                        .updateUserName(r.get(UPDATE_USER_NAME))
                        .updateUserStatus(r.get(UPDATE_USER_STATUS))
                        .updateDate(r.get(A.UPDATE_DATE))
                        .recentUpdateDate(r.get(RECENT_UPDATE_DATE))
                        .build());
    }
}

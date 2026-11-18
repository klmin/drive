package com.thinkfree.drive.permission.repository;

import com.thinkfree.drive.jooq.tables.TbResource;
import com.thinkfree.drive.jooq.tables.TbShare;
import com.thinkfree.drive.permission.data.SharePermissionData;
import com.thinkfree.drive.permission.query.SharePermissionQuery;
import com.thinkfree.drive.resource.repository.ResourceJooq;
import lombok.RequiredArgsConstructor;
import org.jooq.CommonTableExpression;
import org.jooq.DSLContext;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.stereotype.Repository;

import static com.thinkfree.drive.global.jooq.JooqConditions.eq;
import static com.thinkfree.drive.jooq.Tables.TB_RESOURCE;
import static com.thinkfree.drive.jooq.Tables.TB_SHARE;
import static org.jooq.impl.DSL.*;

/**
 * 공유 기준 권한 읽기 — jOOQ(구 SharePermission.xml 1 statement, 10-30 작업 B P1-② 에서 JdbcClient → jOOQ).
 * <p>
 * 시작 CTE {@code share_resource_data} = 공유(user_seq + share_seq)의 리소스(is_delete 일치, 소유 계정 노출 가능) → {@link PermissionJooq} 조상 재귀(재귀 단계도 is_delete 일치)
 * → 요청자(requestUserSeq) 기준 가장 가까운 공개/조직 링크 공유(l)·대상 공유(t) → 24컬럼. 요청자가 소유자여도 링크 조건은 그대로(구 XML 동형).
 * 여러 행이면 구 JdbcClient {@code optional()} 처럼 IncorrectResultSizeDataAccessException.
 */
@Repository
@RequiredArgsConstructor
public class DefaultSharePermissionQueryRepository implements SharePermissionQueryRepository {

    private static final String SHARE_RESOURCE_DATA = "share_resource_data";

    private final DSLContext dsl;

    @Override
    public SharePermissionData findSharePermission(SharePermissionQuery q) {
        TbShare a = TB_SHARE.as("a");
        TbResource b = TB_RESOURCE.as("b");
        CommonTableExpression<?> seed = name(SHARE_RESOURCE_DATA).as(
                select(a.RESOURCE_SEQ, a.USER_SEQ, b.PARENT_FOLDER_SEQ)
                        .from(a)
                        .join(b).on(a.USER_SEQ.eq(b.USER_SEQ).and(a.RESOURCE_SEQ.eq(b.RESOURCE_SEQ)).and(eq(b.IS_DELETE, q.getIsDelete())))
                        .where(eq(a.USER_SEQ, q.getUserSeq()))
                        .and(eq(a.SHARE_SEQ, q.getShareSeq()))
                        .and(ResourceJooq.visibleByActiveUser(a.USER_SEQ)));
        return DataAccessUtils.singleResult(PermissionJooq.permissionFrom(dsl.withRecursive(seed,
                                PermissionJooq.resourceRecursive(SHARE_RESOURCE_DATA, eq(TB_RESOURCE.as("a").IS_DELETE, q.getIsDelete())),
                                PermissionJooq.nearestLink(q.getRequestUserSeq(), false), PermissionJooq.nearestTarget(q.getRequestUserSeq()))
                        .select(PermissionJooq.columns())
                        .from(table(name(SHARE_RESOURCE_DATA)).as("r")))
                .fetch(rec -> {
                    PermissionJooq.Row r = PermissionJooq.read(rec);
                    return SharePermissionData.builder()
                            .userSeq(r.userSeq()).resourceSeq(r.resourceSeq())
                            .shareSeq(r.shareSeq()).shareResourceSeq(r.shareResourceSeq()).shareType(r.shareType())
                            .shareCanView(r.shareCanView()).shareCanEdit(r.shareCanEdit()).shareCanDownload(r.shareCanDownload())
                            .shareCanUpload(r.shareCanUpload()).shareCanDelete(r.shareCanDelete()).shareCanShare(r.shareCanShare()).shareDepth(r.shareDepth())
                            .shareTargetShareSeq(r.shareTargetShareSeq()).shareTargetSeq(r.shareTargetSeq()).shareTargetShareResourceSeq(r.shareTargetShareResourceSeq())
                            .shareTargetShareType(r.shareTargetShareType())
                            .shareTargetCanView(r.shareTargetCanView()).shareTargetCanEdit(r.shareTargetCanEdit()).shareTargetCanDownload(r.shareTargetCanDownload())
                            .shareTargetCanUpload(r.shareTargetCanUpload()).shareTargetCanDelete(r.shareTargetCanDelete()).shareTargetCanShare(r.shareTargetCanShare())
                            .shareTargetDepth(r.shareTargetDepth())
                            .build();
                }));
    }
}

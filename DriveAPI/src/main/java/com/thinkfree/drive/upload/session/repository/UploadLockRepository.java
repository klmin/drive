package com.thinkfree.drive.upload.session.repository;

import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.jooq.DSLContext;

import java.util.Optional;

import static com.thinkfree.drive.jooq.Tables.TB_RESOURCE;
import static com.thinkfree.drive.jooq.Tables.TB_USER;

/**
 * 업로드 세션·커밋 tx 의 행 잠금(docs/design/upload-session-redesign.md 규칙 8·9, §8).
 * 모두 {@code FOR NO KEY UPDATE} — 같은 행을 고치는 쪽끼리만 직렬화하고, FK 참조 INSERT(KEY SHARE)는 막지 않는다
 * (tb_user 는 tb_user_log·tb_system_config 가 참조). 엔티티를 영속성 컨텍스트에 올리지 않는다(스칼라 조회).
 */
@Repository
@RequiredArgsConstructor
public class UploadLockRepository {

    private final DSLContext dsl;

    /**
     * 사용자 행 잠금 + 용량 스냅샷.
     *
     * @param useQuotaByte   확정 사용량
     * @param totalQuotaByte 한도
     */
    public record UserQuota(long useQuotaByte, long totalQuotaByte) {
        /** 확정 사용량 + additional 이 한도를 넘는지 */
        public boolean wouldExceed(long additionalBytes) {
            return totalQuotaByte - useQuotaByte - additionalBytes < 0;
        }
    }

    /**
     * 잠근 폴더 행.
     *
     * @param resourceName 폴더 이름(결과의 parentFolderName)
     */
    public record LockedFolder(long resourceSeq, String resourceName) {
    }

    public Optional<UserQuota> lockUser(int userSeq) {
        return dsl.select(TB_USER.USE_QUOTA_BYTE, TB_USER.TOTAL_QUOTA_BYTE)
                  .from(TB_USER)
                  .where(TB_USER.USER_SEQ.eq(userSeq))
                  .forNoKeyUpdate()
                  .fetchOptional(r -> new UserQuota(nz(r.value1()), nz(r.value2())));
    }

    /**
     * 살아 있는(휴지통 아님) 폴더 행 잠금 — 이름 넘버링 직렬화 + 휴지통 이동과의 경합 차단(§3 #8·#9).
     * 휴지통으로 옮기는 쪽도 이 행을 고치므로, 잠근 뒤 본 is_delete = N 은 커밋까지 유지된다.
     */
    public Optional<LockedFolder> lockLiveFolder(int userSeq, long folderSeq) {
        return dsl.select(TB_RESOURCE.RESOURCE_SEQ, TB_RESOURCE.RESOURCE_NAME)
                  .from(TB_RESOURCE)
                  .where(TB_RESOURCE.USER_SEQ.eq(userSeq))
                  .and(TB_RESOURCE.RESOURCE_SEQ.eq(folderSeq))
                  .and(TB_RESOURCE.RESOURCE_TYPE.eq(ResourceType.FOLDER))
                  .and(TB_RESOURCE.IS_DELETE.eq(false))
                  .forNoKeyUpdate()
                  .fetchOptional(r -> new LockedFolder(r.value1(), r.value2()));
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }
}

package com.thinkfree.drive.upload.session.repository;

import com.thinkfree.drive.jooq.tables.records.TbUploadSessionRecord;
import com.thinkfree.drive.upload.session.data.UploadSessionActiveUsage;
import com.thinkfree.drive.upload.session.data.UploadSessionCleanupTarget;
import com.thinkfree.drive.upload.session.data.UploadSessionData;
import com.thinkfree.drive.upload.session.enums.UploadSessionFailReason;
import com.thinkfree.drive.upload.session.enums.UploadSessionStatus;
import com.thinkfree.drive.upload.session.query.UploadSessionCreateQuery;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record3;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.thinkfree.drive.jooq.Tables.TB_UPLOAD_SESSION;

/** tb_upload_session(jOOQ). 시각은 DB now() 하나로 통일한다. */
@Repository
@RequiredArgsConstructor
public class DefaultUploadSessionRepository implements UploadSessionRepository {

    /** last_error 컬럼 길이 */
    static final int LAST_ERROR_MAX_LENGTH = 1000;

    private static final Field<Instant> DB_NOW = DSL.field("now()", SQLDataType.INSTANT);

    private final DSLContext dsl;

    @Override
    public UUID insert(UploadSessionCreateQuery q) {
        return dsl.insertInto(TB_UPLOAD_SESSION)
                  .set(TB_UPLOAD_SESSION.TENANT_SEQ, q.tenantSeq())
                  .set(TB_UPLOAD_SESSION.USER_SEQ, q.userSeq())
                  .set(TB_UPLOAD_SESSION.REQUEST_USER_SEQ, q.requestUserSeq())
                  .set(TB_UPLOAD_SESSION.SAVE_TYPE, q.saveType())
                  .set(TB_UPLOAD_SESSION.PARENT_FOLDER_SEQ, q.parentFolderSeq())
                  .set(TB_UPLOAD_SESSION.RESOURCE_SEQ, q.resourceSeq())
                  .set(TB_UPLOAD_SESSION.SOURCE_VERSION_SEQ, q.sourceVersionSeq())
                  .set(TB_UPLOAD_SESSION.BASE_STORAGE_KEY, q.baseStorageKey())
                  .set(TB_UPLOAD_SESSION.FILE_NAME, q.fileName())
                  .set(TB_UPLOAD_SESSION.CONTENT_TYPE, q.contentType())
                  .set(TB_UPLOAD_SESSION.SIZE_BYTE, q.sizeByte())
                  .set(TB_UPLOAD_SESSION.PART_SIZE, q.partSize())
                  .set(TB_UPLOAD_SESSION.PART_COUNT, q.partCount())
                  .set(TB_UPLOAD_SESSION.STATUS, UploadSessionStatus.PENDING)
                  .returningResult(TB_UPLOAD_SESSION.UPLOAD_SESSION_ID)
                  .fetchOne(TB_UPLOAD_SESSION.UPLOAD_SESSION_ID);
    }

    @Override
    public int attachStorage(UUID uploadSessionId, String storageKey, String uploadId) {
        return dsl.update(TB_UPLOAD_SESSION)
                  .set(TB_UPLOAD_SESSION.STORAGE_KEY, storageKey)
                  .set(TB_UPLOAD_SESSION.UPLOAD_ID, uploadId)
                  .set(TB_UPLOAD_SESSION.LAST_ACTIVITY, DB_NOW)
                  .where(TB_UPLOAD_SESSION.UPLOAD_SESSION_ID.eq(uploadSessionId))
                  .and(TB_UPLOAD_SESSION.STATUS.eq(UploadSessionStatus.PENDING))
                  .execute();
    }

    @Override
    public Optional<UploadSessionData> findByIdAndRequester(UUID uploadSessionId, int requestUserSeq) {
        return dsl.selectFrom(TB_UPLOAD_SESSION)
                  .where(TB_UPLOAD_SESSION.UPLOAD_SESSION_ID.eq(uploadSessionId))
                  .and(TB_UPLOAD_SESSION.REQUEST_USER_SEQ.eq(requestUserSeq))
                  .fetchOptional(DefaultUploadSessionRepository::toData);
    }

    @Override
    public List<UploadSessionData> findByRequesterAndStatus(int requestUserSeq, UploadSessionStatus status, int limit) {
        return dsl.selectFrom(TB_UPLOAD_SESSION)
                  .where(TB_UPLOAD_SESSION.REQUEST_USER_SEQ.eq(requestUserSeq))
                  .and(TB_UPLOAD_SESSION.STATUS.eq(status))
                  .orderBy(TB_UPLOAD_SESSION.CREATE_DATE.desc())
                  .limit(limit)
                  .fetch(DefaultUploadSessionRepository::toData);
    }

    @Override
    public UploadSessionActiveUsage sumActive(int userSeq) {
        // 시간 조건 없음 — 만료되면 스케줄러가 FAILED 로 바꾸므로 PENDING·COMPLETING = 진행 중
        var row = dsl.select(DSL.coalesce(DSL.sum(TB_UPLOAD_SESSION.SIZE_BYTE), DSL.inline(java.math.BigDecimal.ZERO)), DSL.count())
                     .from(TB_UPLOAD_SESSION)
                     .where(TB_UPLOAD_SESSION.USER_SEQ.eq(userSeq))
                     .and(TB_UPLOAD_SESSION.STATUS.in(UploadSessionStatus.PENDING, UploadSessionStatus.COMPLETING))
                     .fetchOne();
        return row == null ? new UploadSessionActiveUsage(0, 0) : new UploadSessionActiveUsage(row.value1().longValue(), row.value2());
    }

    @Override
    public int touch(UUID uploadSessionId) {
        return dsl.update(TB_UPLOAD_SESSION)
                  .set(TB_UPLOAD_SESSION.LAST_ACTIVITY, DB_NOW)
                  .where(TB_UPLOAD_SESSION.UPLOAD_SESSION_ID.eq(uploadSessionId))
                  .and(TB_UPLOAD_SESSION.STATUS.eq(UploadSessionStatus.PENDING))
                  .execute();
    }

    @Override
    public int markCompleting(UUID uploadSessionId, int requestUserSeq) {
        return dsl.update(TB_UPLOAD_SESSION)
                  .set(TB_UPLOAD_SESSION.STATUS, UploadSessionStatus.COMPLETING)
                  .set(TB_UPLOAD_SESSION.COMPLETING_AT, DB_NOW)
                  .set(TB_UPLOAD_SESSION.LAST_ACTIVITY, DB_NOW)
                  .where(TB_UPLOAD_SESSION.UPLOAD_SESSION_ID.eq(uploadSessionId))
                  .and(TB_UPLOAD_SESSION.REQUEST_USER_SEQ.eq(requestUserSeq))
                  .and(TB_UPLOAD_SESSION.STATUS.eq(UploadSessionStatus.PENDING))
                  .execute();
    }

    @Override
    public int markCompleted(UUID uploadSessionId, long resultResourceSeq, String resultName) {
        return dsl.update(TB_UPLOAD_SESSION)
                  .set(TB_UPLOAD_SESSION.STATUS, UploadSessionStatus.COMPLETED)
                  .set(TB_UPLOAD_SESSION.RESULT_RESOURCE_SEQ, resultResourceSeq)
                  .set(TB_UPLOAD_SESSION.RESULT_NAME, resultName)
                  .set(TB_UPLOAD_SESSION.LAST_ACTIVITY, DB_NOW)
                  .where(TB_UPLOAD_SESSION.UPLOAD_SESSION_ID.eq(uploadSessionId))
                  .and(TB_UPLOAD_SESSION.STATUS.eq(UploadSessionStatus.COMPLETING))
                  .execute();
    }

    @Override
    public Optional<UploadSessionCleanupTarget> markFailed(UUID uploadSessionId, UploadSessionStatus expected, UploadSessionFailReason reason, String lastError) {
        return dsl.update(TB_UPLOAD_SESSION)
                  .set(TB_UPLOAD_SESSION.STATUS, UploadSessionStatus.FAILED)
                  .set(TB_UPLOAD_SESSION.FAIL_REASON, reason)
                  .set(TB_UPLOAD_SESSION.LAST_ERROR, truncate(lastError))
                  .set(TB_UPLOAD_SESSION.LAST_ACTIVITY, DB_NOW)
                  .where(TB_UPLOAD_SESSION.UPLOAD_SESSION_ID.eq(uploadSessionId))
                  .and(TB_UPLOAD_SESSION.STATUS.eq(expected))
                  .returningResult(TB_UPLOAD_SESSION.UPLOAD_SESSION_ID, TB_UPLOAD_SESSION.STORAGE_KEY, TB_UPLOAD_SESSION.UPLOAD_ID)
                  .fetchOptional(DefaultUploadSessionRepository::toTarget);
    }

    @Override
    public List<UploadSessionCleanupTarget> expirePending(Duration ttl, int limit) {
        return failOlderThan(UploadSessionStatus.PENDING, TB_UPLOAD_SESSION.LAST_ACTIVITY, ttl, limit,
                             UploadSessionFailReason.EXPIRED, "expired: no activity for " + ttl);
    }

    @Override
    public List<UploadSessionCleanupTarget> failStaleCompleting(Duration timeout, int limit) {
        return failOlderThan(UploadSessionStatus.COMPLETING, TB_UPLOAD_SESSION.COMPLETING_AT, timeout, limit,
                             UploadSessionFailReason.COMMIT_ERROR, "completing timed out after " + timeout);
    }

    private List<UploadSessionCleanupTarget> failOlderThan(UploadSessionStatus expected, Field<Instant> timeField, Duration age, int limit,
                                                           UploadSessionFailReason reason, String lastError) {
        // 대상 고르기와 잠금을 한 번에 — 동시에 진행 중인 complete·파트 PUT 이 잡은 행은 건너뛰고 다음 실행에서 다시 본다
        var targetIds = dsl.select(TB_UPLOAD_SESSION.UPLOAD_SESSION_ID)
                           .from(TB_UPLOAD_SESSION)
                           .where(TB_UPLOAD_SESSION.STATUS.eq(expected))
                           .and(timeField.lt(olderThan(age)))
                           .orderBy(timeField)
                           .limit(limit)
                           .forUpdate()
                           .skipLocked();
        return dsl.update(TB_UPLOAD_SESSION)
                  .set(TB_UPLOAD_SESSION.STATUS, UploadSessionStatus.FAILED)
                  .set(TB_UPLOAD_SESSION.FAIL_REASON, reason)
                  .set(TB_UPLOAD_SESSION.LAST_ERROR, lastError)
                  .set(TB_UPLOAD_SESSION.LAST_ACTIVITY, DB_NOW)
                  .where(TB_UPLOAD_SESSION.UPLOAD_SESSION_ID.in(targetIds))
                  .and(TB_UPLOAD_SESSION.STATUS.eq(expected))
                  .returningResult(TB_UPLOAD_SESSION.UPLOAD_SESSION_ID, TB_UPLOAD_SESSION.STORAGE_KEY, TB_UPLOAD_SESSION.UPLOAD_ID)
                  .fetch(DefaultUploadSessionRepository::toTarget);
    }

    @Override
    public int purgeFinished(Duration retention, int limit) {
        Condition finished = TB_UPLOAD_SESSION.STATUS.in(UploadSessionStatus.COMPLETED, UploadSessionStatus.FAILED);
        var targetIds = dsl.select(TB_UPLOAD_SESSION.UPLOAD_SESSION_ID)
                           .from(TB_UPLOAD_SESSION)
                           .where(finished)
                           .and(TB_UPLOAD_SESSION.CREATE_DATE.lt(olderThan(retention)))
                           .limit(limit)
                           .forUpdate()
                           .skipLocked();
        return dsl.deleteFrom(TB_UPLOAD_SESSION)
                  .where(TB_UPLOAD_SESSION.UPLOAD_SESSION_ID.in(targetIds))
                  .and(finished)
                  .execute();
    }

    /** now() - age (DB 시각 기준) */
    private static Field<Instant> olderThan(Duration age) {
        return DSL.field("now() - make_interval(secs => {0})", SQLDataType.INSTANT, DSL.val((double) age.toSeconds()));
    }

    private static UploadSessionCleanupTarget toTarget(Record3<UUID, String, String> r) {
        return new UploadSessionCleanupTarget(r.value1(), r.value2(), r.value3());
    }

    private static UploadSessionData toData(TbUploadSessionRecord r) {
        return new UploadSessionData(
                r.getUploadSessionId(),
                r.getTenantSeq(),
                r.getUserSeq(),
                r.getRequestUserSeq(),
                r.getSaveType(),
                r.getParentFolderSeq(),
                r.getResourceSeq(),
                r.getSourceVersionSeq(),
                r.getBaseStorageKey(),
                r.getFileName(),
                r.getContentType(),
                r.getSizeByte(),
                r.getStorageKey(),
                r.getUploadId(),
                r.getPartSize(),
                r.getPartCount(),
                r.getStatus(),
                r.getFailReason(),
                r.getResultResourceSeq(),
                r.getResultName(),
                r.getLastActivity(),
                r.getCompletingAt(),
                r.getCreateDate());
    }

    private static String truncate(String error) {
        if (error == null || error.length() <= LAST_ERROR_MAX_LENGTH) {
            return error;
        }
        return error.substring(0, LAST_ERROR_MAX_LENGTH);
    }
}

package com.thinkfree.drive.cleanup.storage.constants;

import com.thinkfree.drive.storage.constants.StorageConstants;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Duration;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StorageDeletionConstants {

    /** 한 번에 꺼내 지우는 행 수 — S3 DeleteObjects 한 번 호출의 상한과 같게 */
    public static final int BATCH_SIZE = StorageConstants.MAX_DELETE_BATCH_SIZE;

    /** 한 번에 꺼내 abort 하는 행 수 — abort 는 일괄 API 가 없어 행마다 호출하므로 작게 */
    public static final int ABORT_BATCH_SIZE = 100;

    /** 이 횟수만큼 실패한 행은 더 시도하지 않고 남겨 둔다(수동 확인 후 attempt_count = 0 으로 되돌리면 다시 시도) */
    public static final int MAX_ATTEMPT_COUNT = 10;

    /** 실패 후 다음 시도까지 대기 = 2^(이전 실패 횟수) 분, 이 값이 상한 */
    public static final long MAX_BACKOFF_MINUTES = 60;

    /** last_error 컬럼 길이 */
    public static final int LAST_ERROR_MAX_LENGTH = 1000;

    /** 한 번 실행에서 쓸 최대 시간 — 스케줄러 잠금(lockAtMostFor)보다 짧게. DELETE·ABORT 가 나눠 쓴다 */
    public static final Duration RUN_TIME_BUDGET = Duration.ofMinutes(4);

    /** 삭제 직전 방어 — 아직 tb_resource·tb_resource_version 이 참조하는 key 는 지우지 않고 이 사유로 실패 처리(시도 상한에 닿으면 수동 확인) */
    public static final String REFERENCED_ERROR = "REFERENCED: key is still referenced by tb_resource or tb_resource_version";
}

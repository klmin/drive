package com.thinkfree.drive.storage.result;

import java.util.Map;

/**
 * 여러 키 삭제 결과 — 실패한 키와 사유만 담는다(요청한 키 중 여기 없는 키는 삭제 성공, 원래 없던 키도 성공).
 *
 * @param failedKeys 실패한 키 → 사유(S3 오류 코드·메시지, 또는 묶음 호출 자체의 실패 사유)
 */
public record StorageDeleteObjectsResult
(
    Map<String, String> failedKeys
)
{
    public StorageDeleteObjectsResult {
        failedKeys = Map.copyOf(failedKeys);
    }

    public boolean isFailed(String key) {
        return failedKeys.containsKey(key);
    }
}

package com.thinkfree.drive.resource.version.result;

/**
 * 현재 내용을 버전 행으로 넘긴 결과(업로드 세션 커밋 — docs/design/upload-session-redesign.md §4.2 VERSION).
 *
 * @param resourceVersionSeq 새 버전 행 seq
 * @param versionResourceSaveName 새 버전 행의 key(= 넘겨받은 이전 현재 key)
 * @param isLimitExceeded    버전 한도에 걸려 가장 오래된 버전을 밀어냈는지(리소스 현재 버전 번호를 올리지 않는다)
 * @param prunedSizeByte     밀려난 버전 크기(없으면 0) — 같은 tx 에서 사용량 차감
 * @param prunedResourceSaveName 밀려난 버전 key(없으면 null) — 같은 tx 에서 삭제 등록(유예)
 */
public record ResourceVersionPushResult
(
    Long resourceVersionSeq,
    String versionResourceSaveName,
    boolean isLimitExceeded,
    long prunedSizeByte,
    String prunedResourceSaveName
)
{
}

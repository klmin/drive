package com.thinkfree.drive.resource.result;

/**
 * 버전 저장 커밋 결과 — 현재 내용을 버전으로 넘기고 새 key 로 바꾼 뒤, 호출자(같은 tx)가 사용량·삭제 등록에 쓸 값.
 *
 * @param saveResult             저장 결과(resourceSeq·이름·새 크기 등)
 * @param prunedSizeByte         버전 한도로 밀려난 버전 크기(없으면 0) — 사용량에서 뺀다
 * @param prunedResourceSaveName 밀려난 버전 key(없으면 null) — 삭제 등록(유예)
 */
public record ResourceVersionReplaceResult
(
    ResourceSaveResult saveResult,
    long prunedSizeByte,
    String prunedResourceSaveName
)
{
}

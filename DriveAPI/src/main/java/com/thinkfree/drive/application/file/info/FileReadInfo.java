package com.thinkfree.drive.application.file.info;

/**
 * 파일 읽기 결과 — 본문(text)과 추출 불가 사유(textUnavailableReason)는 상호배타.
 * ownerUserSeq는 스코프 소유자(ss??요청자) — 어댑터의 웹 미리보기 링크(ss 파라미터) 조립용.
 */
public record FileReadInfo
(
    String resourceName,
    String extension,
    Long sizeByte,
    Integer ownerUserSeq,
    String text,
    Integer offset,
    Integer returnedChars,
    Integer totalChars,
    Boolean truncated,
    String textUnavailableReason
)
{
}

package com.thinkfree.drive.application.resource.info;

/**
 * 이름변경 결과 — 확정된 새 이름.
 * name은 파일이면 확장자를 뺀 이름(웹 파일 응답 FileRenameResponse.name 동형), fullName은 실제 저장된 이름 전체
 * (파일은 확장자 포함, MCP 툴 응답 재료). 폴더는 확장자 분리가 없어 둘이 같다.
 * 웹 폴더 응답은 본문이 없어 버려진다(통합 진입점은 폴더도 확정 이름을 돌려준다).
 */
public record ResourceRenameInfo
(
    String name,
    String fullName
)
{
}

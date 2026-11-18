package com.thinkfree.drive.application.version.command;

import lombok.Builder;

/**
 * 인자가 많아 위치 기반 생성이 위험하므로 직접 생성하는 어댑터(MCP 툴)는 builder로 조립한다.
 * (웹 어댑터는 MapStruct RequestConverter가 이름 기반으로 생성)
 */
@Builder
public record VersionListCommand
(
    Long resourceSeq,
    Integer sharedByUserSeq,
    Integer pageIndex,
    Integer pageSize
)
{
}

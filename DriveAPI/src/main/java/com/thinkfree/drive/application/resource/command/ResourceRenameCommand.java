package com.thinkfree.drive.application.resource.command;

import com.thinkfree.drive.resource.enums.NameConflictPolicy;

/**
 * 파일/폴더 공용 이름변경 command. 웹 파일 요청(ss/rn/ncp)의 superset —
 * 폴더 요청(ss/rn)에는 ncp 필드가 없어 항상 null이고, 폴더 경로는 ncp를 읽지 않는다(툴 통합 경로도 동일).
 */
public record ResourceRenameCommand
(
    Integer sharedByUserSeq,
    String newResourceName,
    NameConflictPolicy nameConflictPolicy
)
{
    /** 새 이름만 바꾼 command */
    public ResourceRenameCommand withNewResourceName(String newResourceName) {
        return new ResourceRenameCommand(sharedByUserSeq, newResourceName, nameConflictPolicy);
    }
}

package com.thinkfree.drive.resource.result;

import com.thinkfree.drive.resource.data.ResourceIdentity;

/**
 * 폴더 생성 결과 — 저장된 폴더의 식별 값(W10c). 감사 FOLDER_CREATE 인자로 그대로 넘기도록 {@link ResourceIdentity} 를 구현한다
 * (record 접근자는 {@code resourceSeq()} 라 인터페이스 getter 를 위임으로 둔다).
 */
public record ResourceFolderCreateResult
(
    Long resourceSeq,
    String resourceName,
    Long parentFolderSeq,
    String fullPath
)
implements ResourceIdentity
{
    @Override
    public Long getResourceSeq() {
        return resourceSeq;
    }

    @Override
    public String getResourceName() {
        return resourceName;
    }

    @Override
    public Long getParentFolderSeq() {
        return parentFolderSeq;
    }

    @Override
    public String getFullPath() {
        return fullPath;
    }
}

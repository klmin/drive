package com.thinkfree.drive.resource.result;

import com.thinkfree.drive.util.ResourceUtil;

public record ResourceRenameResult
(
    String name,
    String ext,
    String fullName
)
{
    /** 확장자는 이름이 아니라 ext 기준 */
    public static ResourceRenameResult of(String resourceName, String ext) {
        return new ResourceRenameResult(ResourceUtil.nameWithoutExtension(resourceName, ext), ext, resourceName);
    }
}

package com.thinkfree.drive.resource.version.result;

import java.util.List;

public record ResourceVersionDeleteAllResult(
        List<String> deleteKeys,
        long totalSizeByte
) {
}

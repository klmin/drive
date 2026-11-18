package com.thinkfree.drive.download.token;

public record VersionDownloadPayload(
        Long resourceSeq,
        Long resourceVersionSeq,
        Integer sharedByUserSeq,
        Integer requestUserSeq,
        long expiresAt
) {}


package com.thinkfree.drive.download.token;

public record DownloadPayload(
        Long resourceSeq,
        Integer userSeq,
        Integer sharedByUserSeq,
        Integer requestUserSeq,
        long expiresAt
) {}

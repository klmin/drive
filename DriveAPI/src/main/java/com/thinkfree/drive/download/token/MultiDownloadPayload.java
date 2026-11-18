package com.thinkfree.drive.download.token;

import com.thinkfree.drive.partition.PartitionKeyProvider;

import java.util.LinkedHashSet;

/** 멀티 다운로드용 토큰 내부 Payload 구조 */
public record MultiDownloadPayload(
        LinkedHashSet<DownloadItem> rl,
        Integer requestUserSeq,
        long expiresAt
) {

    public record DownloadItem(
            Integer ss,
            Long rs
    ) implements PartitionKeyProvider {

        @Override
        public Integer getSharedByUserSeq() {
            return ss;
        }

        @Override
        public Long getResourceSeq() {
            return rs;
        }
    }
}


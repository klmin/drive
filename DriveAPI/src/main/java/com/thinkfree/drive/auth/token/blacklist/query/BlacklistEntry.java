package com.thinkfree.drive.auth.token.blacklist.query;

import java.time.Instant;

/** 블랙리스트 항목 — Redis 재적재용(토큰 해시, 만료 시각) */
public record BlacklistEntry(String tokenHash, Instant expirationDate) {
}

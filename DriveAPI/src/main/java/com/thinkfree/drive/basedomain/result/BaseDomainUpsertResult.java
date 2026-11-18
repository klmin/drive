package com.thinkfree.drive.basedomain.result;

/** 베이스 도메인 upsert 결과 — 고정 seq 와 저장한(검증된) base_domain */
public record BaseDomainUpsertResult
(
    Integer baseDomainSeq,
    String baseDomain
)
{
}

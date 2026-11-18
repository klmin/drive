package com.thinkfree.drive.global.jdbc;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 요청 하나의 SQL 집계(local 전용 SQL 로그 — {@link SqlLogListener} 가 쌓고 {@link SqlRequestSummaryFilter} 가 요청 끝에 한 줄로 찍는다).
 * 요청 스레드의 ThreadLocal 이라 스케줄러·비동기 스레드 쿼리는 집계하지 않는다(쿼리 줄은 그대로 찍힘).
 */
public final class SqlRequestStats {

    /** SQL 첫 키워드 기준 종류. */
    public enum Verb {
        SELECT, INSERT, UPDATE, DELETE, OTHER
    }

    /** 같은 SQL(? 그대로) 반복 — 첫 호출 위치. */
    public record Repeat(String sql, String caller, int count) {
    }

    private static final ThreadLocal<SqlRequestStats> CURRENT = new ThreadLocal<>();

    private final Map<Verb, Integer> verbs = new EnumMap<>(Verb.class);
    private final Map<String, Repeat> repeats = new HashMap<>();
    private int queries;
    private long sqlNanos;
    private int commits;
    private int rollbacks;
    private Integer tenantSeq;
    private Integer userSeq;

    private SqlRequestStats() {
    }

    static void begin() {
        CURRENT.set(new SqlRequestStats());
    }

    static SqlRequestStats end() {
        SqlRequestStats stats = CURRENT.get();
        CURRENT.remove();
        return stats;
    }

    static Optional<SqlRequestStats> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    void query(Verb verb, String preparedSql, String caller, long nanos) {
        queries++;
        sqlNanos += nanos;
        verbs.merge(verb, 1, Integer::sum);
        repeats.merge(preparedSql, new Repeat(preparedSql, caller, 1), (a, b) -> new Repeat(a.sql(), a.caller(), a.count() + 1));
    }

    void commit() {
        commits++;
    }

    void rollback() {
        rollbacks++;
    }

    /** 테넌트·사용자는 요청 중 처음 보인 값(요청 끝엔 필터들이 컨텍스트를 이미 비움). */
    void identify(Integer tenantSeq, Integer userSeq) {
        if (this.tenantSeq == null) {
            this.tenantSeq = tenantSeq;
        }
        if (this.userSeq == null) {
            this.userSeq = userSeq;
        }
    }

    int queries() {
        return queries;
    }

    long sqlNanos() {
        return sqlNanos;
    }

    Map<Verb, Integer> verbs() {
        return verbs;
    }

    int commits() {
        return commits;
    }

    int rollbacks() {
        return rollbacks;
    }

    Integer tenantSeq() {
        return tenantSeq;
    }

    Integer userSeq() {
        return userSeq;
    }

    Optional<Repeat> mostRepeated() {
        return repeats.values().stream().max((a, b) -> Integer.compare(a.count(), b.count()));
    }
}

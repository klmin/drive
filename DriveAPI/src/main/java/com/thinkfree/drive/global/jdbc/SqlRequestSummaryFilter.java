package com.thinkfree.drive.global.jdbc;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

import static com.thinkfree.drive.global.jdbc.SqlLogListener.DIM;
import static com.thinkfree.drive.global.jdbc.SqlLogListener.GREEN_BOLD;
import static com.thinkfree.drive.global.jdbc.SqlLogListener.MAGENTA_BOLD;
import static com.thinkfree.drive.global.jdbc.SqlLogListener.RESET;
import static com.thinkfree.drive.global.jdbc.SqlLogListener.YELLOW;

/**
 * 요청 끝 SQL 요약 한 줄(local 전용, {@link SqlLogListener} 와 같은 조건) — 메서드·URI·테넌트·사용자·쿼리 수(종류별)·SQL 합계 시간·요청 시간·커밋/롤백 수.
 * 쿼리 {@value #MANY_QUERIES}개 초과, SQL 합계 {@value #SLOW_SQL_MS}ms 초과, 같은 SQL {@value #REPEAT_WARN}회 이상 반복(N+1 의심) 중 하나면 WARN.
 * 맨 바깥 필터라 TenantFilter·시큐리티 체인의 쿼리까지 센다. 쿼리 0개 요청(정적 자원 등)은 찍지 않는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "decorator.datasource.enabled", havingValue = "true")
public class SqlRequestSummaryFilter extends OncePerRequestFilter {

    private static final Logger log = LogManager.getLogger("sql");

    private static final int MANY_QUERIES = 100;
    private static final long SLOW_SQL_MS = 300;
    private static final int REPEAT_WARN = 10;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        SqlRequestStats.begin();
        try {
            chain.doFilter(request, response);
        } finally {
            SqlRequestStats stats = SqlRequestStats.end();
            if (stats != null && stats.queries() > 0) {
                // finally 안 예외는 정상 응답을 실패로 바꾸거나 원래 예외를 가린다 — 요약 실패는 삼킨다
                try {
                    summarize(request, stats, System.nanoTime() - start);
                } catch (RuntimeException e) {
                    log.warn("SQL 요약 로그 실패 : {}", e.toString());
                }
            }
        }
    }

    private static void summarize(HttpServletRequest request, SqlRequestStats stats, long requestNanos) {
        boolean many = stats.queries() > MANY_QUERIES;
        boolean slow = stats.sqlNanos() > TimeUnit.MILLISECONDS.toNanos(SLOW_SQL_MS);
        SqlRequestStats.Repeat repeat = stats.mostRepeated().filter(r -> r.count() >= REPEAT_WARN).orElse(null);

        StringJoiner verbs = new StringJoiner(", ");
        stats.verbs().forEach((verb, count) -> verbs.add(verb.name().toLowerCase() + " " + count));

        StringBuilder sb = new StringBuilder()
                .append(request.getMethod()).append(' ').append(request.getRequestURI())
                .append(DIM).append(" tenant=").append(orDash(stats.tenantSeq())).append(" user=").append(orDash(stats.userSeq()))
                .append(" → ").append(RESET)
                .append(many ? YELLOW : "").append(stats.queries()).append(stats.queries() == 1 ? " query" : " queries").append(RESET)
                .append(DIM).append(" (").append(verbs).append(") | ").append(RESET)
                .append("sql ").append(slow ? YELLOW + ms(stats.sqlNanos()) + RESET : SqlLogListener.elapsed(stats.sqlNanos()))
                .append(DIM).append(" | req ").append(ms(requestNanos)).append(RESET);
        if (stats.commits() > 0) {
            sb.append(DIM).append(" | ").append(RESET).append(GREEN_BOLD).append("commit ").append(stats.commits()).append(RESET);
        }
        if (stats.rollbacks() > 0) {
            sb.append(DIM).append(" | ").append(RESET).append(MAGENTA_BOLD).append("rollback ").append(stats.rollbacks()).append(RESET);
        }
        if (repeat != null) {
            sb.append(DIM).append(" | ").append(RESET).append(YELLOW)
                    .append("same query ×").append(repeat.count()).append(" at ").append(repeat.caller()).append(" — N+1?").append(RESET);
        }
        log.log(many || slow || repeat != null ? Level.WARN : Level.INFO, sb.toString());
    }

    private static String ms(long nanos) {
        double ms = nanos / (double) TimeUnit.MILLISECONDS.toNanos(1);
        return ms < 10 ? String.format("%.1f ms", ms) : String.format("%d ms", Math.round(ms));
    }

    private static String orDash(Integer value) {
        return value == null ? "-" : value.toString();
    }
}

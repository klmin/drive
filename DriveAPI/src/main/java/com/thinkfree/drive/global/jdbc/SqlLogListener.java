package com.thinkfree.drive.global.jdbc;

import com.p6spy.engine.common.ConnectionInformation;
import com.p6spy.engine.common.PreparedStatementInformation;
import com.p6spy.engine.common.ResultSetInformation;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.SimpleJdbcEventListener;
import com.thinkfree.drive.global.jdbc.SqlRequestStats.Verb;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.util.SecurityUtil;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.SQLException;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 로그(local 전용 — {@code decorator.datasource.enabled=true} 일 때만 빈). p6spy 가 DataSource 를 감싸므로 JPA·JPQL·JdbcClient 쿼리가 전부 지나간다.
 * <p>
 * p6spy 기본 로깅은 끄고(yml {@code enable-logging: false}) 이 리스너가 직접 찍는다 — 기존 log4j2 패턴의 클래스·[라인] 자리에 쿼리를 부른 우리 코드 위치를 넣고
 * ({@code withLocation}), 본문은 {@code 클래스.메서드() 실행시간 | 결과 건수 | tx=…} + 전체 SQL(바인딩 값 채움, 자르지 않음 — 120자 이하는 한 줄, 넘으면 절 단위 줄바꿈).
 * <ul>
 *   <li>SELECT 는 결과를 다 읽고 닫힐 때 한 줄({@code fetched N rows}, 시간 = 실행 + 읽기). INSERT·UPDATE·DELETE 는 반영 건수, 0건이면 노랑.</li>
 *   <li>배치({@code batchUpdate})는 실행 1회에 한 줄 — 행 수·반영 건수, SQL 은 {@code ?} 그대로(값은 행마다 다름).</li>
 *   <li>실패는 WARN + {@code FAILED SQLState 메시지}(앱이 잡아 처리하는 실패도 있어 ERROR 아님), 1초 이상도 WARN.</li>
 *   <li>{@code tx=} 는 쿼리를 부른 메서드와 다를 때만. 트랜잭션 밖 SELECT 는 생략, 트랜잭션 밖 쓰기는 노랑 {@code no tx}, Spring Data 리포 기본 트랜잭션은 {@code tx=repo}(쓰기면 노랑). COMMIT 은 쓰기 트랜잭션만, ROLLBACK 은 전부(WARN).
 *       이름 없는 트랜잭션(Spring Session·ShedLock 의 프로그래밍 방식 tx — 우리 코드는 전부 {@code @Transactional})의 커밋·롤백은 생략.</li>
 *   <li>Spring Session·ShedLock 테이블 쿼리는 소음이라 거른다. 요청 스레드면 {@link SqlRequestStats} 에 집계(요청 끝 요약 = {@link SqlRequestSummaryFilter}).</li>
 * </ul>
 * 호출 위치 = 스택에서 처음 만나는 우리 코드 프레임. Spring Data 리포는 프록시라 그 리포를 부른 서비스가 찍힌다.
 */
@Component
@ConditionalOnProperty(name = "decorator.datasource.enabled", havingValue = "true")
public class SqlLogListener extends SimpleJdbcEventListener {

    static final String RESET = "\u001B[0m";
    static final String BOLD = "\u001B[1m";
    static final String DIM = "\u001B[2m";
    static final String GREEN = "\u001B[32m";
    static final String GREEN_BOLD = "\u001B[1;32m";
    static final String YELLOW = "\u001B[33m";
    static final String RED_BOLD = "\u001B[1;31m";
    static final String MAGENTA_BOLD = "\u001B[1;35m";
    static final String BLUE = "\u001B[34m";
    static final String CYAN = "\u001B[36m";

    private static final Logger log = LogManager.getLogger("sql");

    private static final String APP_PACKAGE = "com.thinkfree.drive.";
    private static final String SPRING_DATA_PACKAGE = "org.springframework.data.";
    private static final String INDENT = "    ";
    private static final int ONE_LINE_MAX = 120;
    private static final long SLOW_NANOS = TimeUnit.SECONDS.toNanos(1);

    /** 걸러낼 인프라 테이블 — Spring Session(OAuth2 AS 세션)·ShedLock(스케줄러 잠금). */
    private static final Pattern NOISE = Pattern.compile("\\b(spring_session\\w*|shedlock)\\b", Pattern.CASE_INSENSITIVE);
    /**
     * 문자열 리터럴('' 이스케이프 포함) — 줄 나눔·공백 정리·강조는 리터럴 밖에서만.
     * 펼친 루프 + 소유 수량자 형태여야 한다 — {@code '(?:[^']|'')*'} 처럼 반복 안에 분기를 두면 java.util.regex 가 글자마다 재귀해
     * 수천 자 리터럴(oauth2_authorization.attributes INSERT 등)에서 StackOverflowError 가 나고, 그 Error 가 쿼리 실행 쪽으로 전파된다.
     */
    private static final Pattern QUOTED = Pattern.compile("'[^']*+(?:''[^']*+)*+'");
    /** 이 절 앞에서 줄을 나눈다(join 은 앞의 left·outer 등과, from 은 delete 와 한 줄). */
    private static final Pattern CLAUSE = Pattern.compile(
            "(?<!\\b(?:left|right|full|inner|cross|outer|delete)) (?=(?:from|where|(?:(?:left|right|full|inner|cross) )?(?:outer )?join"
                    + "|group by|order by|having|limit|offset|fetch first|union(?: all)?|values|set|on conflict|returning)\\b)",
            Pattern.CASE_INSENSITIVE);
    /** 강조할 키워드(리터럴 밖에서만). */
    private static final Pattern KEYWORD = Pattern.compile(
            "\\b(?:select|insert into|update|delete from|from|where|(?:(?:left|right|full|inner|cross) )?(?:outer )?join|on conflict|on"
                    + "|and|or|not|in|exists|is null|is not null|between|like|order by|group by|having|limit|offset|fetch first|rows only"
                    + "|values|set|returning|do update|do nothing|union all|union|distinct|case|when|then|else|end|as|asc|desc|with)\\b",
            Pattern.CASE_INSENSITIVE);

    /** 실행은 끝났고 결과를 읽는 중인 SELECT — 결과셋(또는 문장)이 닫힐 때 찍는다. 같은 스레드에서 닫히므로 ThreadLocal. */
    private static final ThreadLocal<Map<StatementInformation, PendingQuery>> PENDING = ThreadLocal.withInitial(IdentityHashMap::new);

    private record Caller(StackTraceElement frame) {
        String label() {
            return simpleName(frame.getClassName()) + "." + frame.getMethodName();
        }
    }

    /** name = {@code 클래스.메서드}(Spring Data 리포 기본 트랜잭션이면 "repo"), repo = 서비스 트랜잭션 없이 리포 호출마다 열린 트랜잭션. */
    private record Tx(String name, boolean readOnly, boolean repo) {
        static final Tx NONE = new Tx(null, false, false);
    }

    private static final class PendingQuery {
        final String sql;
        final String preparedSql;
        final Optional<Caller> caller;
        final Tx tx;
        long nanos;
        int rows;

        PendingQuery(String sql, String preparedSql, Optional<Caller> caller, Tx tx, long nanos) {
            this.sql = sql;
            this.preparedSql = preparedSql;
            this.caller = caller;
            this.tx = tx;
            this.nanos = nanos;
        }
    }

    // ---- SELECT: 결과셋이 닫힐 때까지 미룸 ----

    @Override
    public void onAfterExecuteQuery(PreparedStatementInformation info, long elapsedNanos, SQLException e) {
        guard(() -> query(info, info.getSqlWithValues(), info.getSql(), elapsedNanos, e));
    }

    @Override
    public void onAfterExecuteQuery(StatementInformation info, long elapsedNanos, String sql, SQLException e) {
        guard(() -> query(info, sql, sql, elapsedNanos, e));
    }

    @Override
    public void onAfterResultSetNext(ResultSetInformation info, long elapsedNanos, boolean hasNext, SQLException e) {
        guard(() -> {
            PendingQuery pending = PENDING.get().get(info.getStatementInformation());
            if (pending != null) {
                pending.nanos += elapsedNanos;
                if (hasNext) {
                    pending.rows++;
                }
            }
        });
    }

    @Override
    public void onAfterResultSetClose(ResultSetInformation info, SQLException e) {
        guard(() -> flush(info.getStatementInformation()));
    }

    @Override
    public void onAfterStatementClose(StatementInformation info, SQLException e) {
        guard(() -> flush(info));
    }

    // ---- INSERT·UPDATE·DELETE ----

    @Override
    public void onAfterExecuteUpdate(PreparedStatementInformation info, long elapsedNanos, int rowCount, SQLException e) {
        guard(() -> write(info.getSqlWithValues(), info.getSql(), elapsedNanos, rowCount, e));
    }

    @Override
    public void onAfterExecuteUpdate(StatementInformation info, long elapsedNanos, String sql, int rowCount, SQLException e) {
        guard(() -> write(sql, sql, elapsedNanos, rowCount, e));
    }

    // ---- execute() — 결과 종류를 모름(DDL 등) ----

    @Override
    public void onAfterExecute(PreparedStatementInformation info, long elapsedNanos, SQLException e) {
        guard(() -> write(info.getSqlWithValues(), info.getSql(), elapsedNanos, -1, e));
    }

    @Override
    public void onAfterExecute(StatementInformation info, long elapsedNanos, String sql, SQLException e) {
        guard(() -> write(sql, sql, elapsedNanos, -1, e));
    }

    // ---- 배치 ----

    @Override
    public void onAfterExecuteBatch(StatementInformation info, long elapsedNanos, int[] updateCounts, SQLException e) {
        guard(() -> batch(info, elapsedNanos, updateCounts, e));
    }

    // ---- 커밋·롤백 ----

    @Override
    public void onAfterCommit(ConnectionInformation info, long elapsedNanos, SQLException e) {
        guard(() -> {
            Tx tx = currentTx();
            if (tx.name() == null || (tx.readOnly() && e == null)) {
                return;
            }
            SqlRequestStats.current().ifPresent(SqlRequestStats::commit);
            endTx(info, elapsedNanos, tx, GREEN_BOLD + "COMMIT" + RESET, e != null ? Level.WARN : Level.INFO, e);
        });
    }

    @Override
    public void onAfterRollback(ConnectionInformation info, long elapsedNanos, SQLException e) {
        guard(() -> {
            Tx tx = currentTx();
            if (tx.name() == null) {
                return;
            }
            SqlRequestStats.current().ifPresent(SqlRequestStats::rollback);
            endTx(info, elapsedNanos, tx, MAGENTA_BOLD + "ROLLBACK" + RESET, Level.WARN, e);
        });
    }

    /**
     * 리스너 콜백 격리 — p6spy 는 리스너가 던진 예외·Error 를 그대로 JDBC 호출자에게 전파하므로(쿼리 실패·트랜잭션 롤백),
     * SQL 로그 쪽 실패는 여기서 삼키고 한 줄 경고만 남긴다. 로그 때문에 쿼리가 깨지지 않게 하는 마지막 방어선.
     */
    private static void guard(Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException | StackOverflowError ex) {
            try {
                log.warn("SQL 로그 처리 실패(쿼리는 정상 진행) : {}", ex.toString());
            } catch (RuntimeException ignored) {
                // 경고조차 못 찍는 경우 — 쿼리 진행이 우선
            }
        }
    }

    // ---- 구현 ----

    private void query(StatementInformation info, String sql, String preparedSql, long elapsedNanos, SQLException e) {
        if (isNoise(sql)) {
            return;
        }
        flush(info); // 같은 문장 재실행 — 앞 결과를 먼저 찍는다
        Optional<Caller> caller = caller();
        if (e != null) {
            emit(caller, sql, preparedSql, elapsedNanos, head(caller, elapsedNanos).append(tx(caller, currentTx(), verb(sql))), e);
            return;
        }
        PENDING.get().put(info, new PendingQuery(sql, preparedSql, caller, currentTx(), elapsedNanos));
    }

    private void flush(StatementInformation info) {
        PendingQuery p = PENDING.get().remove(info);
        if (p == null) {
            return;
        }
        Verb verb = verb(p.sql);
        // INSERT … RETURNING(Hibernate IDENTITY 저장)도 결과셋이라 이 경로 — 반영 건수로 표시
        String result = verb == Verb.SELECT ? meta("fetched " + plural(p.rows)) : rows(verb, p.rows);
        StringBuilder head = head(p.caller, p.nanos).append(result).append(tx(p.caller, p.tx, verb));
        emit(p.caller, p.sql, p.preparedSql, p.nanos, head, null);
    }

    private void batch(StatementInformation info, long elapsedNanos, int[] updateCounts, SQLException e) {
        String sql = info.getSql();
        if (isNoise(sql)) {
            return;
        }
        Optional<Caller> caller = caller();
        StringBuilder head = head(caller, elapsedNanos).append(meta("batch " + (updateCounts == null ? "?" : updateCounts.length) + " rows"));
        if (e == null && updateCounts != null) {
            head.append(rows(verb(sql), sumKnown(updateCounts)));
        }
        head.append(tx(caller, currentTx(), verb(sql)));
        emit(caller, sql, sql, elapsedNanos, head, e);
    }

    private void write(String sql, String preparedSql, long elapsedNanos, int rowCount, SQLException e) {
        if (isNoise(sql)) {
            return;
        }
        Optional<Caller> caller = caller();
        StringBuilder head = head(caller, elapsedNanos);
        if (e == null && rowCount >= 0) {
            head.append(rows(verb(sql), rowCount));
        }
        head.append(tx(caller, currentTx(), verb(sql)));
        emit(caller, sql, preparedSql, elapsedNanos, head, e);
    }

    private void emit(Optional<Caller> caller, String sql, String preparedSql, long nanos, StringBuilder head, SQLException e) {
        SqlRequestStats.current().ifPresent(stats -> {
            stats.query(verb(sql), preparedSql, caller.map(c -> c.label() + ":" + c.frame().getLineNumber()).orElse("?"), nanos);
            stats.identify(TenantContext.get(), SecurityUtil.getUserSeq());
        });
        StringBuilder message = new StringBuilder(head);
        if (e != null) {
            message.append(System.lineSeparator()).append(INDENT).append(RED_BOLD).append("FAILED ")
                    .append(e.getSQLState() == null ? "" : e.getSQLState() + " ").append(failure(e)).append(RESET);
        }
        message.append(format(sql));
        Level level = e != null || nanos >= SLOW_NANOS ? Level.WARN : Level.INFO;
        log.atLevel(level).withLocation(caller.map(Caller::frame).orElse(null)).log(message.toString());
    }

    private void endTx(ConnectionInformation info, long nanos, Tx tx, String label, Level level, SQLException e) {
        Optional<Caller> caller = caller();
        StringBuilder message = new StringBuilder(label).append(' ').append(elapsed(nanos))
                .append(meta("tx=" + tx.name() + (tx.readOnly() ? " (ro)" : ""))).append(meta("conn " + info.getConnectionId()));
        if (e != null) {
            message.append(' ').append(RED_BOLD).append("FAILED ").append(failure(e)).append(RESET);
        }
        log.atLevel(level).withLocation(caller.map(Caller::frame).orElse(null)).log(message.toString());
    }

    /** PG 오류 메시지 — "ERROR: " 접두 제거, 여러 줄(Detail·Hint)은 들여쓰기 맞춤. */
    private static String failure(SQLException e) {
        String message = String.valueOf(e.getMessage()).replaceFirst("^ERROR: ", "").strip();
        return message.replaceAll("\\R\\s*",Matcher.quoteReplacement(System.lineSeparator() + INDENT + "       "));
    }

    private static StringBuilder head(Optional<Caller> caller, long nanos) {
        StringBuilder sb = new StringBuilder();
        caller.ifPresent(c -> sb.append(BOLD).append(c.label()).append("()").append(RESET).append(' '));
        return sb.append(elapsed(nanos));
    }

    private static String rows(Verb verb, int count) {
        String action = switch (verb) {
            case INSERT -> "inserted ";
            case UPDATE -> "updated ";
            case DELETE -> "deleted ";
            default -> "affected ";
        };
        String text = action + plural(count);
        boolean zeroWrite = count == 0 && (verb == Verb.UPDATE || verb == Verb.DELETE);
        return zeroWrite ? warn(text) : meta(text);
    }

    /**
     * tx 표시. 쿼리를 부른 메서드와 같으면 생략, 다르면 {@code tx=클래스.메서드}.
     * 트랜잭션 밖 SELECT 는 생략, 트랜잭션 밖 쓰기는 노랑 {@code no tx}. Spring Data 리포 기본 트랜잭션은 {@code tx=repo}(쓰기면 노랑) —
     * 둘 다 서비스 트랜잭션이 없어 쓰기가 문장·호출마다 따로 커밋되는 상태(대개 {@code @Transactional} 누락).
     */
    private static String tx(Optional<Caller> caller, Tx tx, Verb verb) {
        boolean write = verb == Verb.INSERT || verb == Verb.UPDATE || verb == Verb.DELETE;
        if (tx.name() == null) {
            return write ? warn("no tx") : "";
        }
        String text = "tx=" + tx.name() + (tx.readOnly() ? " (ro)" : "");
        if (tx.repo()) {
            return write ? warn(text) : meta(text);
        }
        return caller.map(c -> c.label().equals(tx.name())).orElse(false) ? "" : meta(text);
    }

    private static String meta(String text) {
        return DIM + " | " + text + RESET;
    }

    private static String warn(String text) {
        return DIM + " | " + RESET + YELLOW + text + RESET;
    }

    static String elapsed(long nanos) {
        double ms = nanos / (double) TimeUnit.MILLISECONDS.toNanos(1);
        String color = ms >= 1000 ? RED_BOLD : ms >= 100 ? YELLOW : GREEN;
        String text = ms < 10 ? String.format("%.1f ms", ms) : String.format("%d ms", Math.round(ms));
        return color + text + RESET;
    }

    private static String plural(int n) {
        return n + (n == 1 ? " row" : " rows");
    }

    private static int sumKnown(int[] counts) {
        int sum = 0;
        for (int c : counts) {
            sum += Math.max(c, 0); // SUCCESS_NO_INFO(-2) 는 0 으로
        }
        return sum;
    }

    private static boolean isNoise(String sql) {
        return sql == null || sql.isBlank() || NOISE.matcher(sql).find();
    }

    static Verb verb(String sql) {
        String s = sql.stripLeading().toLowerCase(Locale.ROOT);
        if (s.startsWith("select") || s.startsWith("with") || s.startsWith("(")) {
            return Verb.SELECT;
        }
        if (s.startsWith("insert")) {
            return Verb.INSERT;
        }
        if (s.startsWith("update")) {
            return Verb.UPDATE;
        }
        if (s.startsWith("delete")) {
            return Verb.DELETE;
        }
        return Verb.OTHER;
    }

    /** 현재 Spring 트랜잭션 — 이름은 {@code 클래스.메서드} 로 줄이고, Spring Data 리포 기본 트랜잭션({@code SimpleJpaRepository.*})은 "repo". */
    private static Tx currentTx() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return Tx.NONE;
        }
        String name = TransactionSynchronizationManager.getCurrentTransactionName();
        if (name == null) {
            return Tx.NONE;
        }
        boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        if (name.startsWith(SPRING_DATA_PACKAGE)) {
            return new Tx("repo", readOnly, true);
        }
        int method = name.lastIndexOf('.');
        String shortName = method < 0 ? name : simpleName(name.substring(0, method)) + name.substring(method);
        return new Tx(shortName, readOnly, false);
    }

    /**
     * 공백을 한 칸으로 접고 키워드 강조(리터럴 안은 그대로). 접은 길이가 {@value #ONE_LINE_MAX}자 이하면 한 줄,
     * 넘으면 주요 절 앞에서 줄을 나눈다. 자르지 않는다.
     * 꾸미기가 실패하면 원문 SQL 을 그대로 찍는다 — 리스너 예외·Error 는 p6spy 를 거쳐 쿼리 실패(트랜잭션 롤백)가 되므로 로그 꾸미기 때문에 쿼리를 깨지 않는다.
     */
    private static String format(String sql) {
        try {
            return pretty(sql);
        } catch (RuntimeException | StackOverflowError e) {
            return System.lineSeparator() + INDENT + sql.strip();
        }
    }

    private static String pretty(String sql) {
        String trimmed = sql.strip();
        boolean breakLines = trimmed.replaceAll("\\s+", " ").length() > ONE_LINE_MAX;
        StringBuilder out = new StringBuilder();
        Matcher m = QUOTED.matcher(trimmed);
        int last = 0;
        while (m.find()) {
            out.append(unquoted(trimmed.substring(last, m.start()), breakLines)).append(CYAN).append(m.group()).append(RESET);
            last = m.end();
        }
        out.append(unquoted(trimmed.substring(last), breakLines));
        return System.lineSeparator() + INDENT + out.toString().strip();
    }

    private static String unquoted(String part, boolean breakLines) {
        String collapsed = part.replaceAll("\\s+", " ");
        String broken = breakLines
                ? CLAUSE.matcher(collapsed).replaceAll(Matcher.quoteReplacement(System.lineSeparator() + INDENT))
                : collapsed;
        return KEYWORD.matcher(broken).replaceAll(r -> Matcher.quoteReplacement(BLUE + r.group() + RESET));
    }

    /** 처음 만나는 우리 코드 프레임(SQL 로그 클래스·CGLIB·JDK 프록시 제외). */
    private static Optional<Caller> caller() {
        return StackWalker.getInstance().walk(frames -> frames
                .filter(f -> f.getClassName().startsWith(APP_PACKAGE))
                .filter(f -> !f.getClassName().startsWith(SqlLogListener.class.getPackageName() + ".Sql"))
                .filter(f -> !f.getClassName().contains("$$")) // CGLIB
                .filter(f -> !f.getClassName().contains("$Proxy")) // JDK 동적 프록시(Spring Data 리포 인터페이스 — 비 public 이면 같은 패키지에 생성)
                .findFirst()
                .map(f -> new Caller(f.toStackTraceElement())));
    }

    private static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }
}

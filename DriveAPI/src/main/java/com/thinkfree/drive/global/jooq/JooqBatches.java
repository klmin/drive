package com.thinkfree.drive.global.jooq;

import org.jooq.Batch;
import org.jooq.DSLContext;
import org.jooq.TableRecord;
import org.jooq.UpdatableRecord;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * jOOQ 레코드 배치 — 행을 생성 레코드(컬럼 setter 로 값을 넣은 것)로 바꿔 jOOQ 가 SQL 로 만들고 JDBC 배치로 실행한다.
 * 자리표시자·바인드 순서를 맞출 필요가 없다 — set 한 컬럼만 문장에 들어가고({@code changed} 플래그), UPDATE 의 WHERE 는 PK.
 * 같은 컬럼을 set 한 레코드끼리는 SQL 이 같아 PreparedStatement 하나·{@code executeBatch} 한 번으로 묶인다(행마다 set 컬럼이 다르면 묶음이 갈라진다).
 * <p>
 * {@link #CHUNK_SIZE} 행씩 변환하고 바로 실행한다 — 레코드가 묶음 크기만큼만 메모리에 있고 한 문장으로 오래 잡지 않게(요청 행 수 상한이 없는 일괄 API 대비).
 * 나눠도 호출부 트랜잭션 하나라 중간 실패면 전체 롤백된다.
 */
public final class JooqBatches {

    /** 한 번의 {@code executeBatch} 로 보낼 최대 행 수 */
    public static final int CHUNK_SIZE = 1000;

    /** 행값 IN 한 문장에 넣을 최대 행 수 — PG 바인드 변수 상한(32767) 아래로(3-튜플 기준 30000개) */
    public static final int IN_ROWS_CHUNK_SIZE = 10_000;

    private JooqBatches() {
    }

    /**
     * 행마다 UPDATE(set 한 컬럼, WHERE PK).
     *
     * @param toRecord 행 → 레코드. PK 는 값을 넣은 뒤 {@code changed(pk, false)} 로 SET 에서 뺀다
     * @return 행별 처리 건수(rows 순서)
     */
    public static <T> int[] update(DSLContext dsl, List<T> rows, Function<? super T, ? extends UpdatableRecord<?>> toRecord) {
        return inChunks(rows, toRecord, dsl::batchUpdate);
    }

    /**
     * 행마다 INSERT(set 한 컬럼).
     *
     * @return 행별 처리 건수(rows 순서)
     */
    public static <T> int[] insert(DSLContext dsl, List<T> rows, Function<? super T, ? extends TableRecord<?>> toRecord) {
        return inChunks(rows, toRecord, dsl::batchInsert);
    }

    /**
     * 행마다 문장 하나({@code statement} 안에서 {@code execute()})를 jOOQ {@code batched} 로 모아 실행한다 — 같은 SQL 이 이어지는 동안 PreparedStatement 하나·{@code executeBatch}.
     * upsert 처럼 레코드 배치({@link #insert})로 표현할 수 없는 문장용. {@link #CHUNK_SIZE} 행마다 batched 를 새로 열어 그만큼씩 보낸다.
     *
     * @param statement (batched DSLContext, 행) → 그 DSLContext 로 문장을 만들어 {@code execute()}
     */
    public static <T> void batched(DSLContext dsl, List<T> rows, BiConsumer<DSLContext, ? super T> statement) {
        for (int from = 0; from < rows.size(); from += CHUNK_SIZE) {
            List<T> chunk = rows.subList(from, Math.min(from + CHUNK_SIZE, rows.size()));
            dsl.batched(c -> chunk.forEach(row -> statement.accept(c.dsl(), row)));
        }
    }

    /**
     * 행값 IN 한 문장({@code (a, b, c) IN ((?,?,?), …)})을 {@link #IN_ROWS_CHUNK_SIZE} 행씩 나눠 실행한다 — 항목마다 문장을 따로 보내는 배치 대신 묶음당 한 번 왕복.
     *
     * @param statement 묶음 → 실행(처리 건수)
     * @return 처리 건수 합. 빈 목록이면 실행 없이 0
     */
    public static <T> int executeInRowChunks(List<T> rows, ToIntFunction<List<T>> statement) {
        int count = 0;
        for (int from = 0; from < rows.size(); from += IN_ROWS_CHUNK_SIZE) {
            count += statement.applyAsInt(rows.subList(from, Math.min(from + IN_ROWS_CHUNK_SIZE, rows.size())));
        }
        return count;
    }

    private static <T, R> int[] inChunks(List<T> rows, Function<? super T, ? extends R> toRecord, Function<List<R>, Batch> batch) {
        int[] counts = new int[rows.size()];
        for (int from = 0; from < rows.size(); from += CHUNK_SIZE) {
            List<R> records = rows.subList(from, Math.min(from + CHUNK_SIZE, rows.size())).stream().<R>map(toRecord).toList();
            int[] chunkCounts = batch.apply(records).execute();
            System.arraycopy(chunkCounts, 0, counts, from, chunkCounts.length);
        }
        return counts;
    }
}

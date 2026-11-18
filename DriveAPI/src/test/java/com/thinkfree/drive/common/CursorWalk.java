package com.thinkfree.drive.common;

import com.thinkfree.drive.api.page.query.CursorPaginationQuery;
import com.thinkfree.drive.resource.enums.ResourceType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 커서 목록 리포 테스트 보조 — 첫 페이지부터 마지막 행으로 다음 커서를 만들어(서비스가 내보내는 값과 같게) 끝까지 읽고 이어 붙인다.
 * 이어 붙인 결과를 한 번에 읽은 결과와 비교하면 커서 게이트가 정렬 키와 맞는지(중복·누락·무한 반복 없음)를 기대값 없이 확인할 수 있다.
 */
public final class CursorWalk {

    private static final int MAX_PAGES = 60;

    private CursorWalk() {
    }

    /** 커서 값이 null(SIZE 의 폴더)이면 0 — {@code SortFieldHolder.getSizeCursorValue} 와 같다. Instant 는 ISO 문자열. */
    public static <R> List<R> all(String label, Function<CursorPaginationQuery, List<R>> call,
                                  Function<R, Object> cursorValue, Function<R, Long> cursorId, Function<R, ResourceType> cursorType, int pageSize) {
        List<R> rows = new ArrayList<>();
        CursorPaginationQuery page = CursorPaginationQuery.of(null, null, null, pageSize);
        for (int i = 0; i < MAX_PAGES; i++) {
            List<R> chunk = call.apply(page);
            assertThat(chunk.size()).as("%s page%d 크기", label, i).isLessThanOrEqualTo(pageSize);
            rows.addAll(chunk);
            if (chunk.size() < pageSize) {
                return rows;
            }
            R last = chunk.get(chunk.size() - 1);
            Object value = cursorValue.apply(last);
            page = CursorPaginationQuery.of(value == null ? 0L : value instanceof Instant t ? t.toString() : value, cursorId.apply(last), cursorType.apply(last), pageSize);
        }
        throw new AssertionError(label + ": 커서가 " + MAX_PAGES + "페이지 안에 끝나지 않음");
    }

    /** 한 번에 읽을 때 쓰는 첫 페이지(크기 1000). */
    public static CursorPaginationQuery firstPage() {
        return CursorPaginationQuery.of(null, null, null, 1000);
    }
}

package com.thinkfree.drive.application.page;

import com.thinkfree.drive.global.time.DriveTime;
import com.thinkfree.drive.resource.enums.ResourceType;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

public record CursorPageInfo<T>
(
    List<T> list,
    int pageSize,
    Object nextCursorValue,
    Number nextCursor,
    ResourceType nextCursorType,
    boolean hasNext
)
{
    /**
     * @param mapped 응답으로 내보낼 Info 목록
     * @param source 커서 추출 대상인 도메인 결과 목록 (mapped와 같은 순서)
     */
    public static <S, T> CursorPageInfo<T> of(List<T> mapped, List<S> source, int pageSize,
                                              Function<S, ?> valueExtractor, Function<S, Number> idExtractor, Function<S, ResourceType> typeExtractor) {
        boolean hasNext = source.size() == pageSize;
        S lastItem = hasNext ? source.get(source.size() - 1) : null;
        Object nextCursorValue = (hasNext && valueExtractor != null) ? toCursorValue(valueExtractor.apply(lastItem)) : null;
        Number nextCursor = (hasNext && idExtractor != null) ? idExtractor.apply(lastItem) : null;
        ResourceType nextCursorType = (hasNext && typeExtractor != null) ? typeExtractor.apply(lastItem) : null;
        return new CursorPageInfo<>(mapped, pageSize, nextCursorValue, nextCursor, nextCursorType, hasNext);
    }

    /**
     * 날짜 커서는 설정 시간대 벽시계(LocalDateTime)로 내보낸다 — 응답 JSON 이 Instant 전환 전과 같은 {@code yyyy-MM-ddTHH:mm:ss} 형식이고,
     * 클라이언트가 되돌려 보낸 값은 리포가 {@link com.thinkfree.drive.global.jooq.JooqCursors#timestamp} 로 같은 시간대로 해석한다.
     */
    private static Object toCursorValue(Object value) {
        return value instanceof Instant instant ? DriveTime.toLocal(instant) : value;
    }
}

package com.thinkfree.drive.mcp.util;

import com.thinkfree.drive.constants.DriveConstants;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;

/**
 * MCP 툴 입력 정규화 유틸 — 예상된 검증 실패는 IllegalArgumentException(친절 메시지)으로 던지고,
 * McpToolResult.execute가 이를 인밴드 {error} 반환으로 변환한다 (LLM이 교정 재시도할 수 있게).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class McpToolParams {

    // @ToolParam의 required는 LLM에게 보여주는 스키마 선언일 뿐 런타임 강제가 아니라서 필수 값은 툴 본문이 직접 검증한다.
    // 웹의 path variable 라우팅·@NotNull 검증 등가 — 메시지는 LLM이 소비하는 프로토콜 텍스트라 로케일 이관에서 의도적 제외.
    public static void require(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + "는 필수입니다.");
        }
    }

    // 웹 @NotBlank 등가 — 문자열은 null뿐 아니라 공백도 거른다
    public static void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + "은 필수입니다.");
        }
    }

    // 웹 @NotEmpty 등가 — 배열 파라미터는 null·빈 배열을 거른다. 요소 공백 제거·중복 제거는 호출 측 몫(계약별로 다름)
    public static void requireNonEmpty(Collection<?> value, String name) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + "는 1개 이상 지정해야 합니다.");
        }
    }

    // DB 컬럼 길이 등가 검증 — 웹은 화면 maxLength가 막지만 MCP 입력은 그대로 SQL까지 내려가 "Data too long" 예외가 난다(McpToolResult가 일반 메시지로 바꾸지만 LLM은 무엇을 고칠지 모름).
    // null·공백은 통과(필수 여부는 require/requireText가 담당).
    public static void requireMaxLength(String value, int maxLength, String name) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(name + "은(는) 최대 " + maxLength + "자입니다: " + value.length() + "자");
        }
    }

    // 쌍으로만 의미 있는 선택 파라미터(날짜 범위 시작·종료, cursorId·cursorValue 등) — 둘 다 있거나 둘 다 없으면 통과, 한쪽만 오면 인밴드 검증 에러.
    // 하부 SQL의 <if>가 양쪽 값을 요구해 한쪽만 오면 조건이 조용히 빠지는데, LLM은 한쪽만 넘기기 쉬워 "필터 안 걸린 전체 목록"을 필터 결과로 오독할 수 있다.
    // 빈 문자열은 미지정으로 취급한다(LLM이 ""를 자주 보냄).
    public static void requireTogether(Object first, Object second, String firstName, String secondName) {
        if (isPresent(first) != isPresent(second)) {
            throw new IllegalArgumentException(firstName + "와 " + secondName + "는 함께 지정해야 합니다.");
        }
    }

    private static boolean isPresent(Object value) {
        return value instanceof String s ? StringUtils.hasText(s) : value != null;
    }

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public static int normalizePageSize(Integer pageSize) {
        if (pageSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize는 1~" + MAX_PAGE_SIZE + " 사이여야 합니다: " + pageSize);
        }
        return pageSize;
    }

    public static final int DEFAULT_PAGE_INDEX = 1;

    // 오프셋(pi/ps) 페이지네이션 툴용 — 웹 PagingRequest의 @Min(1) 검증 등가
    public static int normalizePageIndex(Integer pageIndex) {
        if (pageIndex == null) {
            return DEFAULT_PAGE_INDEX;
        }
        if (pageIndex < 1) {
            throw new IllegalArgumentException("pageIndex는 1 이상이어야 합니다: " + pageIndex);
        }
        return pageIndex;
    }

    public static LocalDate parseDate(String value, String paramName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(paramName + "은(는) yyyy-MM-dd 형식이어야 합니다: " + value);
        }
    }

    // 웹 @Future 검증 등가 — 만료일 등 기한성 파라미터 공통. 무기한 불허, 지난 일시는 즉시 못 쓰는 값이 되므로 미래만 허용
    public static void requireFuture(Instant value, String rawValue, String paramName) {
        if (!value.isAfter(Instant.now())) {
            throw new IllegalArgumentException(paramName + "는 미래 일시여야 합니다: " + rawValue);
        }
    }

    // 툴이 nextCursorValue로 내보내는 포맷(ToolDateTimeSupport와 동일). ISO_LOCAL_DATE_TIME은 'T' 구분자를 요구해 이 값을 못 파싱하므로 별도 포맷터가 필요하다.
    private static final DateTimeFormatter CURSOR_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DriveConstants.YYYY_MM_DD_HH_MM_SS_SSS);

    // 날짜형 커서 값(cursorValue) 정규화 — 커서는 서버가 직전 응답에 내보낸 값을 그대로 되돌리는 용도라 사람 친화적 해석(날짜만 → 자정/일말)을 하면 안 된다.
    // 허용 = 툴 자기 출력 포맷(yyyy-MM-dd HH:mm:ss.SSS) + 웹 ISO(yyyy-MM-ddTHH:mm:ss — 웹 응답을 본 LLM이 되돌릴 수 있음). 날짜만·그 외 문자열은 인밴드 거부.
    // 정규화하지 않으면 문자열이 SQL DATETIME 비교까지 그대로 내려가 쓰레기 값은 SQL 예외(isError 노출), 날짜만은 자정 해석으로 당일 항목 조용한 누락.
    // 반환은 툴 출력 포맷 문자열 — 커서 왕복 E2E로 SQL 비교가 검증된 포맷.
    public static String normalizeCursorDateTime(String value, String paramName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        LocalDateTime parsed;
        try {
            parsed = LocalDateTime.parse(value, CURSOR_DATE_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            try {
                parsed = LocalDateTime.parse(value); // ISO_LOCAL_DATE_TIME
            } catch (DateTimeParseException e2) {
                throw new IllegalArgumentException(
                        paramName + "은(는) 직전 응답의 nextCursorValue를 그대로 넘겨야 합니다(yyyy-MM-dd HH:mm:ss.SSS): " + value);
            }
        }
        return parsed.format(CURSOR_DATE_TIME_FORMATTER);
    }

}

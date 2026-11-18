package com.thinkfree.drive.api.v1.admin.auditlog.usecase;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * 거버넌스 CSV 조각을 행 단위로 다시 쓰며 eventId가 겹치는 행을 뺀다.
 * <p>
 * export-range는 한 조각도 서버가 페이지로 나눠 읽어, 그 사이 늦게 도착한 증적이 끼면 같은 행이 두 번 올 수 있다.
 * 조각끼리는 발생 시각 구간이 겹치지 않으므로 조각 안에서만 거르면 된다.
 * 값에 줄바꿈이 들어갈 수 있어(RFC 4180 따옴표) 따옴표 밖의 줄바꿈만 행 끝으로 본다.
 */
final class AuditLogCsvRows {

    private static final String EVENT_ID_HEADER = "eventId";

    private AuditLogCsvRows() {
    }

    /**
     * 조각을 쓴다.
     *
     * @param content     조각 바이트(BOM이 있으면 그대로 둔다)
     * @param writeHeader BOM과 머리 줄까지 쓸지(첫 조각만 true)
     * @return 뺀 중복 행 수. eventId 열을 못 찾으면 거르지 않고 -1
     */
    static int write(byte[] content, boolean writeHeader, OutputStream output) throws IOException {
        int headerStart = hasBom(content) ? 3 : 0;
        int headerEnd = recordEnd(content, headerStart);
        int idColumn = eventIdColumn(content, headerStart, headerEnd);
        if (writeHeader) {
            output.write(content, 0, headerEnd);
        }
        if (idColumn < 0) {
            output.write(content, headerEnd, content.length - headerEnd);
            return -1;
        }

        Set<String> seen = new HashSet<>();
        int duplicates = 0;
        int start = headerEnd;
        while (start < content.length) {
            int end = recordEnd(content, start);
            String eventId = field(content, start, end, idColumn);
            if (eventId == null || eventId.isEmpty() || seen.add(eventId)) {
                output.write(content, start, end - start);
            } else {
                duplicates++;
            }
            start = end;
        }
        return duplicates;
    }

    private static boolean hasBom(byte[] content) {
        return content.length >= 3
                && content[0] == (byte) 0xEF && content[1] == (byte) 0xBB && content[2] == (byte) 0xBF;
    }

    /** 행 끝(줄바꿈 다음) 위치. 따옴표 안의 줄바꿈은 값으로 본다. */
    private static int recordEnd(byte[] content, int start) {
        boolean quoted = false;
        for (int i = start; i < content.length; i++) {
            if (content[i] == '"') {
                quoted = !quoted;
            } else if (content[i] == '\n' && !quoted) {
                return i + 1;
            }
        }
        return content.length;
    }

    /** 머리 줄에서 eventId 열 위치. 없으면 -1. */
    private static int eventIdColumn(byte[] content, int start, int end) {
        for (int column = 0; ; column++) {
            String name = field(content, start, end, column);
            if (name == null) {
                return -1;
            }
            if (EVENT_ID_HEADER.equals(name)) {
                return column;
            }
        }
    }

    /** 행 [start, end)의 column번째 값(따옴표 해제). 열이 모자라면 null. */
    private static String field(byte[] content, int start, int end, int column) {
        boolean quoted = false;
        int current = 0;
        int fieldStart = start;
        int i = start;
        for (; i < end; i++) {
            byte b = content[i];
            if (b == '"') {
                quoted = !quoted;
            } else if (!quoted && (b == ',' || b == '\r' || b == '\n')) {
                if (current == column) {
                    return unquote(content, fieldStart, i);
                }
                if (b != ',') {
                    return null;
                }
                current++;
                fieldStart = i + 1;
            }
        }
        return current == column ? unquote(content, fieldStart, i) : null;
    }

    private static String unquote(byte[] content, int start, int end) {
        String value = new String(content, start, end - start, StandardCharsets.UTF_8);
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).replace("\"\"", "\"");
        }
        return value;
    }
}

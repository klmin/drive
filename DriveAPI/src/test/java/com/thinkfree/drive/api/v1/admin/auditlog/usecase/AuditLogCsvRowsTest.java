package com.thinkfree.drive.api.v1.admin.auditlog.usecase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuditLogCsvRows 조각 쓰기·중복 제거")
class AuditLogCsvRowsTest {

    private static final String BOM = "﻿";
    private static final String HEADER = "schemaVersion,eventId,eventType,occurredAt,object.name\r\n";

    @Test
    @DisplayName("첫 조각은 BOM·머리 줄을 쓰고 eventId가 겹치는 행을 뺀다")
    void firstChunkKeepsHeaderAndRemovesDuplicates() throws Exception {
        String csv = BOM + HEADER
                + "1,e2,LOGIN,2026-10-01T00:00:02Z,\r\n"
                + "1,e1,FILE_PREVIEW,2026-10-01T00:00:01Z,a.docx\r\n"
                + "1,e1,FILE_PREVIEW,2026-10-01T00:00:01Z,a.docx\r\n";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int duplicates = AuditLogCsvRows.write(bytes(csv), true, out);

        assertThat(duplicates).isEqualTo(1);
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo(BOM + HEADER
                + "1,e2,LOGIN,2026-10-01T00:00:02Z,\r\n"
                + "1,e1,FILE_PREVIEW,2026-10-01T00:00:01Z,a.docx\r\n");
    }

    @Test
    @DisplayName("이후 조각은 머리 줄을 떼고, 따옴표 안 줄바꿈·쉼표는 값으로 본다")
    void laterChunkSkipsHeaderAndHandlesQuotedValues() throws Exception {
        String row = "1,\"e1\",FILE_PREVIEW,2026-10-01T00:00:01Z,\"a,\r\nb.docx\"\r\n";
        String csv = HEADER + row + row;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int duplicates = AuditLogCsvRows.write(bytes(csv), false, out);

        assertThat(duplicates).isEqualTo(1);
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo(row);
    }

    @Test
    @DisplayName("eventId 열이 없으면 거르지 않고 그대로 쓴다")
    void writesAsIsWithoutEventIdColumn() throws Exception {
        String csv = "occurredAt,object.name\r\nt,a\r\nt,a\r\n";

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        assertThat(AuditLogCsvRows.write(bytes(csv), true, out)).isEqualTo(-1);
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo(csv);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}

package com.thinkfree.drive.global.time;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * {@link Instant} → 설정 시간대 벽시계의 ISO 로컬 문자열({@code 2026-10-07T09:00:00}).
 * Jackson 이 형식 없는 {@link java.time.LocalDateTime} 을 쓰던 것과 같은 출력이다({@link DateTimeFormatter#ISO_LOCAL_DATE_TIME}).
 * 응답 필드를 Instant 로 바꿔도 클라이언트 계약을 유지하려고 쓴다 — {@link DriveIsoLocalDateTimeFormat}.
 */
public class InstantAsLocalDateTimeSerializer extends StdSerializer<Instant> {

    public InstantAsLocalDateTimeSerializer() {
        super(Instant.class);
    }

    @Override
    public void serialize(Instant value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeString(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(DriveTime.toLocal(value)));
    }
}

package com.thinkfree.drive.global.time;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * MapStruct {@code uses = DriveTimeMapper.class} 용(componentModel = "spring" 이 빈으로 주입하므로 @Component) — 요청의 LocalDateTime 을 설정 시간대로 Instant 로 바꾼다.
 * MapStruct 는 LocalDateTime ↔ Instant 기본 변환이 없어, 이 매퍼를 빠뜨린 컨버터는 컴파일 오류로 걸린다.
 * 달력 날짜(LocalDate)는 하루 시작/끝 중 어느 쪽인지 정해야 하므로 자동 매핑하지 않고
 * {@code expression = "java(DriveTime.startOfDay(...))"} 로 명시한다.
 */
@Component
public class DriveTimeMapper {

    public Instant toInstant(LocalDateTime value) {
        return DriveTime.toInstant(value);
    }
}

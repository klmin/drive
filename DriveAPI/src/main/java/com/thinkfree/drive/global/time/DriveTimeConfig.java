package com.thinkfree.drive.global.time;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

/**
 * {@code drive.time-zone} 을 {@link DriveTime} 에 주입한다. 잘못된 시간대 이름이면 기동 실패.
 * Jackson 출력 시간대({@code spring.jackson.time-zone})도 같은 값을 참조한다(application.yml).
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class DriveTimeConfig {

    public DriveTimeConfig(@Value("${drive.time-zone:" + DriveTime.DEFAULT_ZONE_ID + "}") String timeZone) {
        ZoneId zone = ZoneId.of(timeZone);
        DriveTime.configure(zone);
        log.info("drive.time-zone = {}", zone);
    }
}

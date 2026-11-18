package com.thinkfree.drive.api.v1.admin.auditlog.enums;

import com.thinkfree.drive.auditlog.enums.EventName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 조회 필터 eventType이 감사 기록 쪽 EventName과 어긋나지 않는지 지킨다
 * — EventName이 추가됐는데 조회 enum에 빠지면 그 이벤트는 eventTypes로 거를 수 없게 된다.
 */
class AuditEventTypeTest {

    @Test
    void everyEventName_linksTo_exactlyOneQueryEventType() {
        List<EventName> linked = Arrays.stream(AuditEventType.values())
                .flatMap(type -> type.getSources().stream())
                .toList();

        assertThat(linked).containsExactlyInAnyOrder(EventName.values());
    }

    @Test
    void onlyLoginSuccessAndFailed_mergeInto_LOGIN_othersKeepEventName() {
        assertThat(AuditEventType.LOGIN.getSources())
                .containsExactlyInAnyOrder(EventName.LOGIN_SUCCESS, EventName.LOGIN_FAILED);

        Arrays.stream(AuditEventType.values())
                .filter(type -> type != AuditEventType.LOGIN)
                .forEach(type -> assertThat(type.getSources()).extracting(EventName::name).containsExactly(type.name()));
    }

    @Test
    void linkedEventNames_shareTheSameCategory() {
        Arrays.stream(AuditEventType.values())
                .forEach(type -> assertThat(type.getSources())
                        .extracting(EventName::getEventCategory)
                        .containsOnly(type.getEventCategory()));
    }
}

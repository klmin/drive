package com.thinkfree.drive.api.v1.admin.auditlog.usecase;

import com.thinkfree.drive.api.v1.admin.auditlog.request.AuditLogSearchRequest;
import com.thinkfree.drive.api.v1.admin.auditlog.response.AuditLogCsvExport;
import com.thinkfree.drive.global.exception.ApiException;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.global.time.DriveTime;
import com.thinkfree.governance.sdk.client.EventCsvExport;
import com.thinkfree.governance.sdk.client.EventListPage;
import com.thinkfree.governance.sdk.client.GovernanceClient;
import com.thinkfree.governance.sdk.model.EventQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogQueryUseCase CSV 내보내기 기간 검사")
class AuditLogQueryUseCaseTest {

    private static final int TENANT = 1;
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Mock
    private GovernanceClient governanceClient;

    @InjectMocks
    private AuditLogQueryUseCase auditLogQueryUseCase;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        configureZone(BERLIN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        configureZone(ZoneId.of(DriveTime.DEFAULT_ZONE_ID));
    }

    @Test
    @DisplayName("DST 종료가 낀 92일(2026-08-01~10-31, 92일+1시간)은 내보내고 조각마다 92일 이하로 나눈다")
    void exportsCalendar92DaysAcrossDstEnd() throws Exception {
        EventListPage probe = mock(EventListPage.class);
        given(probe.getTotalCount()).willReturn(10L);
        given(governanceClient.listEvents(any())).willReturn(probe);
        EventCsvExport chunk = mock(EventCsvExport.class);
        given(chunk.getContent()).willReturn("h\nrow\n".getBytes(StandardCharsets.UTF_8));
        given(chunk.getContentWithoutBom()).willReturn("h\nrow\n".getBytes(StandardCharsets.UTF_8));
        given(governanceClient.exportEventsInRange(any())).willReturn(chunk);

        AuditLogCsvExport export = auditLogQueryUseCase.export(request(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 31)));
        export.body().writeTo(new ByteArrayOutputStream());

        ArgumentCaptor<EventQuery> captor = ArgumentCaptor.forClass(EventQuery.class);
        then(governanceClient).should(atLeastOnce()).exportEventsInRange(captor.capture());
        List<EventQuery> queries = captor.getAllValues();
        assertThat(queries).hasSize(2);
        assertThat(queries).allSatisfy(q -> assertThat(Duration.between(
                Instant.parse(q.getOccurredFrom()), Instant.parse(q.getOccurredTo()))).isLessThanOrEqualTo(Duration.ofDays(92)));
        assertThat(queries.get(0).getOccurredTo()).isEqualTo("2026-10-31T23:00:00Z");
        assertThat(queries.get(1).getOccurredTo()).isEqualTo(queries.get(0).getOccurredFrom());
        assertThat(queries.get(1).getOccurredFrom()).isEqualTo("2026-07-31T22:00:00Z");
    }

    @Test
    @DisplayName("선택 날짜가 93일이면 거버넌스 호출 전에 거부한다")
    void rejects93Days() {
        assertThatThrownBy(() -> auditLogQueryUseCase.export(request(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 11, 1))))
                .isInstanceOf(ApiException.class);
        then(governanceClient).shouldHaveNoInteractions();
    }

    private static AuditLogSearchRequest request(LocalDate from, LocalDate to) {
        return new AuditLogSearchRequest(null, from, to, null, null, null, null);
    }

    private static void configureZone(ZoneId zone) {
        ReflectionTestUtils.invokeMethod(DriveTime.class, "configure", zone);
    }
}

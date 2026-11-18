package com.thinkfree.drive.api.v1.admin.auditlog.usecase;

import com.thinkfree.drive.api.page.ApiPage;
import com.thinkfree.drive.api.page.request.PagingRequest;
import com.thinkfree.drive.api.page.response.PaginationResponse;
import com.thinkfree.drive.api.v1.admin.auditlog.request.AuditLogSearchRequest;
import com.thinkfree.drive.api.v1.admin.auditlog.response.AuditLogCsvExport;
import com.thinkfree.drive.api.v1.admin.auditlog.response.AuditLogCsvWriter;
import com.thinkfree.drive.api.v1.admin.auditlog.response.AuditLogItemResponse;
import com.thinkfree.drive.api.v1.admin.auditlog.enums.AuditEventType;
import com.thinkfree.drive.api.v1.admin.auditlog.exception.AuditLogQueryErrorCode;
import com.thinkfree.drive.global.exception.ApiException;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.global.time.DriveTime;
import com.thinkfree.governance.sdk.client.EventCsvExport;
import com.thinkfree.governance.sdk.client.EventListPage;
import com.thinkfree.governance.sdk.client.GovernanceApiException;
import com.thinkfree.governance.sdk.client.GovernanceClient;
import com.thinkfree.governance.sdk.model.EventQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;

/**
 * 감사 로그(거버넌스 증적) 조회 유스케이스. 프론트 필터를 거버넌스 {@link EventQuery}로 변환해
 * SDK {@link GovernanceClient#listEvents}를 호출하고 Drive 응답 DTO로 변환한다(SDK가 HMAC 서명 처리).
 * <p>
 * {@code governance.audit.enabled=true}(= GovernanceClient 빈 존재)일 때만 등록된다.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "governance.audit", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AuditLogQueryUseCase {

    private static final int MAX_LIMIT = 200;
    private static final int MAX_RETRY_ATTEMPTS = 5;
    /** 선택 날짜 기준 내보내기 상한(시작일·종료일 포함). 달력 3개월은 89~92일이라 어떤 3개월 구간도 담긴다. */
    private static final int MAX_EXPORT_DAYS = 92;
    /** 거버넌스 export-range 한 번의 기간 상한(계약 §3.1). DST로 하루가 25시간인 범위는 조각을 나눠 맞춘다. */
    private static final Duration MAX_RANGE_PER_REQUEST = Duration.ofDays(92);
    private static final DateTimeFormatter FILE_NAME_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 동시에 허용할 내보내기 수. 테넌트가 여럿이고 테넌트마다 관리자가 여럿이라 넉넉히 잡는다.
     * 힙은 이 값이 아니라 <b>실제 진행 건수</b>로 조각을 나눠 지킨다({@link #chunkBudgetBytes}).
     */
    private static final int MAX_CONCURRENT_EXPORTS = 10;
    /** 조각 하나가 힙에 남기는 복사본 수(SDK transport→HttpResponse→EventCsvExport 3회 + 우리 getContent 1회). */
    private static final int COPY_FACTOR = 4;
    /** 내보내기에 허용할 힙 비율(1/10). {@code -Xmx}가 없어도 JVM이 아는 실제 최대 힙에서 계산된다. */
    private static final int HEAP_RATIO = 10;
    private static final long MIN_CHUNK_BYTES = 2L * 1024 * 1024;
    private static final long MAX_CHUNK_BYTES = 64L * 1024 * 1024;
    /** 계약 §7 실측(2026-09-14) 한 행 약 1,450바이트. 첫 조각 기간 추정에만 쓴다. */
    private static final int ESTIMATED_ROW_BYTES = 1450;
    /** 앞단 유휴 타임아웃(Apache ProxyTimeout 기본 60초)에 걸리지 않게 잡은 조각 하나의 목표 소요 시간. */
    private static final Duration TARGET_CHUNK_ELAPSED = Duration.ofSeconds(20);
    /** 로그가 몰린 구간에서도 이 아래로는 줄이지 않는다(요청 수가 급증한다). */
    private static final Duration MIN_CHUNK_PERIOD = Duration.ofHours(1);
    /** 다음 조각을 직전의 2배까지만 키운다 — 밀도가 급변해도 한 번에 크게 받지 않게. */
    private static final int CHUNK_GROWTH_LIMIT = 2;
    /** 추정에서 조금씩 물러서는 안전 계수. */
    private static final double CHUNK_SAFETY = 0.8;

    private final GovernanceClient governanceClient;
    private final Semaphore exportSlots = new Semaphore(MAX_CONCURRENT_EXPORTS);

    public PaginationResponse<AuditLogItemResponse> search(AuditLogSearchRequest request, PagingRequest pagingRequest) {
        EventQuery query = toQuery(request, pagingRequest);
        try {
            EventListPage page = governanceClient.listEvents(query);
            List<AuditLogItemResponse> items = page.getItems().stream().map(AuditLogItemResponse::from).toList();
            return PaginationResponse.of(items, ApiPage.of(resolveTotalCount(page), pagingRequest.pi(), pagingRequest.ps()));
        } catch (IOException | GovernanceApiException e) {
            log.error("[AuditLog] governance listEvents failed", e);
            // 원인은 위 로그에 남기고, 호출자(웹·MCP)에는 에러 코드로 알린다 — MCP는 McpToolResult가 {error, errorCode}로 변환
            throw new ApiRuntimeException(AuditLogQueryErrorCode.QUERY_FAILED);
        }
    }

    /**
     * 필터 조건에 해당하는 감사 로그를 기간 전체 CSV로 내려받는다(거버넌스 원본 44열 그대로).
     * 거버넌스 GET /v1/events:export-range는 기간 안의 모든 행을 주는 대신 기간 둘이 필수이고
     * 간격이 92일을 넘으면 400이다(계약 §3.1). 화면이 이미 3개월로 제한하지만, 기간을 비운 요청은 여기서
     * 최근 92일로 채우고 선택 날짜가 92일을 넘는 요청은 거버넌스까지 가기 전에 400으로 돌려준다.
     * <p>
     * 응답 전체를 힙에 들지 않도록 기간을 조각내 받아 받는 즉시 흘려보낸다({@link #writeCsv} 참고).
     * 조회 조건(테넌트 포함)은 ThreadLocal을 읽으므로 본문에 넘기기 전에 여기서 미리 만든다.
     */
    public AuditLogCsvExport export(AuditLogSearchRequest request) {
        Instant occurredTo = request.to() != null
                ? DriveTime.endExclusive(request.to())
                : Instant.now();
        Instant occurredFrom = request.from() != null
                ? DriveTime.startOfDay(request.from())
                : occurredTo.minus(MAX_RANGE_PER_REQUEST);
        if (!occurredFrom.isBefore(occurredTo)) {
            throw new ApiException("조회 종료일이 시작일보다 빠릅니다.");
        }
        // 시각 간격이 아니라 날짜 수로 판정한다 — DST 종료일이 끼면 같은 92일이 92일+1시간이 된다.
        if (request.from() != null) {
            LocalDate toDate = request.to() != null ? request.to() : DriveTime.toLocalDate(occurredTo);
            if (ChronoUnit.DAYS.between(request.from(), toDate) >= MAX_EXPORT_DAYS) {
                throw new ApiException("감사 로그 내보내기 기간은 최대 " + MAX_EXPORT_DAYS + "일입니다.");
            }
        }

        // 페이지 파라미터(limit/offset/cursor)는 이 경로에서 400이라 넣지 않는다(SDK가 걸러 보내지만 애초에 안 넣는다).
        EventQuery.Builder exportFilter = toFilterBuilder(request);
        EventQuery probeQuery = toFilterBuilder(request)
                .setOccurredFrom(occurredFrom.toString())
                .setOccurredTo(occurredTo.toString())
                .setLimit(1)
                .setOffset(0)
                .build();

        // 조각 크기가 동시 실행 수를 전제로 계산되므로 슬롯을 못 잡으면 받지 않는다(거버넌스 §6.1과 같은 처방).
        if (!exportSlots.tryAcquire()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "다른 내보내기가 진행 중입니다. 잠시 뒤 다시 시도해 주세요.");
        }
        AuditLogCsvWriter body = output -> {
            try {
                writeCsv(exportFilter, probeQuery, occurredFrom, occurredTo, output);
            } finally {
                exportSlots.release();
            }
        };
        return new AuditLogCsvExport(buildFileName(occurredFrom, occurredTo), body);
    }

    /**
     * 기간을 조각내 최신 구간부터 받아 이어 쓴다. 조각 하나만 힙에 머무르므로 행이 늘어도 사용량이 늘지 않는다.
     * 조각 크기는 받아 본 실제 바이트·소요 시간으로 매번 다시 잡는다(사전 실측값이 필요 없다).
     * <p>
     * 정렬이 occurredAt 내림차순 고정이라 <b>최신 조각부터</b> 써야 파일 전체도 같은 순서가 된다.
     * 조각 경계는 반열린 구간(occurredFrom 이상, occurredTo 미만)이라 앞 조각의 시작을 다음 조각의 끝으로
     * 그대로 이으면 중복도 누락도 없다.
     */
    private void writeCsv(EventQuery.Builder filter, EventQuery probeQuery,
                          Instant from, Instant to, OutputStream output) throws IOException {
        long budgetBytes = chunkBudgetBytes(runningExports());
        Duration chunkPeriod = initialChunkPeriod(probeQuery, Duration.between(from, to), budgetBytes);
        Instant chunkTo = to;
        boolean first = true;
        int chunks = 0;
        long totalBytes = 0;
        long startedAt = System.nanoTime();

        while (chunkTo.isAfter(from)) {
            Instant chunkFrom = chunkTo.minus(chunkPeriod);
            if (chunkFrom.isBefore(from)) {
                chunkFrom = from;
            }
            long chunkStartedAt = System.nanoTime();
            EventCsvExport chunk = fetchChunk(filter, chunkFrom, chunkTo);
            int bytes = writeChunk(chunk, first, output);
            output.flush();
            Duration elapsed = Duration.ofNanos(System.nanoTime() - chunkStartedAt);

            log.debug("[AuditLog] CSV 조각 {}~{} bytes={} elapsed={}ms", chunkFrom, chunkTo, bytes, elapsed.toMillis());
            chunkPeriod = nextChunkPeriod(chunkPeriod, Duration.between(chunkFrom, chunkTo), bytes, elapsed, budgetBytes);
            chunkTo = chunkFrom;
            first = false;
            chunks++;
            totalBytes += bytes;
        }

        log.info("[AuditLog] CSV 내보내기 완료 range={}~{} chunks={} bytes={} elapsed={}ms budget={}B",
                from, to, chunks, totalBytes, Duration.ofNanos(System.nanoTime() - startedAt).toMillis(), budgetBytes);
    }

    /**
     * 조각 하나에 허용할 바이트. {@code -Xmx}를 안 줬어도 JVM이 아는 실제 최대 힙에서 계산하므로
     * 설치 환경이 달라도 따라간다(호스트 메모리가 아주 클 때를 대비해 상·하한을 둔다).
     * <p>
     * 허용 상한이 아니라 <b>지금 돌고 있는 건수</b>로 나눈다 — 혼자 받으면 큰 조각으로 빠르게 끝내고,
     * 여럿이 몰릴 때만 조각이 작아진다. 시작 시점 한 번만 잰다(진행 중 늘어난 요청은 자기 몫을 다시 나눠 가진다).
     */
    private static long chunkBudgetBytes(int running) {
        long budget = Runtime.getRuntime().maxMemory() / HEAP_RATIO / COPY_FACTOR / Math.max(1, running);
        return Math.min(MAX_CHUNK_BYTES, Math.max(MIN_CHUNK_BYTES, budget));
    }

    /** 자기 자신을 포함한 진행 중 내보내기 수. */
    private int runningExports() {
        return MAX_CONCURRENT_EXPORTS - exportSlots.availablePermits();
    }

    /** 전체 건수로 첫 조각 기간을 추정한다. 건수를 못 구하면 하루로 시작해 실측으로 키운다. */
    private Duration initialChunkPeriod(EventQuery probeQuery, Duration total, long budgetBytes) {
        long rows = probeTotalCount(probeQuery);
        if (rows < 0) {
            return Duration.ofDays(1);
        }
        Duration max = total.compareTo(MAX_RANGE_PER_REQUEST) > 0 ? MAX_RANGE_PER_REQUEST : total;
        long estimatedBytes = rows * ESTIMATED_ROW_BYTES;
        if (estimatedBytes <= budgetBytes) {
            return max;
        }
        long seconds = total.getSeconds() * budgetBytes / estimatedBytes;
        return clampChunkPeriod(Duration.ofSeconds(seconds), max);
    }

    /** 이번 조각의 실제 밀도·속도로 다음 조각 기간을 다시 잡는다. 빈 구간이면 상한까지 키운다. */
    private static Duration nextChunkPeriod(Duration current, Duration actual, long bytes, Duration elapsed, long budgetBytes) {
        double seconds = Math.max(1, actual.getSeconds());
        double byteBased = bytes > 0 ? seconds * budgetBytes / bytes : Double.MAX_VALUE;
        double timeBased = elapsed.toMillis() > 0
                ? seconds * TARGET_CHUNK_ELAPSED.toMillis() / elapsed.toMillis()
                : Double.MAX_VALUE;
        double next = Math.min(byteBased, timeBased) * CHUNK_SAFETY;
        long grown = current.multipliedBy(CHUNK_GROWTH_LIMIT).getSeconds();
        return clampChunkPeriod(Duration.ofSeconds((long) Math.min(next, grown)), MAX_RANGE_PER_REQUEST);
    }

    private static Duration clampChunkPeriod(Duration period, Duration max) {
        if (period.compareTo(MIN_CHUNK_PERIOD) < 0) {
            return MIN_CHUNK_PERIOD;
        }
        return period.compareTo(max) > 0 ? max : period;
    }

    /** 절단 안내가 아니라 첫 조각 기간 추정에만 쓰므로, 실패해도 내보내기를 막지 않고 -1로 알린다. */
    private long probeTotalCount(EventQuery probeQuery) {
        try {
            EventListPage page = callWithRetry(() -> governanceClient.listEvents(probeQuery));
            return page.getTotalCount() != null ? page.getTotalCount() : -1L;
        } catch (IOException | GovernanceApiException e) {
            log.warn("[AuditLog] CSV 내보내기 건수 조회 실패 - 조각을 하루로 시작한다", e);
            return -1L;
        }
    }

    /** 조각 하나를 받는다. 이미 응답이 시작된 뒤라 실패는 IOException으로 올려 스트림을 끊는다(계약 §5.2). */
    private EventCsvExport fetchChunk(EventQuery.Builder filter, Instant from, Instant to) throws IOException {
        EventQuery query = filter
                .setOccurredFrom(from.toString())
                .setOccurredTo(to.toString())
                .build();
        try {
            return callWithRetry(() -> governanceClient.exportEventsInRange(query));
        } catch (GovernanceApiException e) {
            log.error("[AuditLog] governance exportEventsInRange failed range={}~{}", from, to, e);
            throw new IOException("감사 로그 CSV 내보내기에 실패했습니다.", e);
        }
    }

    /**
     * 첫 조각은 BOM과 머리 줄까지, 이후 조각은 데이터 행만 붙인다(계약 §8). 조각 안에서 eventId가 겹치는 행은 뺀다.
     * 문자열로 바꾸면 BOM이 사라지거나 두 번 들어가므로 바이트로 다룬다.
     *
     * @return 조각의 바이트 수(다음 조각 기간 계산에 쓴다)
     */
    private static int writeChunk(EventCsvExport chunk, boolean first, OutputStream output) throws IOException {
        byte[] content = first ? chunk.getContent() : chunk.getContentWithoutBom();
        int duplicates = AuditLogCsvRows.write(content, first, output);
        if (duplicates < 0) {
            log.warn("[AuditLog] CSV 머리 줄에 eventId 열이 없어 중복을 거르지 않았다");
        } else if (duplicates > 0) {
            log.info("[AuditLog] CSV 조각에서 중복 행 {}건을 뺐다", duplicates);
        }
        return content.length;
    }

    /**
     * 파일 이름은 Drive가 만든다. 조각마다 거버넌스가 준 이름은 그 조각의 기간이라 전체 파일 이름으로 쓸 수 없다.
     * 모양은 거버넌스 규칙(계약 §3.3)과 같게 맞춘다.
     */
    private static String buildFileName(Instant from, Instant to) {
        DateTimeFormatter format = FILE_NAME_DATE.withZone(DriveTime.zone());
        return "audit-events_%s-%s.csv".formatted(format.format(from), format.format(to));
    }

    private EventQuery toQuery(AuditLogSearchRequest req, PagingRequest paging) {
        EventQuery.Builder q = toFilterBuilder(req);
        // 페이지 번호 방식이라 offset/limit만 쓴다. cursor는 offset과 함께 쓰면 서버가 400을 내므로 사용하지 않는다
        // (응답의 nextCursor도 쓰지 않고 버린다).
        int limit = resolveLimit(paging.ps());
        q.setLimit(limit);
        q.setOffset((paging.pi() - 1) * limit);
        return q.build();
    }

    /** 필터 → 거버넌스 질의 공통 변환(기간·페이지는 호출부가 마저 채운다). */
    private EventQuery.Builder toFilterBuilder(AuditLogSearchRequest req) {
        EventQuery.Builder q = EventQuery.newBuilder();

        // 사용자 검색: 프론트에서 유저를 선택해 넘긴 이메일로 actorEmail 정확일치(행위자 기준)
        if (hasText(req.actorEmail())) {
            q.setActorEmail(req.actorEmail());
        }
        if (req.eventCategory() != null) {
            q.setEventCategory(req.eventCategory().name());
        }
        if (req.eventTypes() != null) {
            req.eventTypes().stream().filter(Objects::nonNull).map(AuditEventType::name).forEach(q::addEventType);
        }
        if (req.channel() != null) {
            q.setChannel(req.channel().name());
        }
        if (hasText(req.outcomeStatus())) {
            q.setOutcomeStatus(req.outcomeStatus());
        }
        // 기간: occurredFrom 이상, occurredTo 미만 → 종료일 포함되게 다음날 00:00(drive.time-zone)로 변환
        if (req.from() != null) {
            q.setOccurredFrom(DriveTime.startOfDay(req.from()).toString());
        }
        if (req.to() != null) {
            q.setOccurredTo(DriveTime.endExclusive(req.to()).toString());
        }

        // 테넌트 격리: 요청 관리자의 tenantSeq를 scope.id로 넣어 해당 Drive 테넌트 이벤트만 조회한다.
        // (인입 시 scope.id에 tenantSeq를 String으로 실었으므로 동일 형식으로 필터. Drive가 서버에서 항상 주입하므로
        //  프론트가 우회할 수 없다 — 거버넌스 격리는 인증 키=거버넌스 테넌트 단위이고 scope는 그 안의 업무 필터다.)
        q.setScopeId(String.valueOf(TenantContext.getOrThrow()));
        return q;
    }

    /** 429(EXPORT_BUSY) 등 재시도 가능한 응답이면 Retry-After만큼 대기 후 재시도한다(거버넌스팀 계약 §5.1). */
    private <T> T callWithRetry(GovernanceCall<T> call) throws IOException, GovernanceApiException {
        int attempt = 0;
        while (true) {
            try {
                return call.execute();
            } catch (GovernanceApiException e) {
                attempt++;
                if (!e.isRetryable() || e.getRetryAfterSeconds() == null || attempt >= MAX_RETRY_ATTEMPTS) {
                    throw e;
                }
                log.warn("[AuditLog] governance {} 재시도 attempt={} retryAfter={}s", e.getOperation(), attempt, e.getRetryAfterSeconds());
                sleep(e.getRetryAfterSeconds());
            }
        }
    }

    @FunctionalInterface
    private interface GovernanceCall<T> {
        T execute() throws IOException, GovernanceApiException;
    }

    private static void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("감사 로그 CSV 내보내기가 중단되었습니다.", e);
        }
    }

    /** SDK가 1~200만 허용해 상한을 맞춘다. */
    private int resolveLimit(int pageSize) {
        return Math.max(1, Math.min(pageSize, MAX_LIMIT));
    }

    /** totalCount는 오프셋 방식이면 서버가 채워주지만, 생략될 수 있어 방어한다. */
    private long resolveTotalCount(EventListPage page) {
        if (page.getTotalCount() == null) {
            log.warn("[AuditLog] governance totalCount 누락 - 0으로 응답한다");
            return 0L;
        }
        return page.getTotalCount();
    }

    private static boolean hasText(String v) {
        return v != null && !v.trim().isEmpty();
    }
}

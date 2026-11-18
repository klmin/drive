package com.thinkfree.drive.auditlog;

import com.thinkfree.governance.sdk.auth.GovernanceCredentials;
import com.thinkfree.governance.sdk.client.GovernanceClient;
import com.thinkfree.governance.sdk.client.GovernanceClientSettings;
import com.thinkfree.governance.sdk.client.IngestReceipt;
import com.thinkfree.governance.sdk.client.IngestResult;
import com.thinkfree.governance.sdk.http.JdkHttpTransport;
import com.thinkfree.governance.sdk.json.jackson.JacksonJsonCodec;
import com.thinkfree.governance.sdk.model.Actor;
import com.thinkfree.governance.sdk.model.Context;
import com.thinkfree.governance.sdk.model.EventIn;
import com.thinkfree.governance.sdk.model.Change;
import com.thinkfree.governance.sdk.model.EventObject;
import com.thinkfree.governance.sdk.model.Origin;
import com.thinkfree.governance.sdk.model.Outcome;
import com.thinkfree.governance.sdk.model.Scope;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * 로컬 수동 스모크 테스트 — Drive 앱/공유 흐름 없이 SDK로 샘플 이벤트 1건을 로컬 Governance 서버로 직접 전송하고
 * receipt가 STORED가 될 때까지 폴링해서 출력한다. IntelliJ에서 main 우클릭 → Run.
 * <p>
 * ⚠️ 커밋 금지: 로컬 자격증명이 박혀 있는 일회성 검증용. (env로 덮어쓸 수 있음)
 */
public final class GovernanceSdkManualSmokeTest {

    public static void main(String[] args) throws Exception {
        String endpoint = env("GOVERNANCE_SDK_ENDPOINT", "http://127.0.0.1:8081/v1");
        String tenantId = env("GOVERNANCE_SDK_TENANT_ID", "019f8dd7-d836-7491-9eda-4f57fbf3f55b");
        String keyId = env("GOVERNANCE_SDK_KEY_ID", "019f8dd7-d84e-7307-a50a-c1a0d14f50fe");
        String base64Secret = env("GOVERNANCE_SDK_BASE64_SECRET", "Kz9W4AoloZUwBi/El+G4vbSxZYPSvecye+OqKcdMoA8=");

        GovernanceClientSettings settings = GovernanceClientSettings.newBuilder()
                .setEndpoint(URI.create(endpoint))
                .setCredentials(GovernanceCredentials.newBuilder()
                        .setTenantId(tenantId)
                        .setKeyId(keyId)
                        .setBase64Secret(base64Secret)
                        .build())
                .setHttpTransport(JdkHttpTransport.newBuilder()
                        .setConnectTimeout(Duration.ofSeconds(5))
                        .setReadTimeout(Duration.ofSeconds(15))
                        .build())
                .setJsonCodec(new JacksonJsonCodec())
                .build();

        System.out.println("## start ## endpoint=" + endpoint);

        EventIn event = sampleFileRename();

        try (GovernanceClient client = GovernanceClient.create(settings)) {
            IngestResult result = client.ingestEvent(event);
            System.out.println("accepted: status=" + result.getStatus()
                    + " eventId=" + result.getEventId()
                    + " receiptId=" + result.getReceiptId());

            Instant deadline = Instant.now().plusSeconds(15);
            while (Instant.now().isBefore(deadline)) {
                IngestReceipt receipt = client.getIngestReceipt(result.getReceiptId());
                System.out.println("  receipt status=" + receipt.getStatus());
                if ("STORED".equals(receipt.getStatus())) {
                    System.out.println("STORED ✅ storedAt=" + receipt.getStoredAt());
                    break;
                }
                if ("FAILED".equals(receipt.getStatus())) {
                    throw new IllegalStateException("저장 실패: " + receipt.getErrorCode());
                }
                Thread.sleep(300L);
            }
        }
    }

    /** 연동 최종본 §3 예시(FILE_RENAME) — before/after는 change, 상위 분류는 최상위 sourceEventCategory. */
    private static EventIn sampleFileRename() {
        Actor actor = Actor.newBuilder()
                .setType("HUMAN")
                .setId("4821")
                .setName("김민지")
                .setEmail("minji.kim@acme.example")
                .build();

        return EventIn.newBuilder()
                .setEventType("FILE_RENAME")
                .setSourceEventType("FILE_RENAME")
                .setSourceEventCategory("FILE_FOLDER")
                .setOccurredAt("2026-07-09T11:15:30.123Z")
                .setOrigin(Origin.newBuilder()
                        .setSystem("TFD").setComponent("SERVER").setInstanceId("tfd-local-smoke").build())
                .setActor(actor)
                .setScope(Scope.newBuilder().setType("TENANT").setId("27").setName("ACME").build())
                .setObject(EventObject.newBuilder()
                        .setType("FILE").setId("942").setName("기술명세서_v2.docx").setPath("/개발팀").setStorage("TFD").build())
                .setOutcome(Outcome.newBuilder().setStatus("SUCCESS").build())
                .setContext(Context.newBuilder().setChannel("WEB").setClientIp("203.0.113.24").build())
                .setChange(Change.newBuilder()
                        .setBefore("기술명세서_초안.docx").setAfter("기술명세서_v2.docx").build())
                .build();
    }

    private static String env(String key, String defaultValue) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }
}

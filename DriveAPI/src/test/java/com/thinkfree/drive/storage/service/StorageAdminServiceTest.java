package com.thinkfree.drive.storage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.external.ceph.dashboard.client.CephDashboardClient;
import com.thinkfree.drive.external.ceph.dashboard.response.CephCapacityResponse;
import com.thinkfree.drive.external.ceph.dashboard.service.CephAdminService;
import com.thinkfree.drive.storage.enums.CapacityMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Ceph 저장소 용량 조회({@link CephAdminService}) — 실제 Ceph 대시보드 API 대신 클라이언트를 Mock 으로 둔다.
 */
class StorageAdminServiceTest {

    @Test
    void getStorageCapacity() {
        CephDashboardClient client = mock(CephDashboardClient.class);
        when(client.capacity()).thenReturn(new CephCapacityResponse(300L, 1000L, 700L));

        var result = new CephAdminService(client).getStorageCapacity();

        assertThat(result.capacityMode()).isEqualTo(CapacityMode.LIMITED);
        assertThat(result.totalAvailBytes()).isEqualTo(300L);
        assertThat(result.totalBytes()).isEqualTo(1000L);
        assertThat(result.totalUsedBytes()).isEqualTo(700L);
    }

    /** 대시보드 응답(GET /health/get_cluster_capacity)은 snake_case — 응답 레코드가 그대로 읽는지 */
    @Test
    void capacityResponseReadsSnakeCase() throws Exception {
        String body = "{\"total_avail_bytes\":300,\"total_bytes\":1000,\"total_used_raw_bytes\":700,\"total_objects\":5}";

        var response = new ObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue(body, CephCapacityResponse.class);

        assertThat(response).isEqualTo(new CephCapacityResponse(300L, 1000L, 700L));
    }
}

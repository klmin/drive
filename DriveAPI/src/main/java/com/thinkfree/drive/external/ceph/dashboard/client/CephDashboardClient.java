package com.thinkfree.drive.external.ceph.dashboard.client;

import com.thinkfree.drive.external.ceph.dashboard.client.base.CephDashboardBaseExchange;
import com.thinkfree.drive.external.ceph.dashboard.response.CephCapacityResponse;
import org.springframework.web.service.annotation.GetExchange;


public interface CephDashboardClient extends CephDashboardBaseExchange {
    
    @GetExchange("/health/get_cluster_capacity")
    CephCapacityResponse capacity();
    
}

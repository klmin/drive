package com.thinkfree.drive.external.ceph.dashboard.client;

import com.thinkfree.drive.external.ceph.dashboard.client.base.CephDashboardBaseExchange;
import com.thinkfree.drive.external.ceph.dashboard.request.CephAuthRequest;
import com.thinkfree.drive.external.ceph.dashboard.response.CephAuthResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;


public interface CephDashboardAuthClient extends CephDashboardBaseExchange {
    @PostExchange("/auth")
    CephAuthResponse auth(@RequestBody CephAuthRequest request);
}

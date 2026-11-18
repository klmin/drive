package com.thinkfree.drive.external.ceph.dashboard.client.base;

import com.thinkfree.drive.constants.DriveConstants;
import org.springframework.http.MediaType;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(
    url = "/api",
    accept = DriveConstants.CEPH_API_MEDIA_TYPE,
    contentType = MediaType.APPLICATION_JSON_VALUE
)
public interface CephDashboardBaseExchange {
}

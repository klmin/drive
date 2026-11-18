package com.thinkfree.drive.external.ceph.dashboard.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CephCapacityResponse
(
    long totalAvailBytes,
    long totalBytes,
    long totalUsedRawBytes
)
{
}

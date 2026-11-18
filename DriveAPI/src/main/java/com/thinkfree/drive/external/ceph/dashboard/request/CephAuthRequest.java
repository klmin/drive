package com.thinkfree.drive.external.ceph.dashboard.request;

public record CephAuthRequest
(
    String username,
    String password
)
{
}

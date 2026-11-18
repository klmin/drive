package com.thinkfree.drive.external.ceph.dashboard.claims;

public record CephJwtClaims(
        Long exp,
        Long iat,
        String iss,
        String jti,
        String username
) {}

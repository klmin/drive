package com.thinkfree.drive.auth.token.blacklist.query;

import java.time.Instant;

public record BlacklistTokenExpireQuery
(
    Instant expirationThreshold
)
{
}

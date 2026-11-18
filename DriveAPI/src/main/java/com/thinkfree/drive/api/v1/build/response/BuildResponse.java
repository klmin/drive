package com.thinkfree.drive.api.v1.build.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record BuildResponse
(
    String applicationName,
    String buildFileName,
    String version,
    String profile,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    LocalDateTime buildTime
)
{
}

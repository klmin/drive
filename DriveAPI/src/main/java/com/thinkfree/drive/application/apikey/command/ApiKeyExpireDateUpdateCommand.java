package com.thinkfree.drive.application.apikey.command;

import lombok.Builder;

import java.time.Instant;

@Builder
public record ApiKeyExpireDateUpdateCommand
(
    Integer apiKeySeq,
    Instant expireDate
)
{
}

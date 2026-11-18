package com.thinkfree.drive.httpclient;

public record HttpClientProperties
(
    int maxPool,
    int maxPerRoute,
    long validateAfterInactivity,
    long idleConnectionTimeout,
    long connectionTimeout,
    long socketTimeout,
    long requestTimeout,
    long responseTimeout,
    int retryCount,
    long backoff
)
{
    public static HttpClientProperties defaults() {
        return new HttpClientProperties(
            200,
            50,
            5000,
            30000,
            3000,
            10000,
            5000,
            10000,
            2,
            1000
        );
    }
}

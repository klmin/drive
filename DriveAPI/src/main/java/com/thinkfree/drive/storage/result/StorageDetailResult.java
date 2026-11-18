package com.thinkfree.drive.storage.result;

import com.thinkfree.drive.storage.enums.StorageType;

public record StorageDetailResult
(
    StorageType storageType,
    String accessKey,
    String secretKey,
    String bucketName,
    String region,
    String endPoint,
    Boolean status
)
{
}

package com.thinkfree.drive.api.v1.admin.systemconfig.response;

import com.thinkfree.drive.storage.enums.StorageType;

public record StorageResponse(
        StorageType storageType,
        String accessKey,
        String secretKey,
        String bucketName,
        String region,
        String endPoint,
        Boolean status
) {
}

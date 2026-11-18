package com.thinkfree.drive.storage.result;

import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.storage.enums.CapacityMode;

public record StorageCapacityResult
(
    CapacityMode capacityMode,
    long totalAvailBytes,
    long totalBytes,
    long totalUsedBytes
)
{
    public static StorageCapacityResult unlimited() {
        return new StorageCapacityResult(CapacityMode.UNLIMITED, DriveConstants.UNLIMITED_QUOTA, DriveConstants.UNLIMITED_QUOTA, DriveConstants.UNLIMITED_QUOTA);
    }
}

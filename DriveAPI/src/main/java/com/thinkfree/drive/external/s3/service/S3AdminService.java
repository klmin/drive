package com.thinkfree.drive.external.s3.service;

import com.thinkfree.drive.storage.result.StorageCapacityResult;
import com.thinkfree.drive.storage.service.StorageAdminService;
import lombok.RequiredArgsConstructor;
import com.thinkfree.drive.storage.condition.ConditionalOnStorageType;
import com.thinkfree.drive.storage.enums.StorageType;
import org.springframework.stereotype.Service;

@ConditionalOnStorageType(StorageType.S3)
@Service
@RequiredArgsConstructor
public class S3AdminService implements StorageAdminService {
    @Override
    public StorageCapacityResult getStorageCapacity() {
        return StorageCapacityResult.unlimited();
    }
}

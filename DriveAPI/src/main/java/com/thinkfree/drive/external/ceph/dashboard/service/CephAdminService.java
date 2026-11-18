package com.thinkfree.drive.external.ceph.dashboard.service;

import com.thinkfree.drive.external.ceph.dashboard.client.CephDashboardClient;
import com.thinkfree.drive.storage.enums.CapacityMode;
import com.thinkfree.drive.storage.result.StorageCapacityResult;
import com.thinkfree.drive.storage.service.StorageAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.thinkfree.drive.storage.condition.ConditionalOnStorageType;
import com.thinkfree.drive.storage.enums.StorageType;
import org.springframework.stereotype.Service;

@ConditionalOnStorageType(StorageType.CEPH)
@Service
@RequiredArgsConstructor
@Slf4j
public class CephAdminService implements StorageAdminService {
    
    private final CephDashboardClient cephDashboardClient;
    
    @Override
    public StorageCapacityResult getStorageCapacity() {
        var response = cephDashboardClient.capacity();
        return new StorageCapacityResult(CapacityMode.LIMITED, response.totalAvailBytes(), response.totalBytes(), response.totalUsedRawBytes());
    }
    
}

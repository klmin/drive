package com.thinkfree.drive.external.rustfs.service;

import com.thinkfree.drive.storage.result.StorageCapacityResult;
import com.thinkfree.drive.storage.service.StorageAdminService;
import lombok.RequiredArgsConstructor;
import com.thinkfree.drive.storage.condition.ConditionalOnStorageType;
import com.thinkfree.drive.storage.enums.StorageType;
import org.springframework.stereotype.Service;

/**
 * RustFS 용량 조회. 접속은 Ceph와 같은 S3 호환 경로를 쓰지만 Ceph 대시보드가 없으므로
 * 현재는 무제한으로 보고한다. RustFS admin API 연동 시 이 구현만 교체하면 된다.
 */
@ConditionalOnStorageType(StorageType.RUSTFS)
@Service
@RequiredArgsConstructor
public class RustFsAdminService implements StorageAdminService {

    @Override
    public StorageCapacityResult getStorageCapacity() {
        return StorageCapacityResult.unlimited();
    }
}

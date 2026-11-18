package com.thinkfree.drive.metrics.user.service;

import com.thinkfree.drive.metrics.user.result.UserStorageUsageMetricsResult;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.resource.version.service.ResourceVersionFindService;
import com.thinkfree.drive.user.service.UserFindService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserMetricsService {
    
    private final UserFindService userFindService;
    private final ResourceFindService resourceFindService;
    private final ResourceVersionFindService resourceVersionFindService;
    
    @Transactional(readOnly = true)
    public UserStorageUsageMetricsResult getStorageUsageMetrics(int userSeq) {
        
        var user = userFindService.findDetailByUserSeq(userSeq);
        var usedQuotaByte = user.getUseQuotaByte();
        
        var resourceUsageSumData = resourceFindService.findUsageSummaryByUserSeq(userSeq);
        var resourceVersionUsageSumData = resourceVersionFindService.findUsageSummaryByUserSeq(userSeq);
        var calcUsedQuotaBytes = resourceUsageSumData.totalBytes() + resourceVersionUsageSumData.totalBytes();
        var calcTrashBytes = resourceUsageSumData.trashBytes() + resourceVersionUsageSumData.trashBytes();
        
        if(usedQuotaByte != calcUsedQuotaBytes){
            long diff = usedQuotaByte - calcUsedQuotaBytes;
            log.warn("[StorageUsage MISMATCH] userSeq : {}, usedQuotaByte : {}, calcUsedQuotaBytes : {}, diff : {}", userSeq, usedQuotaByte, calcUsedQuotaBytes, diff);
        }
        
        return new UserStorageUsageMetricsResult(user.getTotalQuotaByte(), usedQuotaByte, resourceUsageSumData.liveBytes(), resourceVersionUsageSumData.liveBytes(), calcTrashBytes);
    }
}

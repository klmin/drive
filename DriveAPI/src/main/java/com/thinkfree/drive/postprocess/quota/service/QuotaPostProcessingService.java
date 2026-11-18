package com.thinkfree.drive.postprocess.quota.service;


import com.thinkfree.drive.postprocess.quota.dto.QuotaStateChangeNotifyDto;
import com.thinkfree.drive.postprocess.quota.dto.QuotaStateTransferDto;

public interface QuotaPostProcessingService {
    void processQuotaIncreaseAndNotify(QuotaStateChangeNotifyDto dto);
    void processQuotaDecreaseAndNotify(QuotaStateChangeNotifyDto dto);
    void processQuotaMoveAndNotify(QuotaStateTransferDto dto);
}

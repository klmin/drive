package com.thinkfree.drive.postprocess.upload.service;

import com.thinkfree.drive.postprocess.upload.dto.SharingUploadNotifyDto;

public interface UploadPostProcessingService {
    void processSharedUploadAndNotify(SharingUploadNotifyDto dto);
}


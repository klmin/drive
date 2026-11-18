package com.thinkfree.drive.postprocess.share.service;

import com.thinkfree.drive.postprocess.share.service.dto.*;

public interface SharePostProcessingService {
    void processSharedAndNotify(ShareNotifyDto dto);
    void processUnsharedAndNotify(UnshareNotifyDto dto);
    void processUnsharedByMoveAndNotify(UnshareLookupNotifyDto dto);
    void processDeletedUnsharedAndNotify(DeletedUnshareNotifyDto dto);
    void processShareTargetChangedAndNotify(ShareTargetChangeNotifyDto dto);
}

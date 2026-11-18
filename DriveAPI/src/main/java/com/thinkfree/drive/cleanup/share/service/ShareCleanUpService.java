package com.thinkfree.drive.cleanup.share.service;

import com.thinkfree.drive.cleanup.share.dto.ShareCleanUpDto;
import com.thinkfree.drive.cleanup.share.dto.ShareExternalCleanUpDto;

public interface ShareCleanUpService {

    void deleteShareCascade(ShareCleanUpDto dto);

    /** 전체공개 -> 조직링크 축소: 링크 접근을 잃는 외부 테넌트 유저(잔존 target 제외)의 메타 정리. */
    void deleteOtherTenantShareCascade(ShareExternalCleanUpDto dto);
}

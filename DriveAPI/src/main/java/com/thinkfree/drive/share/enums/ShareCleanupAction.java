package com.thinkfree.drive.share.enums;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum ShareCleanupAction {
    
    NONE(false, false, false, false, false, false),
    DELETE_RELATED_DATA(false, false, false, true, true, true),
    // 직접 공유 해제(이동) — 유형 무관(링크/제한됨) share + target + 메타 정리 + 알림. (구 DELETE_LINK_SHARE 흡수)
    DELETE_SPECIFIC_SHARE(true, true, true, true, true, true)
    ;
    
    private final boolean notifyUnshare;
    private final boolean deleteShare;
    private final boolean deleteShareTargets;
    private final boolean deleteRecents;
    private final boolean deleteStarred;
    private final boolean deletePinnedFolder;
}

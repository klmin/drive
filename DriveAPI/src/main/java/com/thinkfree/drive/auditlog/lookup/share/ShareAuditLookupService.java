package com.thinkfree.drive.auditlog.lookup.share;

import com.thinkfree.drive.share.data.ShareWithTargetsData;

/**
 * 감사 로그 기록에 필요하지만 기존 usecase 흐름에서 바로 얻을 수 없는 값(resourcePath, 공유 현재 상태)을
 * 조회하는 공용 창구. 새 쿼리를 추가하지 않고 기존 ResourceFindService/ShareFindService 호출만 감싼다.
 * findCurrentShareState는 SHARE_PERMISSION_CHANGE 감사 로그의 "변경 전" 값(링크 권한/대상자별 권한)을 얻기 위해
 * updateShareWithTargets 호출 전에 사용해야 한다.
 */
public interface ShareAuditLookupService {
    String findResourcePath(Long resourceSeq, Integer userSeq);
    ShareWithTargetsData findCurrentShareState(Long shareSeq, Integer userSeq);
}

package com.thinkfree.drive.auditlog.lookup.share.impl;

import com.thinkfree.drive.auditlog.lookup.share.ShareAuditLookupService;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.resource.query.ResourceFindQuery;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.share.data.ShareWithTargetsData;
import com.thinkfree.drive.share.service.ShareFindService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultShareAuditLookupService implements ShareAuditLookupService {

    private final ResourceFindService resourceFindService;
    private final ShareFindService shareFindService;
    private final ResourceAuditService resourceAuditService;

    /**
     * 경로 조회에 부모 폴더 seq와 소유자가 필요해 리소스를 먼저 조회한다.
     * 리소스를 못 찾아도 이벤트가 일어난 사실은 남긴다.(이벤트 성공 + 감사로그 기록 실패한 경우)
     * <p>FILE_FOLDER 이벤트와 같은 표기를 쓰도록 위치 루트(MY/SHARE)까지 붙인 경로를 돌려준다.
     */
    @Override
    public String findResourcePath(Long resourceSeq, Integer userSeq) {
        var query = ResourceFindQuery.builder()
                .resourceSeq(resourceSeq)
                .userSeq(userSeq)
                .isDelete(false)
                .build();
        var resource = resourceFindService.findDetailOrNull(query);
        if (resource == null) {
            return null;
        }
        return resourceAuditService.findDisplayPath(resourceSeq, resource.getParentFolderSeq(), resource.getOwnerSeq());
    }

    @Override
    public ShareWithTargetsData findCurrentShareState(Long shareSeq, Integer userSeq) {
        return shareFindService.findShareWithTargets(shareSeq, userSeq, false);
    }
}

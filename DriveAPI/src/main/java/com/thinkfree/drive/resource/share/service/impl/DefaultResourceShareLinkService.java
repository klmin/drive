package com.thinkfree.drive.resource.share.service.impl;

import com.thinkfree.drive.resource.exception.ResourceErrorCode;
import com.thinkfree.drive.resource.repository.ResourceQueryRepository;
import com.thinkfree.drive.resource.repository.ResourceRepository;
import com.thinkfree.drive.resource.share.service.ResourceShareLinkService;
import com.thinkfree.drive.resource.share.service.ResourceShareTokenService;
import com.thinkfree.drive.share.service.ShareDomainService;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
@RequiredArgsConstructor
public class DefaultResourceShareLinkService implements ResourceShareLinkService {

    private final ResourceRepository resourceRepository;
    private final ResourceQueryRepository resourceQueryRepository;
    private final ResourceShareTokenService resourceShareTokenService;
    private final ShareDomainService shareDomainService;

    @Override
    public String getOrCreateLinkUrl(int ownerUserSeq, long resourceSeq) {

        var data = resourceQueryRepository.findShareTokenForLink(ownerUserSeq, resourceSeq);
        DriveAssert.throwIfNull(data, ResourceErrorCode.NOT_FOUND);

        var token = data.getShareToken();
        
        if (!StringUtils.hasText(token)) {
            // 토큰이 없을 때만 — 단건 JPQL(수정 감사 그대로: 변경 감지로 하면 Auditing 이 링크를 발급한 사람·시각으로 수정자·수정일을 덮었다).
            // 동시 발급으로 이미 들어갔으면(0행) 들어간 토큰을 다시 읽는다
            String newToken = resourceShareTokenService.createNewToken();
            if (resourceRepository.applyShareTokenIfAbsent(ownerUserSeq, resourceSeq, newToken) == 1) {
                token = newToken;
            } else {
                var current = resourceQueryRepository.findShareTokenForLink(ownerUserSeq, resourceSeq);
                DriveAssert.throwIfFalse(current != null && StringUtils.hasText(current.getShareToken()), ResourceErrorCode.NOT_FOUND);
                token = current.getShareToken();
            }
        }

        return shareDomainService.getLinkBaseUrl(data.getTenantSeq()) + token;
    }
}

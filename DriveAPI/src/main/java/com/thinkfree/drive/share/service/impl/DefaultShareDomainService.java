package com.thinkfree.drive.share.service.impl;

import com.thinkfree.drive.global.service.ClientDomainService;
import com.thinkfree.drive.share.properties.ShareProperty;
import com.thinkfree.drive.share.service.ShareDomainService;
import com.thinkfree.drive.util.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultShareDomainService implements ShareDomainService {

    private final ShareProperty shareProperty;
    private final ClientDomainService clientDomainService;

    @Override
    public String getLinkBaseUrl(int tenantSeq) {
        var domain = clientDomainService.getDomain(tenantSeq);
        return StringUtil.buildUrl(domain, shareProperty.contextPath(), "?t=");
    }
}

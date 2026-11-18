package com.thinkfree.drive.smtpconfig.service;

import com.thinkfree.drive.smtpconfig.data.SmtpConfigData;
import com.thinkfree.drive.smtpconfig.exception.SmtpConfigErrorCode;
import com.thinkfree.drive.smtpconfig.repository.SmtpConfigQueryRepository;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SmtpConfigFindService {
    private final SmtpConfigQueryRepository smtpConfigQueryRepository;

    public SmtpConfigData findBySmtpConfigSeqDetail(Integer smtpConfigSeq, Integer tenantSeq) {
        var smtpConfig = smtpConfigQueryRepository.findBySmtpConfigSeq(smtpConfigSeq, tenantSeq);
        DriveAssert.throwIfNull(smtpConfig, SmtpConfigErrorCode.SMTP_CONFIG_NOT_FOUND);
        return smtpConfig;
    }

    public SmtpConfigData findCurrentSmtpConfig(Integer tenantSeq) {
        var smtpConfig = smtpConfigQueryRepository.findByTenant(tenantSeq);
        DriveAssert.throwIfNull(smtpConfig, SmtpConfigErrorCode.SMTP_CONFIG_NOT_FOUND);
        return smtpConfig;
    }
}

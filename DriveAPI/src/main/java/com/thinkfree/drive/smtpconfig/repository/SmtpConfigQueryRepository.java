package com.thinkfree.drive.smtpconfig.repository;

import com.thinkfree.drive.smtpconfig.data.SmtpConfigData;

/**
 * tb_smtp_config 읽기 — {@code SmtpConfigFindService} 가 주입. 쓰기는 {@link SmtpConfigRepository}.
 */
public interface SmtpConfigQueryRepository {

    /** PK 단건 — 없거나 다른 테넌트 행이면 null */
    SmtpConfigData findBySmtpConfigSeq(Integer smtpConfigSeq, Integer tenantSeq);

    /** 테넌트의 현재 설정(테넌트당 1건 전제), 없으면 null */
    SmtpConfigData findByTenant(Integer tenantSeq);
}

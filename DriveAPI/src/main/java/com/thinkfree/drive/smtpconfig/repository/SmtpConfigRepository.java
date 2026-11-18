package com.thinkfree.drive.smtpconfig.repository;

import com.thinkfree.drive.smtpconfig.entity.SmtpConfig;

/**
 * tb_smtp_config 쓰기 — {@code SmtpConfigService} 가 주입. 읽기는 {@link SmtpConfigQueryRepository}.
 */
public interface SmtpConfigRepository {

    /** 신규 등록(identity 키·register_date 는 저장 때 채워짐) */
    void save(SmtpConfig smtpConfig);

    /** 고칠 행 — 관리 엔티티(변경 메서드로 고치면 커밋 때 변경 감지). 없으면 null — 테넌트 판정은 Service */
    SmtpConfig findById(int smtpConfigSeq);
    /** 테넌트 삭제 — 이 테넌트의 행 전부. 지운 행 수 */
    int deleteByTenant(Integer tenantSeq);
}

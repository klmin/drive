package com.thinkfree.drive.smtpconfig.service;

import com.thinkfree.drive.smtpconfig.dto.SmtpConfigSaveDto;
import com.thinkfree.drive.email.service.EmailService;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.smtpconfig.util.SmtpPasswordEncryptor;
import com.thinkfree.drive.smtpconfig.data.SmtpConfigData;
import com.thinkfree.drive.smtpconfig.entity.SmtpConfig;
import com.thinkfree.drive.smtpconfig.enums.SmtpPassword;
import com.thinkfree.drive.smtpconfig.exception.SmtpConfigErrorCode;
import com.thinkfree.drive.smtpconfig.repository.SmtpConfigRepository;
import com.thinkfree.drive.smtpconfig.result.SmtpConfigDetailResult;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantPolicy;
import com.thinkfree.drive.util.MaskingUtils;
import org.springframework.util.StringUtils;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmtpConfigService {
    private final SmtpConfigRepository smtpConfigRepository;
    private final SmtpPasswordEncryptor smtpPasswordEncryptor;
    private final SmtpConfigFindService smtpConfigFindService;
    private final EmailService emailService;
    private final TenantPolicy tenantPolicy;

    @Transactional
    public void saveOrUpdateSmtpConfig(Integer loginUserSeq, SmtpConfigSaveDto saveDto) {
        Integer tenantSeq = TenantContext.getOrThrow();

        // 수정 = 관리 엔티티 한 번 로드(없거나 다른 테넌트면 NOT_FOUND) → 변경 메서드 → 커밋 때 변경 감지, 등록 = 새 엔티티 → save
        boolean isNew = saveDto.smtpConfigSeq() == null;
        SmtpConfig smtpConfig = isNew
                ? SmtpConfig.create(tenantSeq, loginUserSeq)
                : smtpConfigRepository.findById(saveDto.smtpConfigSeq());
        // 없는 행·다른 테넌트 행은 같은 NOT_FOUND(테넌트 판정은 Java — 10-13)
        DriveAssert.throwIfTrue(smtpConfig == null || !tenantSeq.equals(smtpConfig.getTenantSeq()), SmtpConfigErrorCode.SMTP_CONFIG_NOT_FOUND);

        // 비밀번호·OAuth 토큰은 바꿀 때만 보낸다 — 수정 요청에서 비어 있으면 기존 값 유지
        String password = cleanPassword(saveDto.encPwd());
        SmtpPassword encryptedPwd = isNew || !password.isEmpty()
                ? new SmtpPassword(smtpPasswordEncryptor.encrypt(password))
                : smtpConfig.getEncPwd();
        String oauthInfo = isNew || StringUtils.hasText(saveDto.oauthInfo())
                ? saveDto.oauthInfo()
                : smtpConfig.getOauthInfo();

        smtpConfig.changeSettings(saveDto.clientName(), saveDto.host(), saveDto.port(), saveDto.encryptionType(), saveDto.authType(),
                saveDto.username(), encryptedPwd, oauthInfo, saveDto.fromAddress(), saveDto.testReceiverAddress());
        if (isNew) {
            smtpConfigRepository.save(smtpConfig);
        }
    }

    @Transactional
    public void testSmtpConfig(Integer smtpConfigSeq) {
        Integer tenantSeq = TenantContext.getOrThrow();
        SmtpConfigData smtpConfig = smtpConfigFindService.findBySmtpConfigSeqDetail(smtpConfigSeq, tenantSeq);

        // EmailService에 테스트 메일 발송 요청
        emailService.sendTestEmail(smtpConfig);
    }

    @Transactional(readOnly = true)
    public SmtpConfigDetailResult getSmtpConfig() {
        Integer tenantSeq = TenantContext.getOrThrow();
        SmtpConfigData smtpConfig = smtpConfigFindService.findCurrentSmtpConfig(tenantSeq);
        boolean hasPassword = hasPassword(smtpConfig.getEncPwd());
        boolean hasOauthInfo = StringUtils.hasText(smtpConfig.getOauthInfo());
        if (!tenantPolicy.allows(tenantSeq, TenantFeature.SMTP_INFO_VIEW)) {
            smtpConfig = maskConnectionInfo(smtpConfig);
        }
        return new SmtpConfigDetailResult(smtpConfig, hasPassword, hasOauthInfo);
    }

    /** 접속 정보(클라이언트 이름·호스트·계정·발신 주소·테스트 수신 주소)를 가린 사본 — 포트·암호화·인증 방식은 그대로 */
    private static SmtpConfigData maskConnectionInfo(SmtpConfigData data) {
        return new SmtpConfigData(data.getSmtpConfigSeq(), data.getTenantSeq(),
                MaskingUtils.hideIfPresent(data.getClientName()), MaskingUtils.hideIfPresent(data.getHost()),
                data.getPort(), data.getEncryptionType(), data.getAuthType(), MaskingUtils.hideIfPresent(data.getUsername()),
                data.getEncPwd(), data.getOauthInfo(),
                MaskingUtils.hideIfPresent(data.getFromAddress()), MaskingUtils.hideIfPresent(data.getTestReceiverEmail()),
                data.getRegisterUserSeq(), data.getRegisterDate(), data.getUpdateUserSeq(), data.getUpdateDate());
    }

    /** 공백 제거, 비어 있으면 빈 문자열 */
    private static String cleanPassword(String password) {
        return password == null || password.trim().isEmpty() ? "" : password.replace(" ", "");
    }

    /** 저장된 비밀번호가 빈 값이 아닌지 */
    private boolean hasPassword(SmtpPassword encrypted) {
        return encrypted != null && encrypted.getValue() != null
                && StringUtils.hasText(smtpPasswordEncryptor.decrypt(encrypted.getValue()));
    }

    /** 테넌트 삭제 — 이 테넌트의 행 전부 */
    public void deleteByTenant(Integer tenantSeq) {
        smtpConfigRepository.deleteByTenant(tenantSeq);
    }
}

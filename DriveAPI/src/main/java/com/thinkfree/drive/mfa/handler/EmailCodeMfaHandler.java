package com.thinkfree.drive.mfa.handler;

import com.thinkfree.drive.mfa.result.MfaSetupResult;
import com.thinkfree.drive.user.enums.MfaMethod;
import org.springframework.stereotype.Component;

@Component
public class EmailCodeMfaHandler implements MfaHandler {

    @Override
    public MfaMethod supports() {
        return MfaMethod.EMAIL_CODE;
    }

    @Override
    public MfaSetupResult setup(Integer userSeq, String userEmail) {
        // TODO: 이메일 인증 설정 로직 (인증코드 발송 등)
        throw new UnsupportedOperationException("이메일 인증은 아직 지원하지 않습니다.");
    }

    @Override
    public void confirm(Integer userSeq, String code) {
        // TODO: 이메일 인증 확인 로직
        throw new UnsupportedOperationException("이메일 인증은 아직 지원하지 않습니다.");
    }

    @Override
    public boolean verify(String secretKey, String code) {
        // TODO: Redis 등에서 발송한 인증코드와 비교
        throw new UnsupportedOperationException("이메일 인증은 아직 지원하지 않습니다.");
    }
}

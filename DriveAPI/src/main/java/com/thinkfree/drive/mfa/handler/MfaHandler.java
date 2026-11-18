package com.thinkfree.drive.mfa.handler;

import com.thinkfree.drive.mfa.result.MfaSetupResult;
import com.thinkfree.drive.user.enums.MfaMethod;

public interface MfaHandler {

    MfaMethod supports();

    MfaSetupResult setup(Integer userSeq, String userId);

    void confirm(Integer userSeq, String code);

    boolean verify(String secretKey, String code);
}

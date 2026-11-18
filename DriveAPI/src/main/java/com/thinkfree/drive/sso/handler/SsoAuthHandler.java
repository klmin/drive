package com.thinkfree.drive.sso.handler;

import com.thinkfree.drive.sso.model.SsoUserInfo;
import com.thinkfree.drive.sso.handler.params.SsoCallbackParams;
import com.thinkfree.drive.sso.protocol.data.SsoProtocolData;
import com.thinkfree.drive.sso.provider.data.SsoProviderData;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;

public interface SsoAuthHandler {

    SsoProviderProtocolType supports();
    String buildAuthorizationUrl(SsoProtocolData ssoProtocolData, String state, String redirectUri);
    String callbackPath();
    SsoUserInfo handleCallback(SsoProviderData provider, SsoProtocolData config, SsoCallbackParams params);
}

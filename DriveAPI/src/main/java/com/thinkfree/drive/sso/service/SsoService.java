package com.thinkfree.drive.sso.service;

import com.thinkfree.drive.sso.provider.data.SsoProviderDetailData;
import com.thinkfree.drive.sso.provider.enums.SsoProviderProtocolType;
import com.thinkfree.drive.sso.result.SsoCallbackResult;
import com.thinkfree.drive.sso.result.SsoSessionTokenResult;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;

public interface SsoService {

    SsoProviderProtocolType supports();
    String buildAuthorizationUrl(SsoProviderDetailData detail);
    
    SsoCallbackResult handleCallback(String authResponse, String correlationId);
    SsoSessionTokenResult resolveSessionToken(Claims claims, HttpServletRequest httpRequest);
    String redirectUrl();
    
}

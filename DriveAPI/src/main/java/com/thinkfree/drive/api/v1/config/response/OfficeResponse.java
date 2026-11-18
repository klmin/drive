package com.thinkfree.drive.api.v1.config.response;

/** 클라이언트용 Office 설정 — 접속 주소·어댑터 이름은 내려주지 않고 주소의 https 여부(officeHttps)만 준다 */
public record OfficeResponse(
        String officeConnectionType,
        String officeAuthMode,
        boolean officeHttps
)
{
}


package com.thinkfree.drive.license.auth.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 인증 유형. 인증 서버는 본문이 아니라 경로로 유형을 정한다.
 */
@Getter
@RequiredArgsConstructor
public enum LicenseAuthType {
    INIT("init"),
    RESTART("restart"),
    AUTO("auto"),
    MANUAL("manual");

    private final String path;
}

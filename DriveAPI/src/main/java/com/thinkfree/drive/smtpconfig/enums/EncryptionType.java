package com.thinkfree.drive.smtpconfig.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EncryptionType {
    SSL("SSL", "SSL 암호화"),
    TLS("TLS", "TLS 암호화"),
    NONE("NONE", "암호화 없음");

    private final String value;
    private final String title;
}

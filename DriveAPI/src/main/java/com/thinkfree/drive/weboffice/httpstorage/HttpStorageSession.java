package com.thinkfree.drive.weboffice.httpstorage;

import com.thinkfree.drive.weboffice.enums.WebOfficeType;

public record HttpStorageSession(Integer tenantSeq, Integer userSeq, Integer ownerUserSeq,
                                 Long resourceSeq, String adapter, String path, WebOfficeType app,
                                 long expiresAt) {
    public String docId() { return ownerUserSeq + "-" + resourceSeq; }
}

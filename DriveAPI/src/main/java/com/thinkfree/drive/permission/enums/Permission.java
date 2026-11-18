package com.thinkfree.drive.permission.enums;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public enum Permission {
    
    VIEW(Set.of()),
    EDIT(Set.of(VIEW)),
    DOWNLOAD(Set.of(VIEW)),
    UPLOAD(Set.of(VIEW)),
    DELETE(Set.of(VIEW, EDIT, UPLOAD)),
    SHARE(Set.of(VIEW));

    private final Set<Permission> prerequisites;
    
}

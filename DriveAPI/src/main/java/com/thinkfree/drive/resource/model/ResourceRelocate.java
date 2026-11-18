package com.thinkfree.drive.resource.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class ResourceRelocate {
    private Long totalSizeBye;
    
    public static ResourceRelocate create(Long totalSizeBye){
        return new ResourceRelocate(totalSizeBye);
    }
}

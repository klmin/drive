package com.thinkfree.drive.user.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public enum UserStatus {
    
    ACTIVE("활성화"),
    INACTIVE("비활성화"),
    PENDING_DELETE("삭제대기"),
    DELETED("삭제");
    
    private final String name;
    
    public boolean isPendingDelete(){
        return this == PENDING_DELETE;
    }
    
    public static List<UserStatus> displayableStatuses(){
        return List.of(UserStatus.ACTIVE, UserStatus.INACTIVE);
    }
    
    @JsonCreator
    public static UserStatus from(String value) {
        return value == null ? null : valueOf(value.toUpperCase());
    }
    
}

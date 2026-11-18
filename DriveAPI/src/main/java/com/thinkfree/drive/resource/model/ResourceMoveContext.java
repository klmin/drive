package com.thinkfree.drive.resource.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class ResourceMoveContext {
  
    private final boolean userChanged;
    
    public static ResourceMoveContext sameOwner(){
        return new ResourceMoveContext(false);
    }
    
    public static ResourceMoveContext create(boolean userChanged){
        return new ResourceMoveContext(userChanged);
    }
}

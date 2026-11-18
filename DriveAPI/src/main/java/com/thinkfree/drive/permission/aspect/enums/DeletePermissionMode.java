package com.thinkfree.drive.permission.aspect.enums;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum DeletePermissionMode {
      DELETE,
      NO_DELETE,
      ANY;
  
    public Boolean toBoolean() {
          return switch (this) {
              case DELETE    -> Boolean.TRUE;
              case NO_DELETE -> Boolean.FALSE;
              case ANY       -> null;
          };
      }
}

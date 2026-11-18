package com.thinkfree.drive.user.admin.enums;

import com.thinkfree.drive.constants.DriveConstants;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;


@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public enum AdminFileTemplateType {
    USER_IMPORT_CSV("admin-users-import_sample.csv")
    ;

    private static final String BASE_PATH = "admin/template";
    
    private final String fileName;
    
    public String getClasspath() {
        return BASE_PATH + DriveConstants.PATH_SEPARATOR + fileName;
    }
   
}

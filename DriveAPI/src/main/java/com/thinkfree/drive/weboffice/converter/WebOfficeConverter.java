package com.thinkfree.drive.weboffice.converter;

import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.weboffice.dto.WebOfficeGetResult;
import com.thinkfree.drive.weboffice.dto.WebOfficeInfoResult;
import com.thinkfree.drive.weboffice.dto.WebOfficeSaveResult;
import org.mapstruct.Mapper;

import java.io.InputStream;
import java.time.Instant;

@Mapper(componentModel = "spring")
public interface WebOfficeConverter {
    
    WebOfficeInfoResult toInfoResult(ResourceType resourceType, String resourceName, Instant updateDate, Boolean read, Boolean write, Boolean locked, String loginUserName, long sizeByte, String profileImage, String cursorColor, int currentVersionNumber);
    WebOfficeGetResult toGetResult(InputStream inputStream);
    WebOfficeSaveResult toSaveResult(String docId);
   
}

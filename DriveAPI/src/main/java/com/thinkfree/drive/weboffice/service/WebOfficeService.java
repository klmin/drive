package com.thinkfree.drive.weboffice.service;

import com.thinkfree.drive.weboffice.dto.*;

public interface WebOfficeService {
    
    WebOfficeInfoResult fetchInfo(WebOfficeInfoDto dto);
    WebOfficeGetResult loadFile(WebOfficeGetDto dto);
    WebOfficeSaveResult save(WebOfficeSaveDto dto);
}

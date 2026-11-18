package com.thinkfree.drive.weboffice.dto;

import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.weboffice.enums.WebOfficeSaveType;
import com.thinkfree.drive.weboffice.enums.WebOfficeType;
import lombok.Builder;
import lombok.Getter;

import java.io.InputStream;

@Builder(toBuilder = true)
@Getter
public class WebOfficeSaveDto {
    
    private WebOfficeType webOfficeType;
    private Long openTimestamp;
    private Integer loginUserSeq;
    private String loginUserName;
    private Integer userSeq;
    private Long resourceSeq;
    private WebOfficeSaveType webOfficeSaveType;
    private Long size;
    private String docId;
    private InputStream inputStream;

    /** 감사 로그 행위자 - 웹오피스는 SecurityContext가 없어 호출부가 조회한 User를 싣는다. */
    private UserDetailData actor;
    
}

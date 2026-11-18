package com.thinkfree.drive.weboffice.dto;

import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.weboffice.enums.WebOfficeType;
import lombok.Builder;
import lombok.Getter;

@Builder(toBuilder = true)
@Getter
public class WebOfficeGetDto {
    
    private Integer loginUserSeq;
    private String loginUserName;
    private Integer userSeq;
    private Long resourceSeq;
    private WebOfficeType webOfficeType;

    /** 감사 로그 행위자 - 웹오피스는 SecurityContext가 없어 호출부가 조회한 User를 싣는다. */
    private UserDetailData actor;
    
}

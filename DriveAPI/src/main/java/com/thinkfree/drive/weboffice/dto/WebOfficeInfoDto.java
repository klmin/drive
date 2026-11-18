package com.thinkfree.drive.weboffice.dto;

import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.weboffice.enums.WebOfficeType;
import lombok.Builder;
import lombok.Getter;

@Builder(toBuilder = true)
@Getter
public class WebOfficeInfoDto {

    private Integer loginUserSeq;
    private String loginUserName;
    private Integer userSeq;
    private Long resourceSeq;
    private WebOfficeType webOfficeType;
    private String profileImage;
    private String cursorColor;
    private String lang;

    /** 어댑터 요청 유형 원본값 - 미지정과 미지원 값을 구분해야 해서 문자열 그대로 싣는다. */
    private String type;

    /** 감사 로그 행위자 - 웹오피스는 SecurityContext가 없어 호출부가 조회한 User를 싣는다. */
    private UserDetailData actor;

}

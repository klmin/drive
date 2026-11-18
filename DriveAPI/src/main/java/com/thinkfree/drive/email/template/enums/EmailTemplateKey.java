package com.thinkfree.drive.email.template.enums;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import static com.thinkfree.drive.messagesource.keys.MessageKeys.*;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public enum EmailTemplateKey {
    SHARE_FOLDER(EMAIL_SHARE_FOLDER_SUBJECT, EMAIL_SHARE_BODY,"폴더 공유"),
    SHARE_FILE(EMAIL_SHARE_FILE_SUBJECT, EMAIL_SHARE_BODY,"파일 공유"),
    ADMIN_USER_CSV_REGISTER(EMAIL_ADMIN_CSV_REGISTER_SUBJECT, EMAIL_ADMIN_CSV_REGISTER_BODY, "관리자 csv 등록"),
    PASSWORD_RESET(EMAIL_PASSWORD_RESET_SUBJECT, EMAIL_PASSWORD_RESET_BODY, "비밀번호 재설정")
    ;
    
    private final String subjectMessageKey;
    private final String bodyMessageKey;
    private final String description;
    
}

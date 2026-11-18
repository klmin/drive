package com.thinkfree.drive.messagesource.keys;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MessageKeys {
    public static final String DOCUMENT_NEW_PPT = "document.new.ppt";
    public static final String DOCUMENT_NEW_WORD = "document.new.word";
    public static final String DOCUMENT_NEW_EXCEL = "document.new.excel";
    public static final String DOCUMENT_NEW_NOTE = "document.new.note";
    public static final String FILE_COPY_SUFFIX = "file.copy.suffix";
    public static final String FILE_READ_UNSUPPORTED_FORMAT = "file.read.reason.unsupported-format";
    public static final String FILE_READ_SIZE_EXCEEDED = "file.read.reason.size-exceeded";
    public static final String FILE_READ_EXTRACT_FAILED = "file.read.reason.extract-failed";
    public static final String FILE_READ_EXTRACT_EMPTY = "file.read.reason.extract-empty";
    public static final String FILE_READ_OFFSET_OUT_OF_RANGE = "file.read.error.offset-out-of-range";
    public static final String FILE_WRITE_CONTENT_REQUIRED = "file.write.error.content-required";
    public static final String FILE_WRITE_SIZE_EXCEEDED = "file.write.error.size-exceeded";
    public static final String FILE_WRITE_INVALID_BASE64 = "file.write.error.invalid-base64";
    public static final String FILE_DOWNLOAD_SIZE_EXCEEDED = "file.download.reason.size-exceeded";
    public static final String EMAIL_SHARE_FOLDER_SUBJECT = "email.share.folder.subject";
    public static final String EMAIL_SHARE_FILE_SUBJECT = "email.share.file.subject";
    public static final String EMAIL_SHARE_BODY = "email.share.body";
    public static final String EMAIL_ADMIN_CSV_REGISTER_SUBJECT = "email.admin.csv.register.subject";
    public static final String EMAIL_ADMIN_CSV_REGISTER_BODY = "email.admin.csv.register.body";
    public static final String EMAIL_PASSWORD_RESET_SUBJECT = "email.password.reset.subject";
    public static final String EMAIL_PASSWORD_RESET_BODY = "email.password.reset.body";
    public static final String WEB_OFFICE_ERROR_FORBIDDEN = "web.office.error.forbidden";
}

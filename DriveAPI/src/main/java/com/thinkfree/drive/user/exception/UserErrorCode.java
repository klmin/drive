package com.thinkfree.drive.user.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {
    INVALID_PASSWORD_PATTERN(HttpStatus.BAD_REQUEST, "USER_001", "비밀번호 형식에 맞지 않습니다."),
    INVALID_EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "USER_002", "이메일 형식에 맞지 않습니다."),
    ADMIN_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_003", "관리자 정보를 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "USER_004", "이미 등록된 이메일입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_005", "유저 정보를 찾을 수 없습니다."),
    PROFILE_UPLOAD_FAIL(HttpStatus.BAD_REQUEST, "USER_007", "프로필 이미지 업로드에 실패했습니다."),
    PASSWORD_NOT_MATCH(HttpStatus.BAD_REQUEST, "USER_009", "입력한 비밀번호가 현재 비밀번호와 일치하지 않습니다."),
    NEW_PASSWORD_SAME_AS_OLD(HttpStatus.BAD_REQUEST, "USER_010", "새 비밀번호가 이전 비밀번호와 동일합니다."),
    CANNOT_DEACTIVATE_SELF_OR_DEFAULT_ADMIN(HttpStatus.FORBIDDEN, "USER_011", "기본 관리자나 본인은 비활성화할 수 없습니다. "),
    CANNOT_DELETE_SELF_OR_DEFAULT_ADMIN(HttpStatus.FORBIDDEN, "USER_012", "기본 관리자나 본인은 삭제할 수 없습니다. "),
    INSERT_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, "USER_013","등록에 실패 했습니다."),
    QUOTA_DECREASE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "USER_014","현재 사용 중인 용량보다 작은 용량으로 변경할 수 없습니다."),
    PROFILE_DELETE_FAIL(HttpStatus.BAD_REQUEST, "USER_015", "프로필 이미지 삭제에 실패했습니다."),
    WITHDRAW_PENDING_FAIL(HttpStatus.BAD_REQUEST, "USER_016","사용자 삭제 대기 처리에 실패했습니다."),
    ACTIVATE_FAIL(HttpStatus.BAD_REQUEST, "USER_017", "사용자 활성화에 실패했습니다."),
    INACTIVATE_FAIL(HttpStatus.BAD_REQUEST, "USER_018", "사용자 비활성화에 실패했습니다."),
    INVALID_STATUS_PARAM(HttpStatus.BAD_REQUEST, "USER_019", "요청된 상태(status) 값이 잘못되었습니다."),
    MFA_SETUP_FAIL(HttpStatus.BAD_REQUEST, "USER_020", "MFA 설정에 실패했습니다."),
    MFA_CONFIRM_FAIL(HttpStatus.BAD_REQUEST, "USER_021", "MFA 설정확인에 실패했습니다."),
    MFA_CANCEL_FAIL(HttpStatus.BAD_REQUEST, "USER_022", "MFA 설정해제에 실패했습니다."),
    INVALID_PASSWORD_RESET_TOKEN(HttpStatus.BAD_REQUEST, "USER_029", "유효하지 않은 비밀번호 재설정 토큰입니다."),
    EXPIRED_PASSWORD_RESET_TOKEN(HttpStatus.BAD_REQUEST, "USER_023", "만료된 비밀번호 재설정 토큰입니다."),
    UPDATE_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, "USER_024", "수정에 실패 했습니다."),
    BULK_UPSERT_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_025", "업데이트 대상 사용자를 찾을 수 없습니다."),
    REQUIRED_FIELD(HttpStatus.BAD_REQUEST, "USER_026", "필수 입력값이 누락되었습니다."),
    PHONE_FIELDS_REQUIRED_TOGETHER(HttpStatus.BAD_REQUEST, "USER_027", "countryCode와 phoneNumber는 함께 입력하거나 함께 비워야 합니다."),
    INVALID_ROLE_PARAM(HttpStatus.BAD_REQUEST, "USER_028", "요청된 권한(roles) 값이 잘못되었습니다."),
    ROLE_REVOKE_USER_IN_USE(HttpStatus.BAD_REQUEST, "USER_030", "사용 중인 용량이 있어 사용자 역할을 회수할 수 없습니다. 파일 정리 후 회수해 주세요."),
    ROLE_REVOKE_SELF_ADMIN(HttpStatus.BAD_REQUEST, "USER_031", "본인의 관리자 역할은 회수할 수 없습니다."),
    PROFILE_IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "USER_032", "프로필 이미지는 5MB 이하만 업로드할 수 있습니다."),
    PROFILE_IMAGE_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "USER_033", "프로필 이미지는 PNG·JPG·GIF·WEBP 형식만 업로드할 수 있습니다."),
    PROTECTED_USER(HttpStatus.FORBIDDEN, "USER_034", "보호 계정은 탈퇴·삭제·비활성화·역할 변경·관리자 비밀번호 변경을 할 수 없습니다."),
    ;
    

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}

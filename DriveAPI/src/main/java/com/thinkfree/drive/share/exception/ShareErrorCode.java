package com.thinkfree.drive.share.exception;

import com.thinkfree.drive.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ShareErrorCode implements ErrorCode {

    RESOURCE_NOT_SHARED(HttpStatus.FORBIDDEN, "SHARE_001","리소스가 공유되지 않았습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "SHARE_002", "접근 권한이 없습니다"),
    DELETE_FAILED(HttpStatus.BAD_REQUEST, "SHARE_003", "삭제에 실패했습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "SHARE_004", "데이터를 찾을 수 없습니다."),
    UPDATE_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, "SHARE_005","수정에 실패 했습니다."),
    DUPLICATE_LINK(HttpStatus.INTERNAL_SERVER_ERROR, "SHARE_006", "이미 존재하는 링크 입니다."),
    INVALID_TOKEN(HttpStatus.NOT_FOUND, "SHARE_007","해당 토큰의 공유 링크를 찾을 수 없습니다."),
    INVALID_SHARE_TYPE(HttpStatus.BAD_REQUEST, "SHARE_008","해당 공유 유형으로 변경이 불가능 합니다."),
    INSERT_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, "SHARE_009","등록에 실패 했습니다."),
    SUPER_ADMIN_SHARE_ENFORCED(HttpStatus.CONFLICT, "SHARE_010","슈퍼어드민이 공유 설정을 전체 적용 중이라 변경할 수 없습니다."),
    SPECIFIC_USERS_NOT_ALLOWED(HttpStatus.FORBIDDEN, "SHARE_011","개별 사용자 공유가 허용되지 않아 사용자를 검색할 수 없습니다."),
    SHARE_DISABLED(HttpStatus.FORBIDDEN, "SHARE_012","공유 기능이 비활성화되어 있습니다."),
    SHARE_TYPE_NOT_ALLOWED(HttpStatus.FORBIDDEN, "SHARE_013","해당 공유 유형은 현재 공유 설정상 허용되지 않습니다."),
    EXTERNAL_SHARE_NOT_ALLOWED(HttpStatus.FORBIDDEN, "SHARE_014","외부 테넌트 사용자 공유가 허용되지 않습니다."),
    ;

    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}
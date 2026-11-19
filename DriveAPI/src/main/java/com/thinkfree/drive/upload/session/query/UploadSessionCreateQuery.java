package com.thinkfree.drive.upload.session.query;

import com.thinkfree.drive.upload.session.enums.UploadSaveType;

/**
 * 세션 INSERT(PENDING, storage_key·upload_id 는 스토리지 준비 후 채움).
 *
 * @param tenantSeq        테넌트 seq(없으면 null — key 접두 0)
 * @param userSeq          소유자(용량 대상)
 * @param requestUserSeq   요청자(시큐리티 컨텍스트 로그인 사용자)
 * @param saveType         저장 유형
 * @param parentFolderSeq  부모 폴더(DUPLICATE)
 * @param resourceSeq      대상 리소스(VERSION·OVERWRITE·RESTORE)
 * @param sourceVersionSeq 복원 원본 버전(RESTORE)
 * @param baseStorageKey   세션 시작 시 대상의 현재 key(OVERWRITE)
 * @param fileName         요청 파일명
 * @param contentType      서버가 확장자로 정한 Content-Type
 * @param sizeByte         신고 크기
 * @param partSize         파트 크기(멀티파트일 때만)
 * @param partCount        파트 수(멀티파트일 때만)
 */
public record UploadSessionCreateQuery
(
    Integer tenantSeq,
    int userSeq,
    int requestUserSeq,
    UploadSaveType saveType,
    Long parentFolderSeq,
    Long resourceSeq,
    Long sourceVersionSeq,
    String baseStorageKey,
    String fileName,
    String contentType,
    long sizeByte,
    Long partSize,
    Integer partCount
)
{
}

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
 * @param reserveByte      진행 중 용량으로 셀 크기 — 커밋 때 늘어날 것으로 보이는 양(OVERWRITE 는 새 크기 − 시작 시 대상 크기, 0 이상.
 *                         그 외 sizeByte). 사전 필터·uploadingByte 용이고 최종 판정은 커밋 tx
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
    Integer partCount,
    long reserveByte
)
{
}

package com.thinkfree.drive.application.upload.command;

import com.thinkfree.drive.resource.enums.ResourceSaveType;

/**
 * 청크 업로드 세션 생성.
 *
 * @param sharedByUserSeq 공유 폴더·파일이면 공유한 사용자(소유자, 용량 대상). 내 드라이브면 null
 * @param parentFolderSeq 신규(DUPLICATE) 위치 — null 이면 루트
 * @param resourceSeq     VERSION·OVERWRITE 대상
 * @param fileName        파일명(확장자 포함)
 * @param size            전체 크기(byte)
 * @param saveType        null 이면 DUPLICATE
 */
public record UploadSessionStartCommand
(
    Integer sharedByUserSeq,
    Long parentFolderSeq,
    Long resourceSeq,
    String fileName,
    long size,
    ResourceSaveType saveType
)
{
}

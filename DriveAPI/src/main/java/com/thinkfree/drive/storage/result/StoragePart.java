package com.thinkfree.drive.storage.result;

/**
 * 멀티파트 업로드의 파트 하나 — ListParts 결과·CompleteMultipartUpload 입력.
 *
 * @param partNumber 파트 번호(1부터)
 * @param eTag       파트 ETag(따옴표 포함 그대로)
 * @param size       파트 크기(byte). complete 입력으로만 쓸 때는 의미 없다
 */
public record StoragePart
(
    int partNumber,
    String eTag,
    long size
)
{
}

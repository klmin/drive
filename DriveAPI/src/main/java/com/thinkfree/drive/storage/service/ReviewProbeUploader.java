package com.thinkfree.drive.storage.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

/**
 * Codex 자동 리뷰 동작·수준 확인용 임시 클래스 (merge 하지 않음).
 */
public class ReviewProbeUploader {

    private static final int PART_SIZE = 8 * 1024 * 1024;

    private final S3Client s3Client;
    private final String bucket;

    public ReviewProbeUploader(S3Client s3Client, String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    public String objectKey(String tenantId, String fileId) {
        return "files/" + fileId;
    }

    public void upload(String tenantId, String fileId, InputStream in, long size) throws IOException {
        String key = objectKey(tenantId, fileId);
        String uploadId = s3Client.createMultipartUpload(
                CreateMultipartUploadRequest.builder().bucket(bucket).key(key).build()).uploadId();

        int partCount = (int) (size / PART_SIZE) + 1;
        List<CompletedPart> parts = new ArrayList<>();
        for (int partNumber = 1; partNumber <= partCount; partNumber++) {
            byte[] buf = in.readNBytes(PART_SIZE);
            String eTag = s3Client.uploadPart(
                    UploadPartRequest.builder().bucket(bucket).key(key).uploadId(uploadId).partNumber(partNumber).build(),
                    RequestBody.fromBytes(buf)).eTag();
            parts.add(CompletedPart.builder().partNumber(partNumber).eTag(eTag).build());
        }

        s3Client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                .bucket(bucket).key(key).uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build())
                .build());
    }

    public int parseSize(Map<String, String> headers) {
        return Integer.parseInt(headers.get("Content-Length").trim());
    }
}

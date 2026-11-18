package com.thinkfree.drive.storage.service;

import com.thinkfree.drive.common.file.model.FileData;
import com.thinkfree.drive.storage.constants.StorageConstants;
import com.thinkfree.drive.storage.exception.StorageErrorCode;
import com.thinkfree.drive.storage.exception.StorageException;
import com.thinkfree.drive.storage.properties.S3ConnectionProperty;
import com.thinkfree.drive.storage.properties.StorageProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** 저장 마커 — 버전 저장이 원본 키에 올린 내용에 그 저장의 버전 seq 를 남기고, 만료 정리가 그것으로 업로드 완료를 판정한다. */
@DisplayName("S3StorageService 저장 마커")
class S3StorageServiceSaveMarkerTest {

    private static final SdkHttpResponse OK = SdkHttpResponse.builder().statusCode(200).build();

    private final S3Client s3Client = Mockito.mock(S3Client.class);
    private final S3StorageService service = new S3StorageService(s3Client, Mockito.mock(S3AsyncClient.class),
            new S3ConnectionProperty("http://localhost:9000", "us-east-1", "ak", "sk", "bucket"), Mockito.mock(StorageProperty.class));

    @SuppressWarnings("unchecked")
    private void givenHead(Object resultOrException) {
        var stub = given(s3Client.headObject(any(Consumer.class)));
        if (resultOrException instanceof RuntimeException e) {
            stub.willThrow(e);
        } else {
            stub.willReturn((HeadObjectResponse) resultOrException);
        }
    }

    @Test
    @DisplayName("findSaveMarker — 마커가 있으면 값, 없으면 empty")
    void findSaveMarker() {
        givenHead(HeadObjectResponse.builder().metadata(Map.of(StorageConstants.SAVE_MARKER_METADATA_KEY, "42")).build());
        assertThat(service.findSaveMarker("k")).contains("42");

        givenHead(HeadObjectResponse.builder().metadata(Map.of("other", "x")).build());
        assertThat(service.findSaveMarker("k")).isEmpty();
    }

    @Test
    @DisplayName("findSaveMarker — 객체 없음(NoSuchKey·404)은 empty, 그 밖의 실패는 GET_FAILED")
    void findSaveMarkerMissingOrFailure() {
        givenHead(NoSuchKeyException.builder().message("no").build());
        assertThat(service.findSaveMarker("k")).isEmpty();

        givenHead(S3Exception.builder().statusCode(404).message("not found").build());
        assertThat(service.findSaveMarker("k")).isEmpty();

        givenHead(S3Exception.builder().statusCode(500).message("boom").build());
        assertThatThrownBy(() -> service.findSaveMarker("k"))
                .isInstanceOfSatisfying(StorageException.class, e -> assertThat(e.getCode()).isEqualTo(StorageErrorCode.GET_FAILED));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("copyAndUpload — 버전 키 복사는 마커 없이, 원본 키 업로드에 마커를 남긴다")
    void copyAndUploadWritesMarkerOnUpload() {
        given(s3Client.copyObject(any(Consumer.class))).willReturn((CopyObjectResponse) CopyObjectResponse.builder().sdkHttpResponse(OK).build());
        given(s3Client.putObject(any(Consumer.class), any(RequestBody.class))).willReturn((PutObjectResponse) PutObjectResponse.builder().sdkHttpResponse(OK).build());

        service.copyAndUpload(new FileData("text/plain", new ByteArrayInputStream(new byte[3]), 3), "key", "versionKey", "42");

        ArgumentCaptor<Consumer<CopyObjectRequest.Builder>> copy = ArgumentCaptor.forClass(Consumer.class);
        verify(s3Client).copyObject(copy.capture());
        CopyObjectRequest.Builder copyReq = CopyObjectRequest.builder();
        copy.getValue().accept(copyReq);
        assertThat(copyReq.build().metadataDirective()).as("백업 복사는 기본(COPY)").isNull();

        ArgumentCaptor<Consumer<PutObjectRequest.Builder>> put = ArgumentCaptor.forClass(Consumer.class);
        verify(s3Client).putObject(put.capture(), any(RequestBody.class));
        PutObjectRequest.Builder putReq = PutObjectRequest.builder();
        put.getValue().accept(putReq);
        PutObjectRequest request = putReq.build();
        assertThat(request.key()).isEqualTo("key");
        assertThat(request.metadata()).containsEntry(StorageConstants.SAVE_MARKER_METADATA_KEY, "42");
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("backUpAndRestore — 복원 복사는 메타데이터 REPLACE + 마커, Content-Type 은 복원할 객체의 것을 유지")
    void backUpAndRestoreWritesMarkerOnRestore() {
        given(s3Client.copyObject(any(Consumer.class))).willReturn((CopyObjectResponse) CopyObjectResponse.builder().sdkHttpResponse(OK).build());
        givenHead(HeadObjectResponse.builder().contentType("application/pdf").contentLength(3L).sdkHttpResponse(OK).build());

        service.backUpAndRestore("key", "versionKey", "restoreKey", "42");

        ArgumentCaptor<Consumer<CopyObjectRequest.Builder>> copy = ArgumentCaptor.forClass(Consumer.class);
        verify(s3Client, times(2)).copyObject(copy.capture());
        List<CopyObjectRequest> requests = copy.getAllValues().stream().map(c -> {
            CopyObjectRequest.Builder b = CopyObjectRequest.builder();
            c.accept(b);
            return b.build();
        }).toList();

        assertThat(requests.get(0).sourceKey()).isEqualTo("key");
        assertThat(requests.get(0).destinationKey()).isEqualTo("versionKey");
        assertThat(requests.get(0).metadataDirective()).isNull();

        CopyObjectRequest restore = requests.get(1);
        assertThat(restore.sourceKey()).isEqualTo("restoreKey");
        assertThat(restore.destinationKey()).isEqualTo("key");
        assertThat(restore.metadataDirective()).isEqualTo(MetadataDirective.REPLACE);
        assertThat(restore.contentType()).isEqualTo("application/pdf");
        assertThat(restore.metadata()).containsEntry(StorageConstants.SAVE_MARKER_METADATA_KEY, "42");
    }
}

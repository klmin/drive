package com.thinkfree.drive.storage.service;

import com.thinkfree.drive.storage.exception.StorageErrorCode;
import com.thinkfree.drive.storage.exception.StorageException;
import com.thinkfree.drive.storage.properties.S3ConnectionProperty;
import com.thinkfree.drive.storage.properties.StorageProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.Instant;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/** findLastModified — 버전 만료 정리의 버전 키 사본 존재 확인용. 객체 없음(404)은 예외가 아니라 empty. */
@DisplayName("S3StorageService.findLastModified")
class S3StorageServiceLastModifiedTest {

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
    @DisplayName("객체가 있으면 Last-Modified 를 돌려준다")
    void returnsLastModified() {
        Instant at = Instant.parse("2026-10-12T01:02:03Z");
        givenHead(HeadObjectResponse.builder().lastModified(at).build());

        assertThat(service.findLastModified("k")).contains(at);
    }

    @Test
    @DisplayName("NoSuchKey·404 는 empty")
    void missingObjectIsEmpty() {
        givenHead(NoSuchKeyException.builder().message("no").build());
        assertThat(service.findLastModified("k")).isEmpty();

        givenHead(S3Exception.builder().statusCode(404).message("not found").build());
        assertThat(service.findLastModified("k")).isEmpty();
    }

    @Test
    @DisplayName("그 밖의 실패는 GET_FAILED")
    void otherFailureThrows() {
        givenHead(S3Exception.builder().statusCode(500).message("boom").build());

        assertThatThrownBy(() -> service.findLastModified("k"))
                .isInstanceOfSatisfying(StorageException.class, e -> assertThat(e.getCode()).isEqualTo(StorageErrorCode.GET_FAILED));
    }
}

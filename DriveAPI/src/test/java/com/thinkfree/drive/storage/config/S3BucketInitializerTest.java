package com.thinkfree.drive.storage.config;

import com.thinkfree.drive.storage.properties.S3ConnectionProperty;
import com.thinkfree.drive.storage.properties.StorageProperty;
import com.thinkfree.drive.upload.session.properties.UploadSessionProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortIncompleteMultipartUpload;
import software.amazon.awssdk.services.s3.model.ExpirationStatus;
import software.amazon.awssdk.services.s3.model.GetBucketLifecycleConfigurationResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.LifecycleRule;
import software.amazon.awssdk.services.s3.model.PutBucketLifecycleConfigurationRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("S3BucketInitializer — 미완성 멀티파트 lifecycle 규칙 병합")
class S3BucketInitializerTest {

    private final S3Client s3Client = Mockito.mock(S3Client.class);

    private S3BucketInitializer initializer(boolean manage) {
        var property = new UploadSessionProperty(Duration.ofHours(1), Duration.ofMinutes(30), Duration.ofDays(7), Duration.ofHours(24),
                DataSize.ofMegabytes(16), DataSize.ofMegabytes(32), DataSize.ofTerabytes(5), 4, 50, 500, manage, 1);
        return new S3BucketInitializer(s3Client, new S3ConnectionProperty("http://x", "us-east-1", "ak", "sk", "bucket"),
                Mockito.mock(StorageProperty.class), property);
    }

    @SuppressWarnings("unchecked")
    private void givenRules(List<LifecycleRule> rules) {
        given(s3Client.headBucket(any(Consumer.class))).willReturn(HeadBucketResponse.builder().build());
        given(s3Client.getBucketLifecycleConfiguration(any(Consumer.class)))
                .willReturn(GetBucketLifecycleConfigurationResponse.builder().rules(rules).build());
    }

    @SuppressWarnings("unchecked")
    private List<LifecycleRule> putRules() {
        ArgumentCaptor<Consumer<PutBucketLifecycleConfigurationRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(s3Client).putBucketLifecycleConfiguration(captor.capture());
        var builder = PutBucketLifecycleConfigurationRequest.builder();
        captor.getValue().accept(builder);
        return builder.build().lifecycleConfiguration().rules();
    }

    private static LifecycleRule rule(String id, int abortDays) {
        return LifecycleRule.builder().id(id).status(ExpirationStatus.ENABLED)
                            .abortIncompleteMultipartUpload(AbortIncompleteMultipartUpload.builder().daysAfterInitiation(abortDays).build())
                            .build();
    }

    @Test
    @DisplayName("기존 규칙을 유지하고 우리 규칙을 더한다")
    void addsRuleKeepingExisting() {
        LifecycleRule ops = LifecycleRule.builder().id("ops").status(ExpirationStatus.ENABLED).expiration(e -> e.days(30)).build();
        givenRules(List.of(ops));

        initializer(true).init();

        assertThat(putRules()).extracting(LifecycleRule::id).containsExactly("ops", S3BucketInitializer.ABORT_RULE_ID);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("같은 규칙이 이미 있으면 PUT 하지 않는다")
    void idempotent() {
        givenRules(List.of(rule(S3BucketInitializer.ABORT_RULE_ID, 1)));

        initializer(true).init();

        verify(s3Client, never()).putBucketLifecycleConfiguration(any(Consumer.class));
    }

    @Test
    @DisplayName("일수가 다르면 우리 규칙만 교체(중복 없이)")
    void replacesOutdatedRule() {
        givenRules(List.of(rule(S3BucketInitializer.ABORT_RULE_ID, 7)));

        initializer(true).init();

        assertThat(putRules()).singleElement().satisfies(r -> assertThat(r.abortIncompleteMultipartUpload().daysAfterInitiation()).isEqualTo(1));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("lifecycle 이 없으면(404) 우리 규칙만, PUT 실패(미지원·권한)는 기동을 막지 않는다")
    void noConfigAndFailureTolerated() {
        given(s3Client.headBucket(any(Consumer.class))).willReturn(HeadBucketResponse.builder().build());
        given(s3Client.getBucketLifecycleConfiguration(any(Consumer.class)))
                .willThrow(S3Exception.builder().statusCode(404).message("NoSuchLifecycleConfiguration").build());
        given(s3Client.putBucketLifecycleConfiguration(any(Consumer.class)))
                .willThrow(S3Exception.builder().statusCode(400).message("MalformedXML").build());

        assertThatCode(() -> initializer(true).init()).doesNotThrowAnyException();
        assertThat(putRules()).extracting(LifecycleRule::id).containsExactly(S3BucketInitializer.ABORT_RULE_ID);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("manageBucketLifecycle=false 면 lifecycle 을 건드리지 않는다")
    void disabled() {
        given(s3Client.headBucket(any(Consumer.class))).willReturn(HeadBucketResponse.builder().build());

        initializer(false).init();

        verify(s3Client, never()).getBucketLifecycleConfiguration(any(Consumer.class));
    }
}

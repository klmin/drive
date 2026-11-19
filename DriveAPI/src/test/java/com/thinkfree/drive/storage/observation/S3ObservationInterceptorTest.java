package com.thinkfree.drive.storage.observation;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.ExecutableHttpRequest;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("S3ObservationInterceptor — S3 호출을 요청 트레이스의 자식 구간으로, 404 는 오류 아님")
class S3ObservationInterceptorTest {

    private final List<Observation.Context> stopped = new CopyOnWriteArrayList<>();
    private ObservationRegistry registry;
    private int nextStatus;
    private S3Client s3;

    @BeforeEach
    void setUp() {
        registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationHandler<>() {
            @Override
            public void onStop(Observation.Context context) {
                stopped.add(context);
            }

            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }
        });
        SdkHttpClient http = new SdkHttpClient() {
            @Override
            public ExecutableHttpRequest prepareRequest(HttpExecuteRequest request) {
                return new ExecutableHttpRequest() {
                    @Override
                    public HttpExecuteResponse call() {
                        return HttpExecuteResponse.builder()
                                .response(SdkHttpResponse.builder().statusCode(nextStatus).build())
                                .responseBody(AbortableInputStream.create(new ByteArrayInputStream(new byte[0])))
                                .build();
                    }

                    @Override
                    public void abort() {
                    }
                };
            }

            @Override
            public void close() {
            }
        };
        s3 = S3Client.builder()
                .httpClient(http)
                .endpointOverride(URI.create("http://storage.test"))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("ak", "sk")))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .overrideConfiguration(o -> o.addExecutionInterceptor(new S3ObservationInterceptor(() -> registry))
                        .retryStrategy(b -> b.maxAttempts(1)))
                .build();
    }

    @AfterEach
    void tearDown() {
        s3.close();
    }

    @Test
    @DisplayName("요청 관측 안에서 부르면 그 자식 구간 s3 headObject 가 생기고 버킷·상태코드가 붙는다")
    void childOfCurrentObservation() {
        nextStatus = 200;
        Observation request = Observation.start("request", registry);
        try (Observation.Scope ignored = request.openScope()) {
            s3.headObject(r -> r.bucket("drive-bucket").key("a/b.docx"));
        } finally {
            request.stop();
        }

        Observation.Context s3Call = stopped.stream()
                .filter(c -> S3ObservationInterceptor.OBSERVATION_NAME.equals(c.getName())).findFirst().orElseThrow();
        assertThat(s3Call.getContextualName()).isEqualTo("s3 headObject");
        assertThat(s3Call.getLowCardinalityKeyValue("rpc.method").getValue()).isEqualTo("HeadObject");
        assertThat(s3Call.getParentObservation()).isSameAs(request);
        assertThat(s3Call.getLowCardinalityKeyValue("aws.s3.bucket").getValue()).isEqualTo("drive-bucket");
        assertThat(s3Call.getLowCardinalityKeyValue("http.status_code").getValue()).isEqualTo("200");
        assertThat(s3Call.getError()).isNull();
        assertThat(s3Call.getAllKeyValues().stream().map(kv -> kv.getValue()))
                .as("객체 키는 남기지 않는다").doesNotContain("a/b.docx");
    }

    @Test
    @DisplayName("404(객체 없음)는 오류로 표시하지 않는다")
    void notFoundIsNotAnError() {
        nextStatus = 404;
        assertThatThrownBy(() -> s3.headObject(r -> r.bucket("drive-bucket").key("missing")))
                .isInstanceOf(S3Exception.class);

        Observation.Context s3Call = stopped.get(0);
        assertThat(s3Call.getLowCardinalityKeyValue("http.status_code").getValue()).isEqualTo("404");
        assertThat(s3Call.getError()).isNull();
    }

    @Test
    @DisplayName("5xx 는 오류로 표시한다 — tail 샘플링이 트레이스를 남긴다")
    void serverErrorIsAnError() {
        nextStatus = 500;
        assertThatThrownBy(() -> s3.headObject(r -> r.bucket("drive-bucket").key("k")))
                .isInstanceOf(S3Exception.class);

        Observation.Context s3Call = stopped.get(0);
        assertThat(s3Call.getLowCardinalityKeyValue("http.status_code").getValue()).isEqualTo("500");
        assertThat(s3Call.getError()).isInstanceOf(S3Exception.class);
    }
}

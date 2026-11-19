package com.thinkfree.drive.storage.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;
import software.amazon.awssdk.http.SdkHttpResponse;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * S3 API 호출(PutObject·GetObject·HeadObject·DeleteObject…)을 Micrometer 관측으로 남긴다.
 * <ul>
 *   <li>트레이스: 요청 트레이스의 자식 구간 {@code s3 put-object} 등 — 재시도를 포함한 API 호출 1건이 구간 1개</li>
 *   <li>메트릭: {@code aws.s3.client} 타이머(operation·bucket·status·error 태그) — Prometheus {@code aws_s3_client_seconds_*}</li>
 * </ul>
 * 동기·비동기 클라이언트 모두 호출 스레드에서 {@link #beforeExecution} 이 불리므로 그때의 현재 관측이 부모가 된다.
 * 비동기 클라이언트는 응답 스레드에서 끝나므로 관측을 스코프로 열지 않고 실행 속성에 들고 다닌다.
 * <p>
 * 객체 키는 남기지 않는다(경로에 사용자·문서 식별 정보가 들어갈 수 있다).
 * 404 는 이 서비스에서 "객체 없음" 정상 결과로 쓰이므로(존재 확인 HeadObject) 오류로 표시하지 않는다 — tail 샘플링이 오류 트레이스로 모두 남기지 않게.
 * GetObject 는 응답 헤더를 받은 시점에 끝난다(본문 스트림 읽기 시간은 호출한 요청 구간에 포함된다).
 */
public class S3ObservationInterceptor implements ExecutionInterceptor {

    public static final String OBSERVATION_NAME = "aws.s3.client";

    private static final ExecutionAttribute<Observation> OBSERVATION = new ExecutionAttribute<>("drive.s3.observation");
    private static final int NOT_FOUND = 404;

    private final Supplier<ObservationRegistry> registry;

    public S3ObservationInterceptor(Supplier<ObservationRegistry> registry) {
        this.registry = registry;
    }

    @Override
    public void beforeExecution(Context.BeforeExecution context, ExecutionAttributes attributes) {
        String operation = Optional.ofNullable(attributes.getAttribute(SdkExecutionAttribute.OPERATION_NAME)).orElse("unknown");
        String service = Optional.ofNullable(attributes.getAttribute(SdkExecutionAttribute.SERVICE_NAME)).orElse("S3");
        Observation observation = Observation.createNotStarted(OBSERVATION_NAME, registry.get())
                // 트레이싱이 구간 이름을 소문자-하이픈으로 바꾼다(PutObject → put-object) — 앞 글자를 소문자로 둬 "s3 put-object" 가 되게
                .contextualName(service.toLowerCase() + " " + lowerFirst(operation))
                .lowCardinalityKeyValue("rpc.system", "aws-api")
                .lowCardinalityKeyValue("rpc.service", service)
                .lowCardinalityKeyValue("rpc.method", operation)
                .lowCardinalityKeyValue("aws.s3.bucket", bucket(context.request()));
        attributes.putAttribute(OBSERVATION, observation.start());
    }

    @Override
    public void afterExecution(Context.AfterExecution context, ExecutionAttributes attributes) {
        Observation observation = attributes.getAttribute(OBSERVATION);
        if (observation == null) {
            return;
        }
        observation.lowCardinalityKeyValue(status(context.httpResponse()));
        observation.stop();
    }

    @Override
    public void onExecutionFailure(Context.FailedExecution context, ExecutionAttributes attributes) {
        Observation observation = attributes.getAttribute(OBSERVATION);
        if (observation == null) {
            return;
        }
        Throwable failure = context.exception();
        int statusCode = failure instanceof SdkServiceException service ? service.statusCode() : 0;
        observation.lowCardinalityKeyValue(statusCode > 0
                ? KeyValue.of("http.status_code", String.valueOf(statusCode))
                : context.httpResponse().map(S3ObservationInterceptor::status).orElse(KeyValue.of("http.status_code", "none")));
        if (statusCode != NOT_FOUND) {
            observation.error(failure);
        }
        observation.stop();
    }

    private static KeyValue status(SdkHttpResponse response) {
        return KeyValue.of("http.status_code", String.valueOf(response.statusCode()));
    }

    private static String lowerFirst(String value) {
        return value.isEmpty() ? value : Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private static String bucket(SdkRequest request) {
        return request.getValueForField("Bucket", String.class).orElse("none");
    }
}

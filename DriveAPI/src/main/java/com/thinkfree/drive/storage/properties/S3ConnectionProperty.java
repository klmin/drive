package com.thinkfree.drive.storage.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * S3 프로토콜 스토리지 접속 정보({@code storage.connection.*}).
 * Ceph RGW, RustFS, AWS S3 모두 이 하나로 붙으며, 스토리지 종류({@code storage.type})와 무관하다.
 * <ul>
 *   <li>endpoint 지정: Ceph RGW, RustFS 등 S3 호환 서버. path-style 접속, region은 SDK 형식값</li>
 *   <li>endpoint 없음: AWS S3. region 필수</li>
 * </ul>
 *
 * @param endpoint   S3 호환 엔드포인트(없으면 AWS S3)
 * @param region     AWS 리전(endpoint 지정형이면 생략 가능)
 */
@ConfigurationProperties("storage.connection")
public record S3ConnectionProperty
(
    String endpoint,
    String region,
    String accessKey,
    String secretKey,
    String bucketName
)
{
    /** 환경변수 미설정으로 빈 문자열이 들어오는 경우를 null로 정규화한다. */
    public S3ConnectionProperty {
        endpoint = StringUtils.hasText(endpoint) ? endpoint.trim() : null;
        region = StringUtils.hasText(region) ? region.trim() : null;
    }

    /** 엔드포인트 지정형(Ceph RGW, RustFS 등) 접속인지 여부 */
    public boolean isEndpointBased() {
        return endpoint != null;
    }
}

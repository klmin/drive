package com.thinkfree.drive.application.file.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * 파일 내용 상한·인코딩 감지 설정 — drive_read_file/drive_download_file/drive_upload_file이 사용한다.
 * 읽기·다운로드 기본값은 구글 드라이브 커넥터 실측 동형(다운로드 10MB 하드 리밋). application.yml에서 조정한다.
 */
@ConfigurationProperties("file.content")
public record FileContentProperty
(
    @DefaultValue("10MB")
    DataSize readMaxSize,

    @DefaultValue("50000")
    int readMaxChars,

    @DefaultValue("10MB")
    DataSize downloadMaxSize,

    // drive_upload_file의 파일 내용 상한 — 텍스트·base64 디코딩 후 바이트 기준 단일 게이트.
    // 512KB 근거: 모델 최대 출력(128K 토큰)으로 도달 가능한 상한(텍스트 ≈380~580KB, base64 원본 ≈190~280KB)과 같은 선이라
    // 정상 흐름에서는 발동하지 않는 서버 보호용 안전판이다. 문자 수 게이트는 두지 않는다(모델 출력이 먼저 잘림).
    @DefaultValue("512KB")
    DataSize writeMaxSize,

    // 평문 인코딩 감지(ICU) 신뢰도 문턱 — 이 점수 미만의 추측은 버리고 MS949 기본값으로 간다.
    // 실측 근거: 오판(짧은 한국어→KOI8-R/Big5)은 10~33점, 정답(Shift_JIS·GB18030 등)은 91~100점
    @DefaultValue("80")
    int charsetConfidenceThreshold
)
{
}

package com.thinkfree.drive.application.file;

import com.ibm.icu.text.CharsetDetector;
import com.ibm.icu.text.CharsetMatch;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * 평문 파일(TEXT_EXTENSIONS) 디코딩 3단 — 다국어 텍스트 대응.
 *
 * ① UTF-8 엄격 검증(REPORT): 추측이 아니라 판정이라 오판이 없다 — 현대 파일 대부분 여기서 끝.
 * ② ICU 통계 감지: UTF-8이 아닌 레거시(Shift_JIS·GBK·ISO-8859·UTF-16 등)를 추측하되,
 *    신뢰도 문턱 미만이면 버린다 — 짧은 한국어 MS949를 KOI8-R(33점)·Big5(10점)로 오판하는
 *    케이스가 실측돼 문턱이 필수다(정답 추측은 91~100점 실측).
 * ③ MS949 기본값: 추측을 믿을 수 없으면 주 사용자층(국내 레거시) 기준으로 해석
 *    (매핑 불가 바이트는 대체 문자 — 어떤 입력에도 예외 없이 문자열을 반환한다).
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PlainTextDecoder {

    private static final Charset DEFAULT_LEGACY = Charset.forName("MS949");

    public static String decode(byte[] bytes, int confidenceThreshold) {
        try {
            var decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return stripBom(decoded);
        } catch (CharacterCodingException e) {
            return stripBom(detectOrDefault(bytes, confidenceThreshold));
        }
    }

    private static String detectOrDefault(byte[] bytes, int confidenceThreshold) {
        CharsetDetector detector = new CharsetDetector();
        detector.setText(bytes);
        CharsetMatch match = detector.detect();
        if (match != null && match.getConfidence() >= confidenceThreshold) {
            try {
                return new String(bytes, match.getName());
            } catch (UnsupportedEncodingException e) {
                log.info("ICU 감지 인코딩을 JVM이 지원하지 않음 — 기본값으로 폴백: {}", match.getName());
            }
        }
        return new String(bytes, DEFAULT_LEGACY);
    }

    private static String stripBom(String text) {
        return !text.isEmpty() && text.charAt(0) == '\uFEFF' ? text.substring(1) : text;
    }
}

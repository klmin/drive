package com.thinkfree.drive.application.file;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 평문 디코딩 3단(UTF-8 검증 → ICU 신뢰도 문턱 → MS949 기본값) 회귀 테스트.
 * 케이스는 2026-08-13 인코딩별 실측(현재 방식 vs ICU 단독 비교 실험)에서 그대로 가져왔다 —
 * 특히 "짧은 한국어 MS949"는 ICU 단독이 KOI8-R(33점)·Big5(10점)로 오판했던 케이스로,
 * 신뢰도 문턱이 이를 걸러 MS949 기본값으로 보내는 것이 이 설계의 핵심이다.
 */
class PlainTextDecoderTest {

    private static final int THRESHOLD = 80; // FileContentProperty.charsetConfidenceThreshold 기본값과 동일

    @Test
    @DisplayName("UTF-8 한국어 — 1차 검증에서 그대로 통과")
    void utf8Korean() throws UnsupportedEncodingException {
        assertDecodes("연말정산 간소화 서비스는 국세청 홈택스에서 제공합니다.", "UTF-8");
    }

    @Test
    @DisplayName("MS949 한국어 장문 — ICU가 EUC-KR 고신뢰로 감지")
    void ms949KoreanLong() throws UnsupportedEncodingException {
        assertDecodes("연말정산 간소화 서비스는 국세청 홈택스에서 제공합니다. 부양가족 자료제공 동의를 신청하세요.", "MS949");
    }

    @Test
    @DisplayName("MS949 한국어 단문 — ICU 오판(KOI8-R 33점)을 문턱이 걸러 MS949 기본값으로")
    void ms949KoreanShort() throws UnsupportedEncodingException {
        assertDecodes("안녕하세요", "MS949");
    }

    @Test
    @DisplayName("MS949 한국어+숫자 혼합 단문 — ICU 오판(Big5 10점)을 문턱이 걸러 MS949 기본값으로")
    void ms949KoreanMixedShort() throws UnsupportedEncodingException {
        assertDecodes("가격 15,000원 (부가세 포함)", "MS949");
    }

    @Test
    @DisplayName("Shift_JIS 일본어 — ICU 고신뢰 감지 (구 방식에서는 깨졌던 케이스)")
    void shiftJisJapanese() throws UnsupportedEncodingException {
        assertDecodes("年末調整の簡素化サービスは国税庁ホームページで提供されます。ご確認ください。", "Shift_JIS");
    }

    @Test
    @DisplayName("GBK 중국어 — ICU 고신뢰 감지 (구 방식에서는 깨졌던 케이스)")
    void gbkChinese() throws UnsupportedEncodingException {
        assertDecodes("年终结算简化服务由国家税务总局网站提供，请确认相关资料。", "GBK");
    }

    @Test
    @DisplayName("ASCII 영어 — 1차 UTF-8 검증에서 통과")
    void asciiEnglish() throws UnsupportedEncodingException {
        assertDecodes("The quick brown fox jumps over the lazy dog.", "US-ASCII");
    }

    @Test
    @DisplayName("ISO-8859-1 독일어 — ICU 고신뢰 감지 (구 방식에서는 � 치환됐던 케이스)")
    void latin1German() throws UnsupportedEncodingException {
        assertDecodes("Die jährliche Abrechnung erfolgt über das Steuerportal. Prüfen Sie die Unterlagen.", "ISO-8859-1");
    }

    @Test
    @DisplayName("UTF-8 BOM — 제거 후 반환")
    void utf8Bom() {
        byte[] withBom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'a', 'b'};
        assertThat(PlainTextDecoder.decode(withBom, THRESHOLD)).isEqualTo("ab");
    }

    @Test
    @DisplayName("빈 파일 — 빈 문자열")
    void empty() {
        assertThat(PlainTextDecoder.decode(new byte[0], THRESHOLD)).isEmpty();
    }

    private void assertDecodes(String original, String encoding) throws UnsupportedEncodingException {
        byte[] bytes = original.getBytes(encoding);
        assertThat(PlainTextDecoder.decode(bytes, THRESHOLD)).isEqualTo(original);
    }
}

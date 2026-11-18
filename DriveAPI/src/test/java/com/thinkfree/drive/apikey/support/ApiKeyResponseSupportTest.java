package com.thinkfree.drive.apikey.support;

import com.thinkfree.drive.util.MaskingUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiKeyResponseSupport.maskApiKey — 목록 키 값 마스킹")
class ApiKeyResponseSupportTest {

    @Test
    @DisplayName("기본 — 키 길이와 무관한 고정 길이 '*'")
    void masksFixedLength() {
        assertThat(ApiKeyResponseSupport.maskApiKey("tfd_abcd1234", false)).isEqualTo("*".repeat(20));
        assertThat(ApiKeyResponseSupport.maskApiKey("tfd_" + "a".repeat(60), false)).isEqualTo("*".repeat(20));
    }

    @Test
    @DisplayName("hide — 블러용 값")
    void hides() {
        assertThat(ApiKeyResponseSupport.maskApiKey("tfd_abcd1234", true)).isEqualTo(MaskingUtils.MASKED_PLACEHOLDER);
    }

    @Test
    @DisplayName("값 없음 — null")
    void nullKey() {
        assertThat(ApiKeyResponseSupport.maskApiKey(null, false)).isNull();
        assertThat(ApiKeyResponseSupport.maskApiKey(null, true)).isNull();
    }
}

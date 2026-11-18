package com.thinkfree.drive.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class IntellectRequestUtilTest {

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @DisplayName("Referer 경로가 /intellect 인 경우만 인텔릭트 요청으로 판별")
    @CsvSource(nullValues = "NULL", value = {
            "https://drive.example.com/intellect?rs=1&ss=2&mode=ro, true",
            "https://drive.example.com/intellect/?rs=1&ss=2, true",
            "https://drive.example.com/intellect, true",
            "https://drive.example.com/drive/?pfs=10, false",
            "https://drive.example.com/intellectual, false",
            "https://drive.example.com/, false",
            "https://drive.example.com, false",
            "not a uri with spaces, false",
            "'', false",
            "NULL, false"
    })
    void isFromIntellect(String referer, boolean expected) {
        var request = new MockHttpServletRequest();
        if (referer != null) {
            request.addHeader(HttpHeaders.REFERER, referer);
        }

        assertThat(IntellectRequestUtil.isFromIntellect(request)).isEqualTo(expected);
    }
}

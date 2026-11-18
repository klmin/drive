package com.thinkfree.drive.application.resource;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.resource.exception.ResourceErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("이름 변경 이름 규칙 단위테스트")
class ResourceRenameNameRuleTest {

    @ParameterizedTest(name = "[{index}] 기존 확장자 ''{1}'' + 요청 ''{0}'' → ''{2}''")
    @CsvSource(delimiter = '|', value = {
            "회의록.docx       | docx | 회의록.docx",
            "회의록 v1.2.docx  | docx | 회의록 v1.2.docx",
            "a.txt.txt         | txt  | a.txt.txt",
            "b.docx            | DOCX | b.DOCX",
            "b                 |      | b",
            "changed.txt       | note | changed.txt.note",
            "changed           | note | changed.note",
            "backup.tar.gz     | gz   | backup.tar.gz",
            "보고서_최종.HWP    | hwp  | 보고서_최종.hwp",
            "README.md         |      | README.md",
    })
    @DisplayName("기존 확장자 유지")
    void keepOriginalExtension(String requestedName, String originalExt, String expected) {
        assertThat(ResourceUseCase.keepOriginalExtension(requestedName, originalExt)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {".note", ".NOTE"})
    @DisplayName("확장자만 있는 이름은 거절")
    void rejectExtensionOnly(String requestedName) {
        assertThatThrownBy(() -> ResourceUseCase.keepOriginalExtension(requestedName, "note"))
                .isInstanceOf(ApiRuntimeException.class)
                .extracting("code").isEqualTo(ResourceErrorCode.INVALID_NAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"trim me .note", "con.note", "LPT1.note"})
    @DisplayName("확장자를 뺀 이름의 끝 공백·예약어 거절")
    void rejectInvalidBaseName(String requestedName) {
        assertThatThrownBy(() -> ResourceUseCase.keepOriginalExtension(requestedName, "note"))
                .isInstanceOf(ApiRuntimeException.class)
                .extracting("code").isEqualTo(ResourceErrorCode.INVALID_NAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"changed.", " changed", "changed ", "changed.note ", "changed\t", "   ", "   .tfi",
            ".", "..", ".hidden", "trail ",
            "x/y\\z:*?\"<>|", "a\\b/c:d*e?f\"g<h>i|j.tfi", "a/b", "a\\b", "a:b", "a*b", "a?b", "a\"b", "a<b", "a>b", "a|b",
            "CON", "prn", "Aux", "NUL", "COM1", "com9", "LPT1", "lpt9"})
    @DisplayName("앞뒤 공백·마침표로 시작·끝·금지 문자·예약어 거절")
    void rejectInvalidName(String requestedName) {
        assertThatThrownBy(() -> ResourceUseCase.validateRenameName(requestedName))
                .isInstanceOf(ApiRuntimeException.class)
                .extracting("code").isEqualTo(ResourceErrorCode.INVALID_NAME);
    }

    @ParameterizedTest
    @ValueSource(ints = {256, 300})
    @DisplayName("255자 초과 거절")
    void rejectTooLongName(int length) {
        assertThatThrownBy(() -> ResourceUseCase.validateRenameName("a".repeat(length)))
                .isInstanceOf(ApiRuntimeException.class)
                .extracting("code").isEqualTo(ResourceErrorCode.INVALID_NAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"changed", "changed.note", "a..b", "회의록 v1.2", "CON1", "COM10", "LPT0", "CON.note", "a b"})
    @DisplayName("정상 이름 통과")
    void acceptValidName(String requestedName) {
        assertThatNoException().isThrownBy(() -> ResourceUseCase.validateRenameName(requestedName));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 255})
    @DisplayName("255자 이하 통과")
    void acceptMaxLengthName(int length) {
        assertThatNoException().isThrownBy(() -> ResourceUseCase.validateRenameName("a".repeat(length)));
    }
}

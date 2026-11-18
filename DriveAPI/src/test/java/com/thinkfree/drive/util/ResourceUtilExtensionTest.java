package com.thinkfree.drive.util;

import com.thinkfree.drive.resource.result.ResourceRenameResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("확장자 기준 이름 분해 단위테스트")
class ResourceUtilExtensionTest {

    @ParameterizedTest(name = "[{index}] ''{0}'' + ext ''{1}'' → ''{2}''")
    @CsvSource(delimiter = '|', value = {
            "report.docx  | docx | report",
            "UP.DOCX      | DOCX | UP",
            "a.txt        | ''   | a.txt",
            "README       | ''   | README",
            "a.txt.note   | note | a.txt",
            "v1.2.docx    |      | v1.2",
    })
    @DisplayName("확장자를 뺀 이름은 ext 기준(빈 값 = 이름 전체, null = 마지막 점 기준)")
    void nameWithoutExtension(String name, String ext, String expected) {
        assertThat(ResourceUtil.nameWithoutExtension(name, ext)).isEqualTo(expected);
    }

    @Test
    @DisplayName("확장자 없는 파일의 a.txt 는 이름 전체·ext 빈 값으로 분해")
    void extractWithEmptyExt() {
        var result = ResourceUtil.extract("a.txt의 사본", "");

        assertThat(result.ext()).isEmpty();
        assertThat(result.originalFileNameWithoutExt()).isEqualTo("a.txt의 사본");
    }

    @Test
    @DisplayName("이름 변경 결과 name 은 ext 기준")
    void renameResultUsesExt() {
        assertThat(ResourceRenameResult.of("a.txt", "").name()).isEqualTo("a.txt");
        assertThat(ResourceRenameResult.of("a.txt", "txt").name()).isEqualTo("a");
    }
}

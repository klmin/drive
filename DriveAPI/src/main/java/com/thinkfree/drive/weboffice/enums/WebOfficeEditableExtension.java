package com.thinkfree.drive.weboffice.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * TFO 웹오피스에서 편집 가능한 파일 확장자.
 * 프런트 constants/FILES.js의 TFO_EXTENSION_APPS에서 열람 전용(TFO_VIEW_ONLY_EXTENSIONS: pdf)을
 * 제외한 목록과 동일해야 한다 — TFO 지원 확장자가 바뀌면 양쪽을 함께 수정한다.
 */
public enum WebOfficeEditableExtension {
    // 한워드
    DOC, DOCX, ODT,
    // 한셀
    XLS, XLSX, CELL, ODS, CSV,
    // 한쇼
    PPT, PPTX, SHOW, ODP;

    private static final Set<String> EXTENSIONS = Arrays.stream(values())
            .map(e -> e.name().toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());

    public static boolean contains(String extension) {
        return extension != null && EXTENSIONS.contains(extension.trim().toLowerCase(Locale.ROOT));
    }
}

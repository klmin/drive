package com.thinkfree.drive.common.search.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum FileType {
    DOCUMENT(List.of("doc", "docx", "hwp", "hwpx", "txt", "rtf", "odt", "md", "pages")),
    SPREADSHEET(List.of("xls", "xlsx", "csv", "tsv", "ods", "numbers")),
    PRESENTATION(List.of("ppt", "pptx", "odp", "key")),
    PDF(List.of("pdf")),
    IMAGE(List.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "tiff", "tif", "heic")),
    NOTE(List.of("tfi"))
    ;
    
    private final List<String> extensions;
    
    @JsonCreator
    public static FileType from(String value) {
        return value == null ? null : valueOf(value.toUpperCase());
    }
    
}

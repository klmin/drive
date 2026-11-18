package com.thinkfree.drive.weboffice.enums;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/** 어댑터가 문서 정보를 어떤 동작 때문에 요청했는지 나타내는 info 요청 유형. */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum WebOfficeInfoType {

    OPEN("문서 열기", "open"),
    DOWNLOAD("원본 다운로드", "download"),
    PDF_DOWNLOAD("PDF 변환 다운로드", "pdf_download"),
    PRINT("인쇄", "print")
    ;

    private final String description;
    private final String adapterParam;

    private static final Map<String, WebOfficeInfoType> LOOKUP_BY_ADAPTER_PARAM;

    static {
        LOOKUP_BY_ADAPTER_PARAM = new HashMap<>();
        for (WebOfficeInfoType type : WebOfficeInfoType.values()) {
            LOOKUP_BY_ADAPTER_PARAM.put(type.getAdapterParam(), type);
        }
    }

    public static WebOfficeInfoType findByAdapterParam(String adapterParam) {
        return adapterParam == null ? null : LOOKUP_BY_ADAPTER_PARAM.get(adapterParam.toLowerCase());
    }
}

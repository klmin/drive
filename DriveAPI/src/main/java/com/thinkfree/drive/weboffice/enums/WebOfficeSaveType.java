package com.thinkfree.drive.weboffice.enums;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum WebOfficeSaveType {
    
    SAVE("파일 저장 시", "save"),
    SAVE_AS("다른 이름으로 저장", "saveas"),
    AUTO_SAVE("문서 편집 상태에서 스케쥴링에 의해 주기적으로 자동저장 될 때", "autosave"),
    AUTO_PUT("문서 편집 세션이 비정상 종료되었을 때", "autoput"),
    CLOSE("문서가 닫힐 때 편집 내용이 저장될 때", "close")
    ;
    
    private final String description;
    private final String adapterParam;
    
    private static final Map<String, WebOfficeSaveType> LOOKUP_BY_ADAPTER_PARAM;
    
    static {
        LOOKUP_BY_ADAPTER_PARAM = new HashMap<>();
        for (WebOfficeSaveType type : WebOfficeSaveType.values()) {
            LOOKUP_BY_ADAPTER_PARAM.put(type.getAdapterParam(), type);
        }
    }
    
    public static WebOfficeSaveType findByAdapterParam(String adapterParam) {
        return adapterParam == null ? null : LOOKUP_BY_ADAPTER_PARAM.get(adapterParam.toLowerCase());
    }
    public boolean shouldSaveWithVersioning() {
        return this == AUTO_PUT || this == CLOSE || this == SAVE;
    }
}

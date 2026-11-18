package com.thinkfree.drive.csv.data;

import lombok.With;

import java.util.Map;

public record CsvParseData<T>
(

    T data,
    Map<String, String> raw,
    @With
    String failureReason
    
)
{
    
    public boolean isSuccess() {
        return failureReason == null;
    }
    public String rawValue(String header) {
        return raw == null ? "" : raw.getOrDefault(header, "");
    }
}

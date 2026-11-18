package com.thinkfree.drive.csv.result;

import com.thinkfree.drive.csv.data.CsvParseData;
import com.thinkfree.drive.csv.exception.CsvErrorCode;

import java.util.List;

public record CsvParseResult<T>
(
    List<String> headers,
    List<CsvParseData<T>> dataList,
    CsvErrorCode globalError
)
{
    
    public static <T> CsvParseResult<T> success(List<String> headers, List<CsvParseData<T>> dataList){
        return new CsvParseResult<>(headers, dataList, null);
    }
    public static <T> CsvParseResult<T> globalFail(CsvErrorCode globalError){
        return new CsvParseResult<>(List.of(), List.of(), globalError);
    }
    
    public boolean allSuccess() {
        return dataList.stream().allMatch(CsvParseData::isSuccess);
    }
    
    public boolean isGlobalFail(){
        return globalError != null;
    }
    
    public List<T> successes() {
        return dataList.stream().filter(CsvParseData::isSuccess).map(CsvParseData::data).toList();
    }
    
    public List<CsvParseData<T>> failures() {
        return dataList.stream().filter(d -> !d.isSuccess()).toList();
    }
    
}

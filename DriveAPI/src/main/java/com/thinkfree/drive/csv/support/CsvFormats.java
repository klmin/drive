package com.thinkfree.drive.csv.support;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.csv.CSVFormat;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CsvFormats {
    
    public static CSVFormat readerForUserImport() {
        return CSVFormat.DEFAULT.builder()
                        .setTrim(true)
                        .setIgnoreEmptyLines(true)
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .get();
    }
    
    public static CSVFormat writerWithHeaders(List<String> headers){
        return CSVFormat.DEFAULT.builder().setHeader(headers.toArray(String[]::new)).get();
    }
    public static CSVFormat writerNoHeader() {
        return CSVFormat.DEFAULT.builder().get();
    }
}

package com.thinkfree.drive.csv.support;

import com.thinkfree.drive.csv.constans.CsvConstants;
import com.thinkfree.drive.csv.data.CsvParseData;
import com.thinkfree.drive.csv.exception.CsvErrorCode;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVPrinter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class CsvWriter {
    
    private static final byte[] UTF8_BOM = new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF};
    
    public static byte[] write(String message) {
        
        try (var out = new ByteArrayOutputStream()) {
            
            out.write(UTF8_BOM);
            
            try(var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                var printer = new CSVPrinter(writer, CsvFormats.writerNoHeader())) {
                printer.printRecord(message);
            }
            
            return out.toByteArray();
        } catch (IOException e) {
            log.error("[CsvWriter Global Error write Exception] message : {}", message, e);
            throw new ApiRuntimeException(CsvErrorCode.UNKNOWN_ERROR);
        }
    }
    
    public static <T> byte[] write(List<String> headers, List<CsvParseData<T>> failures) {
        
        List<String> headerOut = new ArrayList<>(headers.size() + 1);
        headerOut.addAll(headers);
        headerOut.add(CsvConstants.FAILURE_REASON);
        
        try (var out = new ByteArrayOutputStream()) {
            
            out.write(UTF8_BOM);
            
            try (var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                 var printer = new CSVPrinter(writer, CsvFormats.writerWithHeaders(headerOut))) {

                for (var row : failures) {
                    
                    List<String> line = new ArrayList<>(headerOut.size());
                    
                    for (String h : headers) {
                        line.add(row.rawValue(h));
                    }
                    
                    line.add(Objects.toString(row.failureReason(), ""));
                    printer.printRecord(line);
                }
            }
            
            return out.toByteArray();
        } catch (IOException e) {
            log.error("[CsvWriter Error write Exception] headers : {}, failures : {}", headers, failures.size(), e);
            throw new ApiRuntimeException(CsvErrorCode.UNKNOWN_ERROR);
        }
    }
    
}

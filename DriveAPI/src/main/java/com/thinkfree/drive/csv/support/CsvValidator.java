package com.thinkfree.drive.csv.support;

import com.thinkfree.drive.csv.constans.CsvConstants;
import com.thinkfree.drive.csv.exception.CsvErrorCode;
import com.thinkfree.drive.csv.exception.CsvException;
import com.thinkfree.drive.csv.model.CsvClassMeta;
import com.thinkfree.drive.csv.model.CsvFieldMeta;
import com.thinkfree.drive.csv.properties.CsvProperty;
import com.thinkfree.drive.csv.registry.CsvComponentRegistry;
import com.thinkfree.drive.csv.validation.CsvRowRequiredTogetherRowConstraint;
import com.thinkfree.drive.util.ResourceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVParser;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class CsvValidator {
    
    private final CsvProperty csvProperty;
    private final CsvComponentRegistry csvComponentRegistry;
    private final CsvRowRequiredTogetherRowConstraint csvRowRequiredTogetherRowConstraint;
    
    
    public void validateSize(MultipartFile multipartFile){
        long limit = csvProperty.adminMaxSize().toBytes();
        if (multipartFile.getSize() > limit) {
            throw CsvException.of(CsvErrorCode.FILE_TOO_LARGE);
        }
    }
    
    public void validateHeader(Class<?> clazz, CSVParser csvParser){
        
        var headerMap = csvParser.getHeaderMap();
        var headerNames = csvParser.getHeaderNames();
        
        if (headerMap == null || headerMap.isEmpty()) {
            throw CsvException.of(CsvErrorCode.HEADER_MISMATCH);
        }
        
        var meta = csvComponentRegistry.metaOf(clazz);
        var className = meta.clazz().getName();
        var headerSize = headerNames.size();
        var fieldSize = meta.fields().size();
        
        if (headerSize > fieldSize) {
            log.error("[csv] size mismatch. class: {}, fieldSize : {}, headerSize: {}", clazz.getName(), fieldSize, headerSize);
            throw CsvException.of(CsvErrorCode.UNSUPPORTED_COLUMN);
        }
        if (headerSize < fieldSize) {
            log.error("[csv] size mismatch. class: {}, fieldSize : {}, headerSize: {}", clazz.getName(), fieldSize, headerSize);
            throw CsvException.of(CsvErrorCode.HEADER_MISMATCH);
        }
        
        for(CsvFieldMeta csvFieldMeta : meta.fields()){
            
            var headerIndex = headerMap.get(csvFieldMeta.key());
            var index = csvFieldMeta.index();
            
            if (headerIndex == null || index != headerIndex) {
                log.error("[csv] header missing. class: {}, field : {}, index : {}, headerIndex : {}", className, csvFieldMeta.field().getName(), index, headerIndex);
                throw CsvException.of(CsvErrorCode.HEADER_MISMATCH);
            }
        }
        
    }
    
    public String normalize(String value, CsvFieldMeta meta) {
        for (var n : meta.csvField().normalizers()) {
            try{
                value = csvComponentRegistry.normalizer(n).normalize(value);
            } catch (CsvException e) {
                log.error("[csv] applyNormalizers CsvException - value : {}, key : {}", value, meta.key(), e);
                throw e;
            } catch (Exception e) {
                log.error("[csv] applyNormalizers Exception - value : {}, key : {}", value, meta.key(), e);
                throw CsvException.error(CsvErrorCode.INVALID_VALUE, value);
            }
        }
        return value;
    }
    
    public void validateValue(String value, CsvFieldMeta meta) {
        for (var c : meta.csvField().constraints()) {
            try {
                csvComponentRegistry.constraint(c).validate(value);
            } catch (CsvException e) {
                log.error("[csv] applyConstraints CsvException - value : {}, key : {}", value, meta.key(), e);
                throw e;
            } catch (Exception e) {
                log.error("[csv] applyConstraints Exception - value : {}, key : {}", value, meta.key(), e);
                throw CsvException.error(CsvErrorCode.INVALID_VALUE, value);
            }
        }
    }
    
    public void validateRow(Object row, CsvClassMeta meta){
        csvRowRequiredTogetherRowConstraint.validate(row, meta);
    }
    
    public void validateExtension(String fileName) {
        String ext = ResourceUtil.generateExtension(ResourceUtil.getFileExtension(fileName));
        if (!CsvConstants.CSV_EXTENSION.equals(ext)){
            throw new CsvException(CsvErrorCode.INVALID_FORMAT);
        }
    }
    
}

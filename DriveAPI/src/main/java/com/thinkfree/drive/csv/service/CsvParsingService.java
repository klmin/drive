package com.thinkfree.drive.csv.service;

import com.thinkfree.drive.csv.data.CsvParseData;
import com.thinkfree.drive.csv.exception.CsvErrorCode;
import com.thinkfree.drive.csv.exception.CsvException;
import com.thinkfree.drive.csv.model.CsvClassMeta;
import com.thinkfree.drive.csv.model.CsvFieldMeta;
import com.thinkfree.drive.csv.registry.CsvComponentRegistry;
import com.thinkfree.drive.csv.result.CsvParseResult;
import com.thinkfree.drive.csv.support.CsvFormats;
import com.thinkfree.drive.csv.support.CsvReaders;
import com.thinkfree.drive.csv.support.CsvValidator;
import com.thinkfree.drive.util.ObjectUtil;
import com.thinkfree.drive.util.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.charset.MalformedInputException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CsvParsingService {
    
    private final CsvValidator csvValidator;
    private final CsvComponentRegistry csvComponentRegistry;
    
    public <T> CsvParseResult<T> csvParse(MultipartFile file, Class<T> clazz) {
        
        try {
            
            csvValidator.validateExtension(file.getOriginalFilename());
            csvValidator.validateSize(file);
            
            try (InputStream is = file.getInputStream();
                 Reader reader = CsvReaders.openUtf8Reader(is);
                 CSVParser parser = CsvFormats.readerForUserImport().parse(reader)
            ) {
                csvValidator.validateHeader(clazz, parser);
                return parseToObjects(clazz, parser);
            }
            
        }catch (MalformedInputException e){
            log.error("[csvParse MalformedInputException]", e);
            return CsvParseResult.globalFail(CsvErrorCode.UNSUPPORTED_ENCODING);
        }catch (IOException e){
            log.error("[csvParse IOException]", e);
            return CsvParseResult.globalFail(CsvErrorCode.INVALID_FORMAT);
        } catch (CsvException e) {
            log.error("[csvParse CsvException]", e);
            return CsvParseResult.globalFail(e.getCode());
        }catch (Exception e){
            log.error("[csvParse Exception]", e);
            return CsvParseResult.globalFail(CsvErrorCode.UNKNOWN_ERROR);
        }
    }
    
    private <T> CsvParseResult<T> parseToObjects(Class<T> clazz, CSVParser csvParser){
        
        var meta = csvComponentRegistry.metaOf(clazz);
        List<CsvParseData<T>> csvParseDataList = new ArrayList<>();
        List<String> headers = new ArrayList<>(csvParser.getHeaderNames());
        
        for(CSVRecord csvRecord : csvParser){
            
            T obj = createObject(clazz);
            String failReason = null;
            
            for (var f : meta.fields()) {
                
                String key = f.key();
                String fieldName = f.field().getName();
                Object parsedValue = null;
                String rawValue = null;
                boolean required = f.csvField().required();
                
                try {
                    rawValue = csvRecord.get(key);
                    parsedValue = parseField(rawValue, f);
                } catch (IllegalArgumentException | IllegalStateException e) {
                    if (required) {
                        log.error("[csv] record not found. class: {}, rawValue : {}, field: {}, key : {}", clazz.getName(), rawValue, fieldName, key, e);
                        failReason = StringUtil.nvl(failReason, CsvErrorCode.MISSING_REQUIRED_FIELD.toString());
                    }
                } catch (CsvException e) {
                    log.error("[csv] field invalid. class: {}, rawValue : {}, field: {}, key : {}", clazz.getName(), rawValue, fieldName, key, e);
                    failReason = StringUtil.nvl(failReason, e.getMessage());
                    
                }
                
                try {
                    Field field = f.field();
                    field.set(obj, parsedValue);
                } catch (IllegalAccessException | IllegalArgumentException e) {
                    log.error("[csv] field set fail. class: {}, parsedValue : {}, field: {}, key : {}", clazz.getName(), parsedValue, fieldName, key, e);
                    failReason = StringUtil.nvl(failReason, CsvErrorCode.INVALID_VALUE.getMessage());
                }
                
            }
            
            if (failReason == null) {
                try {
                    csvValidator.validateRow(obj, meta);
                } catch (CsvException e) {
                    failReason = e.getMessage();
                }
            }
            
            if (csvParser.getHeaderMap().size() < csvRecord.values().length) {
                failReason = StringUtil.nvl(failReason, CsvErrorCode.UNSUPPORTED_COLUMN.getMessage());
            }
            
            csvParseDataList.add(new CsvParseData<>(obj, csvRecord.toMap(), failReason));
        }
        
        applyDatasetValidators(meta, csvParseDataList);
        
        return CsvParseResult.success(headers, csvParseDataList);
    }
    
    private Object parseField(String rawValue, CsvFieldMeta csvFieldMeta) throws CsvException {
        
        boolean required = csvFieldMeta.csvField().required();
         
        if(!StringUtils.hasText(rawValue)){
            if(required){
                throw CsvException.error(CsvErrorCode.MISSING_REQUIRED_FIELD, null);
            }
            return null;
        }
        
        String trimValue = rawValue.trim();
        
        var normalized = csvValidator.normalize(trimValue, csvFieldMeta);
        
        if (!StringUtils.hasText(normalized)) {
            if(required){
                throw CsvException.error(CsvErrorCode.MISSING_REQUIRED_FIELD, null);
            }
            return null;
        }
        
        csvValidator.validateValue(normalized, csvFieldMeta);
        
        return csvComponentRegistry.typeHandler(csvFieldMeta.type()).convertAndValidate(normalized, csvFieldMeta);
        
    }
    
    private <T> void applyDatasetValidators(CsvClassMeta meta, List<CsvParseData<T>> rows) {
        
        List<CsvFieldMeta> uniqueFields = meta.fields()
                                              .stream()
                                              .filter(f -> f.csvField().unique())
                                              .toList();
        
        if (uniqueFields.isEmpty()){
            return;
        }
        
        Map<CsvFieldMeta, Map<String, Integer>> seenByField = new LinkedHashMap<>();
        
        for (CsvFieldMeta f : uniqueFields) {
            seenByField.put(f, new LinkedHashMap<>());
        }
        
        for (int i = 0; i < rows.size(); i++) {
            
            var row = rows.get(i);
    
            for (CsvFieldMeta f : uniqueFields) {
                
                String val = extractStringValue(row.data(), f);
                
                if (!StringUtils.hasText(val)) {
                    continue;
                }
    
                Map<String, Integer> seen = seenByField.get(f);
                Integer firstIdx = seen.get(val);
    
                if (firstIdx != null) {
                   
                    if (!StringUtils.hasText(row.failureReason())) {
                        var updated = row.withFailureReason(CsvErrorCode.DUPLICATE_ROW.getMessage());
                        rows.set(i, updated);
                    }
                    continue;
                }
                
                seen.put(val, i);
            }
        }
    }
    
    private <T> T createObject(Class<T> clazz) throws CsvException {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            log.error("[csv] 객체 생성 실패. class: {},", clazz.getName(), e);
            throw CsvException.of(CsvErrorCode.UNKNOWN_ERROR);
        }
    }
    
    private <T> String extractStringValue(T obj, CsvFieldMeta f) {
        try {
            Field field = f.field();
            Object v = field.get(obj);
            return ObjectUtil.toStringIfNotNull(v);
        } catch (Exception e) {
            log.error("[csv] key extract failed. field : {}", f.field().getName(), e);
            return null;
        }
    }
    
    
}

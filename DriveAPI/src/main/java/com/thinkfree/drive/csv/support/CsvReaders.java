package com.thinkfree.drive.csv.support;

import com.thinkfree.drive.csv.exception.CsvErrorCode;
import com.thinkfree.drive.csv.exception.CsvException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.input.BOMInputStream;

import java.io.*;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class CsvReaders {
    
    public static BOMInputStream createBomInputStream(InputStream is) {
        try {
            return BOMInputStream.builder()
                                 .setInputStream(is)
                                 .setInclude(false)
                                 .setByteOrderMarks(ByteOrderMark.UTF_8)
                                 .get();
        }catch(IOException e){
            log.error("CSV BOM stream open failed", e);
            throw new CsvException(CsvErrorCode.INVALID_FORMAT);
        }
    }
    
    public static Reader openUtf8Reader(InputStream is) throws IOException {

        BOMInputStream bomIn = createBomInputStream(is);
        ByteOrderMark detectedBom;
        
        try {

            detectedBom = bomIn.getBOM();
            
            if (!ByteOrderMark.UTF_8.equals(detectedBom)) {
                log.error("Unsupported BOM detected: {}", detectedBom);
                throw new CsvException(CsvErrorCode.UNSUPPORTED_ENCODING);
            }

        } catch (IOException e) {
            log.error("CSV BOM stream open failed", e);
            throw new CsvException(CsvErrorCode.INVALID_FORMAT);
        }

        CharsetDecoder charsetDecoder = StandardCharsets.UTF_8.newDecoder()
                                                        .onMalformedInput(CodingErrorAction.REPORT)
                                                        .onUnmappableCharacter(CodingErrorAction.REPORT);
        
        return new BufferedReader(new InputStreamReader(bomIn, charsetDecoder), 64 * 1024);

    }
 
}
    

    
  


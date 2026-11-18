package com.thinkfree.drive.weboffice.support;

import com.thinkfree.drive.global.exception.ApiException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DocIdParser {
    
    private static final String SPLIT_SEPARATOR = "-";
    
    public static DocIdParserResult parseDocId(String docId) {
        try {
            String[] parts = docId.split(SPLIT_SEPARATOR);
            return new DocIdParserResult(Integer.parseInt(parts[0]), Long.parseLong(parts[1]));
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}

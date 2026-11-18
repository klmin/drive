package com.thinkfree.drive.util;

import com.thinkfree.drive.util.file.FileNameParts;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
class ResourceUtilTest {
    
    @Test
    void getFileExtension() {
        
        String[] testFileNames = {
                "a..b. (1) (1)",
                "a..b. (1)(1)",
                "a..b. (1) [5]",
                "a..b. (1) (1).txt",
                "a..b.c (1) (1).txt",
                "a (1) (1)",
                "a (1) (1).jpg",
                "a (1) (1) (2).txt",
                "a (1) (1) (1)",
                "a (1) (1)(1)",
                "a (1) (1) (4)",
                "a",
                "a.txt",
                "a (1) 1.txt",
                "a (1) [1].txt",
                "(1) (1) [1].txt",
                "a.a (1) [1].txt",
                "a (1) (1)",
                "a (1)(1)"
        };
        
        for (String fileName : testFileNames) {
            FileNameParts fileNameParts = ResourceUtil.extractFileNameParts(fileName);
            log.info("originalFileName :{}, baseFileName : {}, copyNumber : {}", fileName, fileNameParts.baseFileName(), fileNameParts.copyNumber() );
        }
        
    }
}
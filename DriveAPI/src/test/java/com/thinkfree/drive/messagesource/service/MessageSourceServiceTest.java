package com.thinkfree.drive.messagesource.service;

import com.thinkfree.drive.messagesource.keys.MessageKeys;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

@SpringBootTest
@Slf4j
class MessageSourceServiceTest {
    
    @Autowired
    MessageSourceService messageSourceService;
    
    @Test
    void getMessage() {
        
        String excelName = messageSourceService.getMessage(MessageKeys.DOCUMENT_NEW_EXCEL);
        log.info("default locale excelName : {}", excelName);
        
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        excelName = messageSourceService.getMessage(MessageKeys.DOCUMENT_NEW_EXCEL);
        log.info("english locale excelName : {}", excelName);
        
    }
    
  
}
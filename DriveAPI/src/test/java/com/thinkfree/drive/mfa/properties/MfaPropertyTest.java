package com.thinkfree.drive.mfa.properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MfaPropertyTest {
    
    @Autowired
    public MfaProperty mfaProperty;
    
    @Test
    void test(){
        System.out.println("mfaProperty : "+mfaProperty);
    }
}
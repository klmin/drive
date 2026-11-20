package com.thinkfree.drive.mfa.properties;

import com.thinkfree.drive.common.DriveIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DriveIntegrationTest
class MfaPropertyTest {
    
    @Autowired
    public MfaProperty mfaProperty;
    
    @Test
    void test(){
        System.out.println("mfaProperty : "+mfaProperty);
    }
}
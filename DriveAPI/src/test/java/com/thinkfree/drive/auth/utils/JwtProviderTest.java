package com.thinkfree.drive.auth.utils;

import com.thinkfree.drive.user.enums.RoleType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "jwt.accessExp=315360000000"
})
@Slf4j
class JwtProviderTest {
    
    @Autowired
    JwtProvider jwtProvider;
    
    @Test
    void createToken(){
        int userSeq = 2;
        String accessToken = jwtProvider.createAccessToken(userSeq, "test-session-id", RoleType.ADMIN);
        log.error("accessToken : {}", accessToken);
    }
}
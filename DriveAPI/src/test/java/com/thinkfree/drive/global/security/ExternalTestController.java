package com.thinkfree.drive.global.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExternalTestController {

    @GetMapping("/api/public/v1/test/ping")
    public String ping() {
        return "pong";
    }
    
    @GetMapping("/api/public/v1/test/user-role")
    public String userRole() {
        return "role";
    }
}

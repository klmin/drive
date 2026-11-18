package com.thinkfree.drive.api.v1.common.controller;

import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/common")
@Tag(name = "System info")
public class CommonController {

    @ExternalApi
    @Operation(summary = "Check Drive availability",
            description = "Returns the plain text value OK when the API responds.")
    @GetMapping("/ping")
    public String getPing() {
        return "OK";
    }
}

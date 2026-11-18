package com.thinkfree.drive.api.v1.build.controller;

import com.thinkfree.drive.api.v1.build.response.BuildInfoResponse;
import com.thinkfree.drive.api.v1.build.response.BuildResponse;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.util.DateUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/build")
@RequiredArgsConstructor
@Tag(name = "System info")
public class BuildController {
    
    private final BuildProperties buildProperties;
    private final Environment environment;

    @ExternalApi
    @Operation(summary = "Get detailed build information",
            description = "Returns metadata for the running Drive build, including its version and build time.")
    @GetMapping
    public ResponseEntity<BuildResponse> info(){
        
        String version = buildProperties.getVersion();
        String name = buildProperties.getName();
        String buildTime = buildProperties.get("currentTime");
        String finalName = buildProperties.get("finalName");
        // 활성 프로필(쉼표 구분). 서버는 프로필 없이 뜨므로 null
        String[] activeProfiles = environment.getActiveProfiles();
        String profile = activeProfiles.length == 0 ? null : String.join(",", activeProfiles);

        LocalDateTime buildTimeFormat = DateUtil.parse(buildTime, "yyyyMMddHHmm");

        return ResponseEntity.ok(new BuildResponse(name, finalName, version, profile, buildTimeFormat));
    }

    @ExternalApi
    @Operation(summary = "Get the build summary",
            description = "Returns a compact version and build time summary for the running Drive server.")
    @GetMapping("/summary")
    public ResponseEntity<BuildInfoResponse> summary(){

        String version = buildProperties.getVersion();
        String buildTime = buildProperties.get("currentTime");

        LocalDateTime buildInfoTimeFormat = DateUtil.parse(buildTime, "yyyyMMddHHmm");
        String buildInfo = "v" + version + "(build " + buildTime + ")";

        return ResponseEntity.ok(new BuildInfoResponse(buildInfo));
    }
}

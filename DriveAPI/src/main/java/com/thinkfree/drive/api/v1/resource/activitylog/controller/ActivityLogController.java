package com.thinkfree.drive.api.v1.resource.activitylog.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.resource.activitylog.request.ResourceActivityLogListRequest;
import com.thinkfree.drive.api.v1.resource.activitylog.response.ResourceActivityLogListResponse;
import com.thinkfree.drive.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/resources")
@Tag(name = "Resources")
public class ActivityLogController {

    @Operation(summary = "List resource activity",
            description = "Returns sample activity data for the resource. This is a placeholder response.")
    @GetMapping("/{resourceSeq}/activities")
    public ResponseEntity<ApiResponse<List<ResourceActivityLogListResponse>>> findAllPaginated(ResourceActivityLogListRequest request, @AuthenticationPrincipal CustomUserDetails details) {
        // dummpy API로 추후 수정 필요
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            InputStream is = getClass().getClassLoader().getResourceAsStream("data/activity-logs.json");

            if (is == null) {
                throw new RuntimeException("activity-logs.json 파일을 찾을 수 없습니다.");
            }

            List<ResourceActivityLogListResponse> logs = objectMapper.readValue(
                    is,
                    new TypeReference<List<ResourceActivityLogListResponse>>() {}
            );
            return ApiResponse.success(logs);

        } catch (Exception e) {
            throw new RuntimeException("JSON 파일 읽기 실패: " + e.getMessage(), e);
        }
    }
}

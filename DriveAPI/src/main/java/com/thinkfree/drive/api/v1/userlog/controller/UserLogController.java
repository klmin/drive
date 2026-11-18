package com.thinkfree.drive.api.v1.userlog.controller;

import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.userlog.response.UserLogListResponse;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.userlog.service.UserLogFindService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
@Tag(name = "Account")
public class UserLogController {
    private final UserLogFindService userLogFindService;

    @ExternalApi
    @Operation(summary = "List your activity",
            description = "Returns a page of the signed-in user's activity records.")
    @GetMapping("/log")
    public ResponseEntity<ApiResponse<List<UserLogListResponse>>> getCurrentUserLogs(@AuthenticationPrincipal CustomUserDetails userDetails, @ModelAttribute @Valid CursorPagingRequest pagingRequest) {
        var response = userLogFindService.findMyLogs(userDetails.getUserSeq(), pagingRequest);
        var list = response.getList().stream()
                .map(UserLogListResponse::from)
                .toList();
        return ApiResponse.successWithCursor(list, response.getPage());
    }
}

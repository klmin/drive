package com.thinkfree.drive.api.v1.superadmin.user.controller;

import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.superadmin.user.request.SuperAdminUserSearchRequest;
import com.thinkfree.drive.api.v1.superadmin.user.response.SuperAdminUserSearchResponse;
import com.thinkfree.drive.api.v1.superadmin.user.usecase.SuperAdminUserUseCase;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Super admin - Tenants")
@RestController
@RequestMapping("/api/v1/super-admin/tenants/{tenantSeq}/users")
@RequiredArgsConstructor
public class SuperAdminUserController {

    private final SuperAdminUserUseCase superAdminUserUseCase;

    @ExternalApi
    @Operation(summary = "Search users in a selected tenant",
            description = "Finds accounts in the specified tenant that match the supplied search filters.")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<SuperAdminUserSearchResponse>>> search(@PathVariable Integer tenantSeq,
                                                                                  @ModelAttribute @Valid SuperAdminUserSearchRequest request) {
        var response = superAdminUserUseCase.search(tenantSeq, request);
        return ApiResponse.success(response);
    }
}

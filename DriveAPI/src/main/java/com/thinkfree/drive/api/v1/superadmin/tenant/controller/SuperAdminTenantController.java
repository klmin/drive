package com.thinkfree.drive.api.v1.superadmin.tenant.controller;

import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.systemconfig.response.OfficeResponse;
import com.thinkfree.drive.api.v1.superadmin.tenant.request.SuperAdminTenantCreateRequest;
import com.thinkfree.drive.api.v1.superadmin.tenant.request.SuperAdminTenantUpdateRequest;
import com.thinkfree.drive.api.v1.superadmin.tenant.response.SuperAdminTenantAccessTokenResponse;
import com.thinkfree.drive.api.v1.superadmin.tenant.response.SuperAdminTenantCreateResponse;
import com.thinkfree.drive.api.v1.superadmin.tenant.response.SuperAdminTenantDetailResponse;
import com.thinkfree.drive.api.v1.superadmin.tenant.response.SuperAdminTenantListResponse;
import com.thinkfree.drive.api.v1.superadmin.tenant.response.SuperAdminTenantQuotaCapacityResponse;
import com.thinkfree.drive.api.v1.superadmin.tenant.usecase.SuperAdminTenantUseCase;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.license.exception.ResultCode;
import com.thinkfree.drive.tenant.enums.TenantType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Tag(name = "Super admin - Tenants")
@RestController
@RequestMapping("/api/v1/super-admin/tenants")
@RequiredArgsConstructor
public class SuperAdminTenantController {

    private final SuperAdminTenantUseCase superAdminTenantUseCase;

    @ExternalApi
    @Operation(summary = "List tenants",
            description = "Returns a page of tenants. Filter by type with tt (SELF_MANAGED or SYSTEM_MANAGED).")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SuperAdminTenantListResponse>>> findAllPaginated(@ModelAttribute @Valid CursorPagingRequest pagingRequest,
                                                                                            @RequestParam(required = false) TenantType tt) {
        var response = superAdminTenantUseCase.findAllPaginated(pagingRequest, tt);
        return ApiResponse.successWithCursor(response.getList(), response.getPage());
    }
    
    @ExternalApi
    @Operation(summary = "Get available tenant quota capacity",
            description = "Returns the capacity available for tenant quota allocation. Pass ts to evaluate an existing tenant.")
    @GetMapping("/quota-capacity")
    public ResponseEntity<ApiResponse<SuperAdminTenantQuotaCapacityResponse>> findQuotaCapacity(@RequestParam(required = false) Integer ts) {
        var response = superAdminTenantUseCase.findQuotaCapacity(ts);
        return ApiResponse.success(response);
    }

    @ExternalApi
    @Operation(summary = "Get tenant details",
            description = "Returns settings and status for the tenant identified by tenantSeq.")
    @GetMapping("/{tenantSeq}")
    public ResponseEntity<ApiResponse<SuperAdminTenantDetailResponse>> find(@PathVariable Integer tenantSeq) {
        var response = superAdminTenantUseCase.find(tenantSeq);
        return ApiResponse.success(response);
    }

    @ExternalApi
    @Operation(summary = "Create a tenant",
            description = "Creates a tenant with its administrator account, default settings, and total quota.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<SuperAdminTenantCreateResponse>> create(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                                              @Valid @RequestBody SuperAdminTenantCreateRequest request) {
        var response = superAdminTenantUseCase.create(request, userDetails.getUserSeq());
        return ApiResponse.created(response);
    }

    @ExternalApi
    @Operation(summary = "Update a tenant",
            description = "Updates the tenant name, domain, allowed IPs, and total quota. Tenant type and user mode cannot be changed.")
    @PatchMapping("/{tenantSeq}")
    public ResponseEntity<ApiResponse<Void>> update(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                    @PathVariable Integer tenantSeq,
                                                    @Valid @RequestBody SuperAdminTenantUpdateRequest request) {
        superAdminTenantUseCase.update(tenantSeq, request, userDetails.getUserSeq());
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Delete a tenant",
            description = "Deletes a SYSTEM_MANAGED tenant with all its users and data. SELF_MANAGED tenants cannot be deleted.")
    @DeleteMapping("/{tenantSeq}")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                    @PathVariable Integer tenantSeq) {
        superAdminTenantUseCase.delete(tenantSeq, userDetails.getUserSeq());
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Register a license for a tenant",
            description = "Uploads a license file and registers it for the selected tenant.")
    @PostMapping(value = "/{tenantSeq}/license", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> registerLicense(@PathVariable Integer tenantSeq,
                                                             @RequestParam(value = "licenseFile") MultipartFile licenseFile) {
        byte[] licenseBlob;
        try {
            licenseBlob = licenseFile.getBytes();
        } catch (IOException e) {
            throw new ApiRuntimeException(ResultCode.LICENSE);
        }

        superAdminTenantUseCase.registerLicense(tenantSeq, licenseBlob);
        return ApiResponse.success();
    }

    @ExternalApi
    @Operation(summary = "Get Office settings for a tenant",
            description = "Returns the selected tenant's Office integration settings. The response can contain connection credentials.")
    @GetMapping("/{tenantSeq}/office")
    public ResponseEntity<ApiResponse<OfficeResponse>> getOffice(@PathVariable Integer tenantSeq) {
        var result = ApiResponse.success(superAdminTenantUseCase.getOffice(tenantSeq));
        // 비밀값이 들어 있어 캐시하지 않는다(관리자 office 조회와 같음)
        return ResponseEntity.status(result.getStatusCode()).headers(result.getHeaders())
                .cacheControl(CacheControl.noStore()).body(result.getBody());
    }

    @Operation(summary = "Issue a tenant entry key",
            description = "Issues a short-lived key (valid for 5 minutes) for entering the selected tenant's administrator console.")
    @PostMapping("/{tenantSeq}/entry-key")
    public ResponseEntity<ApiResponse<SuperAdminTenantAccessTokenResponse>> issueEntryKey(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                                                          @PathVariable Integer tenantSeq) {
        var response = superAdminTenantUseCase.issue(tenantSeq, userDetails.getUserSeq());
        return ApiResponse.success(response);
    }
}

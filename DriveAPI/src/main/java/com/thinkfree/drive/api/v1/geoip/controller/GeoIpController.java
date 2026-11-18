package com.thinkfree.drive.api.v1.geoip.controller;

import com.thinkfree.drive.global.annotation.ParameterDoc;
import com.maxmind.geoip2.record.Country;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.geoip.response.GeoIpResponse;
import com.thinkfree.drive.global.config.externalapi.ExternalApi;
import com.thinkfree.drive.userlog.service.IpLocationService;
import com.thinkfree.drive.util.IpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "GeoIP", description = "Look up the country of an IP address")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/geoip")
public class GeoIpController {

    private final IpLocationService ipLocationService;

    @ExternalApi
    @Operation(summary = "Look up a country code",
            description = "Uses the requested IP address, or the caller's address when none is provided, to look up a country code.")
    @GetMapping("/country-code")
    public ResponseEntity<ApiResponse<GeoIpResponse>> getCountryCode(
            HttpServletRequest request,
            @ParameterDoc(title = "IP address", description = "IP address whose country code you want to look up.") @RequestParam(required = false) String ip) {
        String clientIp = (ip != null && !ip.isBlank()) ? ip : IpUtil.extractClientIp(request);
        Country country = ipLocationService.getCountry(clientIp);

        String countryCode = (country != null) ? country.getIsoCode() : "국가코드 확인 불가 - Geoip db에 없는 ip";
        return ApiResponse.success(new GeoIpResponse(clientIp, countryCode));
    }
}

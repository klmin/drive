package com.thinkfree.drive.api.v1.admin.accessrestriction.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.admin.accessrestriction.request.AllowedCountriesUpdateRequest;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockUserCustom//(tenantSeq = 3)
class AccessAllowedCountryControllerTest extends AbstractMvcTest {

    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/admins/access-restriction");
        //changeTestHost();
    }

    //@Transactional
    @Test
    void putAllowedCountries() {
        var request = new AllowedCountriesUpdateRequest(List.of("KR", "GA"));
        ApiResponse<Void> response = putForObject("countries", request, new TypeReference<>() {}, status().isOk());
        Assertions.assertEquals(OK.value(), response.getCode());
    }

   // @Transactional
    @Test
    void putAllowedCountries_빈목록이면_전체삭제() {
        var request = new AllowedCountriesUpdateRequest(List.of());
        ApiResponse<Void> response = putForObject("countries", request, new TypeReference<>() {}, status().isOk());
        Assertions.assertEquals(OK.value(), response.getCode());
    }

    @Test
    void putAllowedCountries_null이면_400() {
        var request = new AllowedCountriesUpdateRequest(null);
        putForObject("countries", request, new TypeReference<>() {}, status().isBadRequest());
    }

}

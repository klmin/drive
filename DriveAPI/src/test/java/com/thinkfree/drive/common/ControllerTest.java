package com.thinkfree.drive.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.api.v1.token.controller.TokenReissueController;
import com.thinkfree.drive.auth.token.refresh.service.impl.DefaultRefreshTokenService;
import com.thinkfree.drive.auth.utils.JwtProvider;
import com.thinkfree.drive.global.config.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ImportAutoConfiguration(SecurityConfig.class)
@WebMvcTest({
        TokenReissueController.class
})
@AutoConfigureRestDocs
public abstract class ControllerTest {
    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    protected JwtProvider jwtProvider;

    @MockitoBean
    protected DefaultRefreshTokenService refreshTokenService;

    protected String convertObjectToJson(Object data) throws JsonProcessingException {
        return objectMapper.writeValueAsString(data);
    }
}
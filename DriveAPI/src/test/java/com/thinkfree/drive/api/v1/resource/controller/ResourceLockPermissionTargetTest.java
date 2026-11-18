package com.thinkfree.drive.api.v1.resource.controller;

import com.thinkfree.drive.api.v1.resource.converter.ResourceRequestConverter;
import com.thinkfree.drive.api.v1.resource.converter.ResourceResponseConverter;
import com.thinkfree.drive.application.resource.ResourceDownloadUseCase;
import com.thinkfree.drive.application.resource.ResourceUseCase;
import com.thinkfree.drive.global.enums.AuthSource;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.global.security.UserDetailDto;
import com.thinkfree.drive.permission.aspect.ResourcePermissionAspect;
import com.thinkfree.drive.permission.enums.SharePermissionType;
import com.thinkfree.drive.permission.service.ResourcePermissionService;
import com.thinkfree.drive.permission.support.RequestActor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * lock/unlock 의 권한 검사 대상 = 실제 잠금 대상(path 의 resourceSeq).
 * 실제 {@link ResourcePermissionAspect} 를 컨트롤러에 씌우고 같은 JSON 본문을 보내, 권한 서비스가 받은 resourceSeq 와
 * usecase 가 받은 resourceSeq 를 비교한다. 본문에 다른 resourceSeq 를 넣어도 path 값으로 검사해야 한다(권한 우회 방지).
 */
@DisplayName("lock/unlock 권한 검사 대상")
class ResourceLockPermissionTargetTest {

    private static final int REQUEST_USER = 10;
    private static final int OWNER = 20;
    private static final long PATH_RESOURCE = 200L;
    private static final long OTHER_RESOURCE = 100L;

    private final ResourceUseCase resourceUseCase = mock(ResourceUseCase.class);
    private final ResourcePermissionService permissionService = mock(ResourcePermissionService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var controller = new ResourceController(resourceUseCase, mock(ResourceDownloadUseCase.class),
                mock(ResourceRequestConverter.class), mock(ResourceResponseConverter.class));
        AspectJProxyFactory factory = new AspectJProxyFactory(controller);
        factory.setProxyTargetClass(true);
        factory.addAspect(new ResourcePermissionAspect(permissionService));
        ResourceController proxied = factory.getProxy();

        mockMvc = MockMvcBuilders.standaloneSetup(proxied)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        var details = new CustomUserDetails(new UserDetailDto(REQUEST_USER, "user@thinkfree.com", "사용자", "ROLE_USER",
                AuthSource.INTERNAL, 1, Set.of()));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, "", details.getAuthorities()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Long checkedResourceSeq() {
        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(permissionService).checkPermission(eq(SharePermissionType.EDIT), captor.capture(), eq(OWNER),
                any(RequestActor.class), nullable(Boolean.class));
        return captor.getValue();
    }

    @Test
    @DisplayName("lock: 본문에 다른 resourceSeq 를 넣어도 path 리소스로 권한 검사")
    void lock_bodyResourceSeqIgnored() throws Exception {
        mockMvc.perform(patch("/api/v1/resources/" + PATH_RESOURCE + "/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceSeq\":" + OTHER_RESOURCE + ",\"ss\":" + OWNER + "}"))
                .andExpect(status().isOk());

        verify(resourceUseCase).lock(PATH_RESOURCE, OWNER, REQUEST_USER);
        assertThat(checkedResourceSeq()).isEqualTo(PATH_RESOURCE);
    }

    @Test
    @DisplayName("unlock: 본문에 다른 resourceSeq 를 넣어도 path 리소스로 권한 검사")
    void unlock_bodyResourceSeqIgnored() throws Exception {
        mockMvc.perform(patch("/api/v1/resources/" + PATH_RESOURCE + "/unlock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceSeq\":" + OTHER_RESOURCE + ",\"ss\":" + OWNER + "}"))
                .andExpect(status().isOk());

        verify(resourceUseCase).unlock(PATH_RESOURCE, OWNER, REQUEST_USER);
        assertThat(checkedResourceSeq()).isEqualTo(PATH_RESOURCE);
    }

    @Test
    @DisplayName("lock: 공유 항목을 본문 {ss} 만으로 잠가도 path 리소스로 권한 검사")
    void lock_ssOnly() throws Exception {
        mockMvc.perform(patch("/api/v1/resources/" + PATH_RESOURCE + "/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ss\":" + OWNER + "}"))
                .andExpect(status().isOk());

        verify(resourceUseCase).lock(PATH_RESOURCE, OWNER, REQUEST_USER);
        assertThat(checkedResourceSeq()).isEqualTo(PATH_RESOURCE);
    }
}

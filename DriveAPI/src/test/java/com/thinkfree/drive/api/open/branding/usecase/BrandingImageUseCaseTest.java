package com.thinkfree.drive.api.open.branding.usecase;

import com.thinkfree.drive.api.open.branding.converter.BrandingImageResponseConverter;
import com.thinkfree.drive.api.open.branding.response.BrandingImageResponse;
import com.thinkfree.drive.branding.enums.BrandingImageSource;
import com.thinkfree.drive.branding.enums.BrandingImageType;
import com.thinkfree.drive.branding.exception.BrandingErrorCode;
import com.thinkfree.drive.branding.result.BrandingImageResult;
import com.thinkfree.drive.branding.service.BrandingFindService;
import com.thinkfree.drive.branding.service.BrandingMetaService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.license.service.LicenseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 테넌트가 특정되지 않은 요청(슈퍼어드민 포털, {@code SecurityPaths.TENANT_OPTIONAL})에서 TenantContext 가 비어 있을 때
 * 클래스패스 기본 이미지를 {@code "default"} ETag 로 돌려주는지. 테넌트가 있을 때의 동작은 종전과 같다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BrandingImageUseCase — 테넌트 없음/있음")
class BrandingImageUseCaseTest {

    private static final int TENANT = 7;
    private static final String DEFAULT_ETAG = "\"default\"";
    private static final BrandingImageResult DEFAULT_IMAGE = new BrandingImageResult(new byte[]{1, 2, 3}, "image/svg+xml");

    @Mock
    private BrandingImageResponseConverter converter;
    @Mock
    private BrandingFindService brandingFindService;
    @Mock
    private BrandingMetaService brandingMetaService;
    @Mock
    private LicenseService licenseService;

    @InjectMocks
    private BrandingImageUseCase useCase;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("TenantContext 가 비어 있으면 테넌트 조회 없이 기본 이미지를 \"default\" ETag 로 반환한다")
    void returnsDefaultImageWithoutTenant() {
        when(brandingMetaService.getDefaultImage(BrandingImageType.FAVICON)).thenReturn(DEFAULT_IMAGE);
        BrandingImageResponse converted = new BrandingImageResponse(DEFAULT_IMAGE.imageData(), DEFAULT_IMAGE.contentType(), 3, DEFAULT_ETAG, false);
        when(converter.toBrandingImageResponse(DEFAULT_IMAGE, DEFAULT_ETAG)).thenReturn(converted);

        BrandingImageResponse response = useCase.getImage(BrandingImageType.FAVICON, null);

        assertThat(response).isSameAs(converted);
        verifyNoInteractions(brandingFindService, licenseService);
        verify(brandingMetaService, never()).getImage(any(), any(), any());
    }

    @Test
    @DisplayName("TenantContext 가 비어 있고 If-None-Match 가 \"default\" 면 304")
    void notModifiedWithoutTenantWhenDefaultEtagMatches() {
        BrandingImageResponse response = useCase.getImage(BrandingImageType.FAVICON, DEFAULT_ETAG);

        assertThat(response.notModified()).isTrue();
        assertThat(response.etag()).isEqualTo(DEFAULT_ETAG);
        verifyNoInteractions(brandingFindService, licenseService, brandingMetaService, converter);
    }

    @Test
    @DisplayName("TenantContext 가 있으면 종전대로 테넌트 브랜딩을 조회한다(미등록 → 테넌트 기준 기본 이미지)")
    void looksUpTenantBrandingWhenTenantPresent() {
        TenantContext.set(TENANT);
        when(brandingFindService.findMetaOrThrow(TENANT)).thenThrow(ApiRuntimeException.type(BrandingErrorCode.BRANDING_NOT_FOUND));
        when(brandingMetaService.getImage(TENANT, BrandingImageType.FAVICON, BrandingImageSource.DEFAULT)).thenReturn(DEFAULT_IMAGE);
        BrandingImageResponse converted = new BrandingImageResponse(DEFAULT_IMAGE.imageData(), DEFAULT_IMAGE.contentType(), 3, DEFAULT_ETAG, false);
        when(converter.toBrandingImageResponse(eq(DEFAULT_IMAGE), eq(DEFAULT_ETAG))).thenReturn(converted);

        BrandingImageResponse response = useCase.getImage(BrandingImageType.FAVICON, null);

        assertThat(response).isSameAs(converted);
        verify(brandingMetaService, never()).getDefaultImage(any());
    }
}

package com.thinkfree.drive.api.v1.admin.branding.converter;

import com.thinkfree.drive.api.v1.admin.branding.request.BrandingUpsertRequest;
import com.thinkfree.drive.api.v1.admin.branding.response.BrandingSettingResponse;
import com.thinkfree.drive.branding.data.BrandingMetaData;
import com.thinkfree.drive.branding.dto.BrandingUpsertDto;
import com.thinkfree.drive.branding.exception.BrandingErrorCode;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import org.mapstruct.Mapper;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Mapper(componentModel = "spring")
public interface AdminBrandingConverter {

    BrandingSettingResponse toResponse(BrandingMetaData data);

    default BrandingUpsertDto toUpsertDto(Integer tenantSeq, BrandingUpsertRequest request) {
        var favicon   = toImageData(request.favicon());
        var logoIcon  = toImageData(request.logoIcon());
        var logoImage = toImageData(request.logoImage());
        var emailLogo = toImageData(request.emailLogo());

        return BrandingUpsertDto.builder()
                                .tenantSeq(tenantSeq)
                                .tabTitle(request.tabTitle())
                                .serviceName(request.serviceName())
                                .useDefault(request.useDefault())
                                .faviconData(favicon.data())
                                .faviconContentType(favicon.contentType())
                                .logoIconData(logoIcon.data())
                                .logoIconContentType(logoIcon.contentType())
                                .logoImageData(logoImage.data())
                                .logoImageContentType(logoImage.contentType())
                                .emailLogoData(emailLogo.data())
                                .emailLogoContentType(emailLogo.contentType())
                                .build();
    }

    record ImageData(byte[] data, String contentType) {}

    default ImageData toImageData(MultipartFile file) {
        
        try {
            boolean hasFile = file != null && !file.isEmpty();
            return new ImageData(
                hasFile ? file.getBytes() : null,
                hasFile ? file.getContentType() : null
            );
        } catch (IOException e) {
            throw new ApiRuntimeException(BrandingErrorCode.IMAGE_CONVERT_FAILED);
        }
    }
}

package com.thinkfree.drive.api.v1.admin.branding.request;

import io.swagger.v3.oas.annotations.media.Schema;
import com.thinkfree.drive.branding.enums.BrandingImageType;
import jakarta.validation.GroupSequence;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;
import org.springframework.web.multipart.MultipartFile;

public record BrandingUpsertRequest(
        
        @Schema(title = "Browser tab title", description = "Text displayed in the Drive browser tab.")
        @NotBlank
        @Size(min=2, max = 30)
        String tabTitle,
        @Schema(title = "Service name", description = "Service name displayed in Drive's branding.")
        @NotBlank
        @Size(min=2, max = 30)
        String serviceName,
        @Schema(title = "Use default branding", description = "Set true for Thinkfree's default branding. Custom branding (false) requires all four image files.")
        @NotNull
        Boolean useDefault,
        @Schema(title = "Favicon file", description = "Choose the browser-tab icon for custom branding.")
        MultipartFile favicon,
        @Schema(title = "Logo icon file", description = "Choose the compact logo for custom branding.")
        MultipartFile logoIcon,
        @Schema(title = "Logo image file", description = "Choose the full logo image for custom branding.")
        MultipartFile logoImage,
        @Schema(title = "Email logo file", description = "Choose the logo used in Drive email messages.")
        MultipartFile emailLogo
) {
    
    public interface FileRequired {}
    public interface FileFormat {}
    
    @GroupSequence({Default.class, FileRequired.class, FileFormat.class})
    public interface ValidationOrder {}
    
    @AssertTrue(message = "직접 등록 시 이미지 4개는 필수입니다", groups = FileRequired.class)
@Schema(hidden = true)
    public boolean isImagesRequired() {
        if (Boolean.TRUE.equals(useDefault)){
            return true;
        }
        return hasFile(favicon) && hasFile(logoIcon) && hasFile(logoImage) && hasFile(emailLogo);
    }
    
    @AssertTrue(message = "파비콘은 ICO/PNG, 최대 500KB 이어야 합니다.", groups = FileFormat.class)
@Schema(hidden = true)
    public boolean isFaviconValid() {
        if (Boolean.TRUE.equals(useDefault)){
            return true;
        }
        return BrandingImageType.FAVICON.isValidFile(favicon);
    }
    
    @AssertTrue(message = "심볼형 로고는 PNG/SVG, 최대 1MB 이어야 합니다.", groups = FileFormat.class)
@Schema(hidden = true)
    public boolean isLogoIconValid() {
        if (Boolean.TRUE.equals(useDefault)){
            return true;
        }
        return BrandingImageType.LOGO_ICON.isValidFile(logoIcon);
    }
    
    @AssertTrue(message = "기본형 로고는 PNG/SVG, 최대 2MB 이어야 합니다.", groups = FileFormat.class)
@Schema(hidden = true)
    public boolean isLogoImageValid() {
        if (Boolean.TRUE.equals(useDefault)){
            return true;
        }
        return BrandingImageType.LOGO_IMAGE.isValidFile(logoImage);
    }
    
    @AssertTrue(message = "이메일 로고는 PNG/SVG, 최대 2MB 이어야 합니다.", groups = FileFormat.class)
@Schema(hidden = true)
    public boolean isEmailLogoValid() {
        if (Boolean.TRUE.equals(useDefault)){
            return true;
        }
        return BrandingImageType.EMAIL_LOGO.isValidFile(emailLogo);
    }

    private boolean hasFile(MultipartFile file) {
        return file != null && !file.isEmpty();
    }
}

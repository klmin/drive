package com.thinkfree.drive.branding.enums;

import com.thinkfree.drive.constants.DriveConstants;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public enum BrandingImageType {

    FAVICON   ("favicon",    "favicon.ico",   Set.of("ico", "png"), 500 * 1024L),
    LOGO_ICON ("logo-icon",  "logo-simbol.svg", Set.of("png", "svg"), 1024 * 1024L),
    LOGO_IMAGE("logo-image", "logo-default.svg",Set.of("png", "svg"), 2 * 1024 * 1024L),
    EMAIL_LOGO("email-logo", "logo-email.svg",Set.of("png", "svg"), 2 * 1024 * 1024L);
    
    private static final String BASE_PATH = "branding/default";

    private final String pathValue;
    private final String fileName;
    private final Set<String> allowedExtensions;
    private final long maxSizeBytes;
    
    
    private static final Map<String, BrandingImageType> IMAGE_TYPE_MAP =
            Arrays.stream(values())
                .collect(Collectors.toUnmodifiableMap(
    type -> type.pathValue,
                     Function.identity()
                ));
    
    public boolean isValidFile(MultipartFile file) {
        if (file == null || file.isEmpty()){
            return false;
        }
        if (file.getSize() > maxSizeBytes) {
            return false;
        }
        var ext = getExtension(file.getOriginalFilename());
        return allowedExtensions.contains(ext);
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")){
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
    
    public String getClasspath() {
        return BASE_PATH + DriveConstants.PATH_SEPARATOR + fileName;
    }
    
    public static BrandingImageType fromPath(String name) {
        BrandingImageType brandingImageType = IMAGE_TYPE_MAP.get(name);
        if (brandingImageType == null){
            throw new IllegalArgumentException("Unknown brandingImageType: " + name);
        }
        return brandingImageType;
    }
    

    
}

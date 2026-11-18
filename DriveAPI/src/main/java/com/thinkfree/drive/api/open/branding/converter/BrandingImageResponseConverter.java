package com.thinkfree.drive.api.open.branding.converter;

import com.thinkfree.drive.api.open.branding.response.BrandingImageResponse;
import com.thinkfree.drive.branding.result.BrandingImageResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BrandingImageResponseConverter {
    
    @Mapping(target="data", source="result.imageData")
    @Mapping(target="contentLength", expression="java(result.contentLength())")
    @Mapping(target="notModified", expression="java(false)")
    BrandingImageResponse toBrandingImageResponse(BrandingImageResult result, String etag);
}

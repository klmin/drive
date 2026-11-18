package com.thinkfree.drive.branding.converter;

import com.thinkfree.drive.branding.dto.BrandingUpsertDto;
import com.thinkfree.drive.branding.entity.Branding;
import com.thinkfree.drive.branding.enums.BrandingImageType;
import com.thinkfree.drive.branding.query.BrandingImageFindQuery;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BrandingConverter {
    
    @Mapping(target = "type", source = "brandingImageType")
    BrandingImageFindQuery toBrandingImageFindQuery(Integer tenantSeq, BrandingImageType brandingImageType);
    
    Branding toBrandingUpsert(BrandingUpsertDto dto);
}

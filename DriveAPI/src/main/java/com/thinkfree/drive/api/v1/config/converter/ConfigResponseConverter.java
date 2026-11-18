package com.thinkfree.drive.api.v1.config.converter;

import com.thinkfree.drive.api.v1.config.response.ClientConfigResponse;
import com.thinkfree.drive.api.v1.config.response.FileSizeLimitResponse;
import com.thinkfree.drive.api.v1.config.response.OfficeResponse;
import com.thinkfree.drive.systemconfig.data.ClientConfigData;
import com.thinkfree.drive.systemconfig.data.FileSizeLimitData;
import com.thinkfree.drive.systemconfig.data.OfficeData;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ConfigResponseConverter {
    FileSizeLimitResponse toFileSizeLimitResponse(FileSizeLimitData dto);
    OfficeResponse toOfficeResponse(OfficeData dto);

    @Mapping(source = "tenantCount", target = "tenantCount")
    ClientConfigResponse toClientConfigResponse(ClientConfigData dto, int tenantCount);
}

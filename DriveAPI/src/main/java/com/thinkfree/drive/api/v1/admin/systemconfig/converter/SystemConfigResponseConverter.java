package com.thinkfree.drive.api.v1.admin.systemconfig.converter;

import com.thinkfree.drive.api.v1.admin.systemconfig.response.*;
import com.thinkfree.drive.storage.result.StorageDetailResult;
import com.thinkfree.drive.systemconfig.data.*;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SystemConfigResponseConverter {
    OfficeResponse toOfficeResponse(OfficeData dto);
    StorageResponse toStorageResponse(StorageDetailResult result);
    IntellectResponse toIntellectResponse(IntellectData dto);
    FileSizeLimitResponse toFileSizeLimitResponse(FileSizeLimitData dto);
    MfaStatusResponse toMfaStatusResponse(MfaStatusData dto);
    AccessRestrictionResponse toAccessRestrictionStatusResponse(AccessRestrictionData dto, List<String> allowedCountries, List<String> allowedIps);

}

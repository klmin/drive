package com.thinkfree.drive.systemconfig.converter;

import com.thinkfree.drive.api.v1.admin.systemconfig.request.*;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.systemconfig.entity.SystemConfig;
import com.thinkfree.drive.systemconfig.enums.SystemConfigKey;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public class SystemConfigConverter {

    public SystemConfig toUpsertFileSizeLimitSystemConfig(FileSizeLimitUpdateRequest request, Integer tenantSeq, Integer loginUserSeq) {
        Long maxFileUploadSize = request.maxFileUploadSize();
        String stored = (maxFileUploadSize == null) ? String.valueOf(DriveConstants.SIZE_LIMIT_UNLIMITED) : maxFileUploadSize.toString();
        return SystemConfig.createSystemConfig(tenantSeq, SystemConfigKey.MAX_FILE_UPLOAD_SIZE.getDbKey(), stored, loginUserSeq);
    }

    public SystemConfig toUpsertMfaStatusSystemConfig(MfaStatusUpdateRequest request, Integer tenantSeq, Integer loginUserSeq) {
        return SystemConfig.createSystemConfig(
                tenantSeq,
                SystemConfigKey.MFA_STATUS.getDbKey(),
                String.valueOf(request.enabled()),
                loginUserSeq);
    }

    public SystemConfig toUpsertAccessRestrictionStatusSystemConfig(AccessRestrictionUpdateRequest request, Integer tenantSeq, Integer loginUserSeq) {
        return SystemConfig.createSystemConfig(
                tenantSeq,
                SystemConfigKey.ACCESS_RESTRICTION_STATUS.getDbKey(),
                String.valueOf(request.enabled()),
                loginUserSeq);
    }

    public SystemConfig toUpsertIntellectStatusSystemConfig(IntellectUpdateRequest request, Integer tenantSeq, Integer loginUserSeq) {
        return SystemConfig.createSystemConfig(
                tenantSeq,
                SystemConfigKey.INTELLECT_STATUS.getDbKey(),
                String.valueOf(request.enabled()),
                loginUserSeq);
    }
}

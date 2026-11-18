package com.thinkfree.drive.application.admin.user.converter;

import com.thinkfree.drive.application.admin.user.info.AdminUserBulkStatusInfo;
import com.thinkfree.drive.application.admin.user.info.AdminUserBulkUpsertInfo;
import com.thinkfree.drive.application.admin.user.info.AdminUserListInfo;
import com.thinkfree.drive.application.admin.user.info.AdminUserLogInfo;
import com.thinkfree.drive.application.admin.user.info.AdminUserSearchInfo;
import com.thinkfree.drive.application.admin.user.info.AdminUserStorageUsageInfo;
import com.thinkfree.drive.metrics.user.result.UserStorageUsageMetricsResult;
import com.thinkfree.drive.user.data.UserBulkStatusData;
import com.thinkfree.drive.user.data.UserListData;
import com.thinkfree.drive.user.data.UserSearchData;
import com.thinkfree.drive.user.admin.result.AdminUserInsertResult;
import com.thinkfree.drive.userlog.data.UserLogData;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 도메인 data → application Info 조립 전담. Info → 어댑터 응답 변환은 각 어댑터 컨버터가 담당한다.
 */
@Mapper(componentModel = "spring")
public interface AdminUserInfoConverter {

    List<AdminUserListInfo> toListInfos(List<UserListData> data);

    List<AdminUserBulkStatusInfo> toBulkStatusInfos(List<UserBulkStatusData> data);

    AdminUserStorageUsageInfo toStorageUsageInfo(UserStorageUsageMetricsResult storageUsageMetrics);

    List<AdminUserBulkUpsertInfo.InsertedUserInfo> toInsertedUserInfos(List<AdminUserInsertResult> users);

    List<AdminUserLogInfo> toLogInfos(List<UserLogData> data);

    List<AdminUserSearchInfo> toSearchInfos(List<UserSearchData> data);

}

package com.thinkfree.drive.user.admin.service;

import com.thinkfree.drive.license.service.LicenseService.SeatPolicy;
import com.thinkfree.drive.user.admin.dto.AdminUserSaveDto;
import com.thinkfree.drive.user.admin.result.AdminUserInsertResult;
import com.thinkfree.drive.user.admin.dto.AdminUserBulkUpdateDto;
import com.thinkfree.drive.user.query.UserDeleteMarkQuery;

import java.util.List;

public interface AdminUserService {

    List<AdminUserInsertResult> registerAll(List<AdminUserSaveDto> saveDtos, SeatPolicy seatPolicy);

    List<AdminUserInsertResult> upsertAll(List<AdminUserSaveDto> inserts, List<AdminUserBulkUpdateDto> updates, SeatPolicy seatPolicy);

    void restoreUser(int userSeq);

    void markUsersAsDeleted(UserDeleteMarkQuery query);

}

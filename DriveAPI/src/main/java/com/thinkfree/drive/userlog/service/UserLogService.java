package com.thinkfree.drive.userlog.service;

import com.thinkfree.drive.userlog.enums.ActivityType;
import com.thinkfree.drive.userlog.entity.UserLog;
import com.thinkfree.drive.userlog.enums.ActorType;
import com.thinkfree.drive.userlog.repository.UserLogRepository;
import com.thinkfree.drive.userlog.repository.UserLogQueryRepository;
import com.thinkfree.drive.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserLogService {
    private final UserLogRepository userLogRepository;
    private final UserLogQueryRepository userLogQueryRepository;
    private final IpLocationService ipLocationService;

    public void registerLoginLog(Integer userSeq, HttpServletRequest request) {
        registerUserLog(userSeq, request, ActivityType.LOGIN, ActorType.USER);
    }

    public void registerLogoutLog(Integer userSeq, HttpServletRequest request) {
        registerUserLog(userSeq, request, ActivityType.LOGOUT, ActorType.USER);
    }

    public void registerChangePasswordLog(Integer userSeq, HttpServletRequest request, ActorType actorType) {
        registerUserLog(userSeq, request, ActivityType.CHANGE_PASSWORD, actorType);
    }

    private void registerUserLog(Integer userSeq, HttpServletRequest request, ActivityType activityType, ActorType actorType) {
        String ip = IpUtil.extractClientIp(request);

        String region = ipLocationService.getCountryFromIp(ip);
        UserLog log = UserLog.createUserLog(userSeq, region, ip, activityType, actorType);

        userLogRepository.insertUserLog(log);
    }

    public boolean hasChangedPassword(Integer userSeq) {
        return userLogQueryRepository.existsChangePasswordLog(userSeq);
    }

    public boolean isFirstLogin(Integer userSeq) {
        return userLogQueryRepository.existsLoginLog(userSeq);
    }
}

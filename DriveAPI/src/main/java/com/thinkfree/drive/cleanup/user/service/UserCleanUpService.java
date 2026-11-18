package com.thinkfree.drive.cleanup.user.service;

import java.util.List;

public interface UserCleanUpService {
    
    void deleteUsersCascade(List<Integer> userList);
}

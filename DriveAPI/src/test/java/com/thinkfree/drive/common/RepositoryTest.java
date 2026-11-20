package com.thinkfree.drive.common;

import com.thinkfree.drive.user.repository.UserQueryRepository;
import com.thinkfree.drive.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@DriveIntegrationTest
@Transactional
public class RepositoryTest {

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected UserQueryRepository userQueryRepository;

}

package com.thinkfree.drive.common;

import com.thinkfree.drive.user.repository.UserQueryRepository;
import com.thinkfree.drive.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
public class RepositoryTest {

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected UserQueryRepository userQueryRepository;

}

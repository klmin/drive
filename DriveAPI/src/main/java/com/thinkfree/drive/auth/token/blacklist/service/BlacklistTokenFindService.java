package com.thinkfree.drive.auth.token.blacklist.service;

import com.thinkfree.drive.auth.token.blacklist.query.BlacklistFindQuery;

public interface BlacklistTokenFindService {
    boolean existsByTokenHash(BlacklistFindQuery query);
}

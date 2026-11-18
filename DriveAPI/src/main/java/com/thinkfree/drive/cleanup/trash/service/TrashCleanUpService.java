package com.thinkfree.drive.cleanup.trash.service;

public interface TrashCleanUpService {

    void cleanUpResourcesByDate();

    void cleanUpResourcesByUser(Integer userSeq);

}

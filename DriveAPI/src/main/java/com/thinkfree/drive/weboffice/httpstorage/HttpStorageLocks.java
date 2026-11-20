package com.thinkfree.drive.weboffice.httpstorage;

import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.resource.service.ResourceLockUnlockService;
import com.thinkfree.drive.resource.query.ResourceFindQuery;
import com.thinkfree.drive.resource.enums.ResourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.Instant;

/** Uses the same file lock as the former Forward adapter. No connection-owner store is maintained. */
@Service
@RequiredArgsConstructor
public class HttpStorageLocks {
    private final ResourceFindService resources;
    private final ResourceLockUnlockService locks;
    public record Lock(boolean locked, String owner) {}

    public Lock current(HttpStorageSession session) {
        var resource = resources.findDetailOrNull(ResourceFindQuery.builder().userSeq(session.ownerUserSeq())
                .resourceSeq(session.resourceSeq()).resourceType(ResourceType.FILE).isDelete(false).build());
        if (resource == null) throw new HttpStorageException(HttpStatus.NOT_FOUND);
        return new Lock(Boolean.TRUE.equals(resource.getIsLocked()), null);
    }

    // TFO coordinates editing connections. Drive retains its existing file-level lock behavior.
    public void lockDocument(HttpStorageSession session, String connection) {
        locks.lockResource(session.resourceSeq(), session.ownerUserSeq(), session.userSeq(), Instant.now());
    }
    public void unlockDocument(HttpStorageSession session, String connection) {
        locks.unlockResource(session.resourceSeq(), session.ownerUserSeq(), session.userSeq(), Instant.now());
    }
}

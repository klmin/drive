package com.thinkfree.drive.event.resource.listener;

import com.thinkfree.drive.cleanup.storage.service.StorageDeletionService;
import com.thinkfree.drive.event.resource.ResourceDeletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 리소스 삭제 뒤 스토리지 객체 정리 — 지울 키를 삭제 대기열(tb_storage_deletion)에 넣고, 실제 삭제는 SchedulerService#processStorageDeletions 가 한다.
 * <p>
 * BEFORE_COMMIT·동기: DB 행을 지운 트랜잭션 안에서 등록하므로 커밋되면 둘 다 남고 롤백되면 둘 다 없다
 * (구 방식 = 커밋 뒤 @Async 로 바로 삭제 — 그 사이 WAS 가 죽거나 삭제가 실패하면 객체가 고아로 남았다).
 * 등록이 실패하면 예외가 발행한 트랜잭션까지 올라가 DB 삭제도 롤백된다.
 * fallbackExecution: 트랜잭션 밖에서 발행돼도 버리지 않고 바로 등록한다(지금 발행처는 모두 트랜잭션 안).
 */
@Component
@RequiredArgsConstructor
public class ResourceDeletedListener {

    private final StorageDeletionService storageDeletionService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onResourceDeleted(ResourceDeletedEvent event) {
        storageDeletionService.register(event.deleteKeys());
    }
}

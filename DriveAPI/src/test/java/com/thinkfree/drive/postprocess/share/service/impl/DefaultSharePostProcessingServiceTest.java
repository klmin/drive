package com.thinkfree.drive.postprocess.share.service.impl;

import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.postprocess.share.service.SharePostProcessingService;
import com.thinkfree.drive.postprocess.share.service.dto.UnshareLookupNotifyDto;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.postprocess.share.service.dto.UnshareNotifyDto;
import lombok.With;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Slf4j
class DefaultSharePostProcessingServiceTest {
    
    @Autowired
    public SharePostProcessingService sharePostProcessingService;
    
    @WithMockUserCustom
    @Test
    @Transactional
    void processUnsharedByMoveAndNotify() {
        var unshareLookupNotifyDto = UnshareLookupNotifyDto.builder()
                                                   .userSeq(1)
                                                   .userName("test")
                                                   .actorUserSeq(1)
                                                   .targetUserSeq(1)
                                                   .resourceType(ResourceType.FILE)
                                                   .resourceSeqList(List.of(5281L))
                                                   .fileName("testFile")
                                                   .occurredDatetime(Instant.now())
                                                   .build();
        sharePostProcessingService.processUnsharedByMoveAndNotify(unshareLookupNotifyDto);
    }
}
package com.thinkfree.drive.share.service;

import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.share.converter.ShareConverter;
import com.thinkfree.drive.share.converter.ShareResultConverter;
import com.thinkfree.drive.share.data.SharedResourceData;
import com.thinkfree.drive.share.dto.ShareUpdateDto;
import com.thinkfree.drive.share.entity.Share;
import com.thinkfree.drive.share.enums.ShareType;
import com.thinkfree.drive.share.exception.ShareErrorCode;
import com.thinkfree.drive.share.query.ShareDeleteQuery;
import com.thinkfree.drive.share.repository.ShareRepository;
import com.thinkfree.drive.share.service.impl.DefaultShareService;
import com.thinkfree.drive.share.target.converter.ShareTargetConverter;
import com.thinkfree.drive.share.target.result.ShareTargetDeletionResult;
import com.thinkfree.drive.share.target.result.ShareTargetReplaceResult;
import com.thinkfree.drive.share.target.service.ShareTargetService;
import com.thinkfree.drive.share.validation.SharePermissionValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * W9 — 공유 수정은 쓰기 리포 {@code findByUserSeqAndShareSeq} 관리 엔티티 + {@link Share#changeShare}(커밋 때 변경 감지), 정리 이벤트 시각은 서비스가 정한 현재 시각(D1),
 * 공유 삭제는 호출부가 읽은 판단용 DTO 를 받아 같은 조회를 반복하지 않는다(D4). DB 반영·감사 컬럼은 {@code DefaultShareRepositoryTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DefaultShareService — 공유 수정(변경 감지)·삭제(판단 DTO 재사용) W9")
class DefaultShareServiceWriteTest {

    private static final int OWNER = 2;
    private static final long SHARE_SEQ = 10L;
    private static final long RESOURCE_SEQ = 100L;

    @Mock private ShareConverter shareConverter;
    @Mock private ShareResultConverter shareResultConverter;
    @Mock private ShareRepository shareRepository;
    @Mock private ShareTargetService shareTargetService;
    @Mock private SharePermissionValidator sharePermissionValidator;
    @Mock private ShareTargetConverter shareTargetConverter;
    @Mock private ShareFindService shareFindService;

    @InjectMocks
    private DefaultShareService shareService;

    private static Share stored(ShareType type) {
        return Share.builder().shareSeq(SHARE_SEQ).userSeq(OWNER).resourceSeq(RESOURCE_SEQ).shareType(type)
                .canView(true).canEdit(false).canDownload(false).canUpload(false).canDelete(false).canShare(false).build();
    }

    private static ShareUpdateDto updateDto(ShareType previous, ShareType next, boolean canEdit) {
        return ShareUpdateDto.builder().shareSeq(SHARE_SEQ).userSeq(OWNER).ownerTenantSeq(1).previousShareType(previous).shareType(next)
                .canView(true).canEdit(canEdit).canDownload(true).canUpload(false).canDelete(false).canShare(false)
                .resourceType(ResourceType.FILE).resourceSeq(RESOURCE_SEQ).resourceName("a.txt").build();
    }

    private void replaceReturnsEmpty() {
        given(shareTargetService.replaceShareTargets(any()))
                .willReturn(new ShareTargetReplaceResult(OWNER, Set.of(), ShareTargetDeletionResult.empty(), Instant.now()));
    }

    @Test
    @DisplayName("수정 — 관리 엔티티 1회 로드 후 changeShare(링크 공유는 권한 반영), 정리 이벤트 시각 = 지금(flush·수정일 조회 없음)")
    void update_changesManagedEntity() {
        replaceReturnsEmpty();
        Share managed = stored(ShareType.ORG_LINK);
        given(shareRepository.findByUserSeqAndShareSeq(OWNER, SHARE_SEQ)).willReturn(managed);
        Instant before = Instant.now();

        var result = shareService.updateShareWithTargets(updateDto(ShareType.ORG_LINK, ShareType.ORG_LINK, true));

        assertThat(managed.getCanEdit()).as("링크 공유 — 권한 반영").isTrue();
        assertThat(managed.getCanDownload()).isTrue();
        assertThat(result.deleteResult().occurredDatetime()).isBetween(before, Instant.now());
        verify(shareRepository).findByUserSeqAndShareSeq(OWNER, SHARE_SEQ);
    }

    @Test
    @DisplayName("수정 — SPECIFIC_USERS 로 바꾸면 공유 유형만(권한 컬럼 그대로), 없는 공유는 UPDATE_FAIL(구 0행)")
    void update_specificUsersOnlyType_andMissingFails() {
        replaceReturnsEmpty();
        Share managed = stored(ShareType.SPECIFIC_USERS);
        given(shareRepository.findByUserSeqAndShareSeq(OWNER, SHARE_SEQ)).willReturn(managed);

        shareService.updateShareWithTargets(updateDto(ShareType.SPECIFIC_USERS, ShareType.SPECIFIC_USERS, true));
        assertThat(managed.getShareType()).isEqualTo(ShareType.SPECIFIC_USERS);
        assertThat(managed.getCanEdit()).as("SPECIFIC_USERS 는 권한 컬럼 불변").isFalse();

        given(shareRepository.findByUserSeqAndShareSeq(OWNER, SHARE_SEQ)).willReturn(null);
        assertThatThrownBy(() -> shareService.updateShareWithTargets(updateDto(ShareType.SPECIFIC_USERS, ShareType.SPECIFIC_USERS, false)))
                .isInstanceOf(ApiRuntimeException.class)
                .extracting(e -> ((ApiRuntimeException) e).getCode())
                .isEqualTo(ShareErrorCode.UPDATE_FAIL);
    }

    @Test
    @DisplayName("삭제 — 받은 판단용 DTO 값으로 대상 계산·삭제, 같은 공유 조회를 다시 하지 않는다(D4)")
    void deleteShare_usesGivenDto() {
        var dto = new SharedResourceData(SHARE_SEQ, RESOURCE_SEQ, OWNER, 1, ShareType.SPECIFIC_USERS, ResourceType.FILE, "a.txt");
        given(shareTargetService.calculateTargetsDeleteResult(SHARE_SEQ, OWNER, ShareType.SPECIFIC_USERS, ResourceType.FILE, RESOURCE_SEQ))
                .willReturn(ShareTargetDeletionResult.empty());
        var deleteQuery = ShareDeleteQuery.builder().shareSeq(SHARE_SEQ).userSeq(OWNER).build();
        given(shareConverter.toShareDeleteQuery(SHARE_SEQ, OWNER)).willReturn(deleteQuery);
        given(shareRepository.delete(deleteQuery)).willReturn(1);

        var result = shareService.deleteShare(dto);

        assertThat(result.targetDeleteResult().resourceSeq()).isEqualTo(RESOURCE_SEQ);
        assertThat(result.targetDeleteResult().fileName()).isEqualTo("a.txt");
        assertThat(result.linkDeleteResult()).as("SPECIFIC_USERS 는 링크 채널 없음").isNull();
        verify(shareRepository).delete(deleteQuery);
        verify(shareFindService, never()).findSharedResourceByShare(anyLong(), anyInt(), anyBoolean());
    }
}

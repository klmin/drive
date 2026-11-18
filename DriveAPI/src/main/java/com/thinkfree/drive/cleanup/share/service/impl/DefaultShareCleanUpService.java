package com.thinkfree.drive.cleanup.share.service.impl;

import com.thinkfree.drive.cleanup.share.dto.ShareCleanUpDto;
import com.thinkfree.drive.cleanup.share.dto.ShareExternalCleanUpDto;
import com.thinkfree.drive.cleanup.share.enums.CleanUpShareErrorCode;
import com.thinkfree.drive.cleanup.share.service.ShareCleanUpService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.partition.UserResourceGroup;
import com.thinkfree.drive.pinnedfolder.query.PinnedFolderDeleteResourcesQuery;
import com.thinkfree.drive.pinnedfolder.service.PinnedFolderService;
import com.thinkfree.drive.recent.service.RecentService;
import com.thinkfree.drive.starred.service.StarredService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class DefaultShareCleanUpService implements ShareCleanUpService {
    
    private final StarredService starredService;
    private final RecentService recentService;
    private final PinnedFolderService pinnedFolderService;
    
    /**
     * 링크 방식 단건(파일) 정리. 삭제 유저는 SQL 조건으로 결정:
     * USER_SEQ != 소유주 AND USER_SEQ NOT IN preserveUserSeqSet(잔존 target).
     * 파일은 핀 불가라 최근/중요만.
     */
    private void deleteLinkShareCascade(long resourceSeq, int sharedByUserSeq, Set<Integer> preserveUserSeqSet) {
        starredService.deleteByResourceExcludingOwner(resourceSeq, sharedByUserSeq, sharedByUserSeq, preserveUserSeqSet);
        recentService.deleteByResourceExcludingOwner(resourceSeq, sharedByUserSeq, sharedByUserSeq, preserveUserSeqSet);
    }

    /**
     * 링크 방식 범위(폴더 + 접근 상실 하위 목록) 정리. 삭제 조건은 단건과 동일(소유주·잔존 target 제외 전원).
     * 폴더 범위라 핀폴더도 함께 정리.
     */
    private void deleteLinkSharesCascade(Collection<Long> resourceSeqList, int sharedByUserSeq, Set<Integer> preserveUserSeqSet) {
        starredService.deleteByResourcesExcludingOwner(resourceSeqList, sharedByUserSeq, sharedByUserSeq, preserveUserSeqSet);
        recentService.deleteByResourcesExcludingOwner(resourceSeqList, sharedByUserSeq, sharedByUserSeq, preserveUserSeqSet);
        var excludeUserSeqList = preserveUserSeqSet == null || preserveUserSeqSet.isEmpty() ? null : List.copyOf(preserveUserSeqSet);
        var pinnedFolderDeleteResourcesQuery = new PinnedFolderDeleteResourcesQuery(sharedByUserSeq, sharedByUserSeq, List.copyOf(resourceSeqList), excludeUserSeqList, null);
        pinnedFolderService.unpinFolderByResourcesExcludingOwner(pinnedFolderDeleteResourcesQuery);
    }

    /**
     * 대상 방식 파일 정리. partitionKeys = (빠진 대상 userSeq, 공유 리소스 seq) 쌍 —
     * (USER_SEQ, RESOURCE_SEQ) IN (...) 정밀 열거 삭제라 열거된 유저 외에는 영향 없음.
     */
    private void deleteShareTargetsCascade(Collection<PartitionKey> partitionKeys, int sharedByUserSeq) {
        starredService.deleteByShareTargets(sharedByUserSeq, partitionKeys);
        recentService.deleteByShareTargets(sharedByUserSeq, partitionKeys);

    }

    /**
     * 대상 방식 폴더 정리. userResourceGroups = 빠진 유저별 "본인이 접근을 잃는 하위 리소스 목록" —
     * 유저별 (USER_SEQ = 유저 AND RESOURCE_SEQ IN 목록) 삭제. 핀폴더 포함.
     */
    public void deleteTargetShareCascade(Collection<UserResourceGroup> userResourceGroups, int sharedByUserSeq) {
        
        if(userResourceGroups == null || userResourceGroups.isEmpty()){
            return;
        }
        
        starredService.deleteByUserResources(userResourceGroups, sharedByUserSeq);
        recentService.deleteByUserResources(userResourceGroups, sharedByUserSeq);
        pinnedFolderService.unpinFolderByUserResources(userResourceGroups, sharedByUserSeq);
        
    }
    
    /**
     * 공유 해제/수정으로 발생한 메타(최근/중요/핀) 정리분 삭제.
     * <p>
     * dto.shareType 은 실제 공유 유형이 아니라 <b>삭제 방식 라우팅 키</b> — 같은 dto 필드도 방식에 따라 의미가 다르다:
     * <ul>
     * <li><b>ORG_LINK/PUBLIC_LINK (링크 방식)</b> — 접근자 열거 불가 → 리소스 범위로 삭제.
     *     partitionKeys = 정리할 리소스 목록(userSeq 칸은 소유주 운반용, 조건으로 안 씀).
     *     삭제 유저는 SQL 에서 "소유주 제외 + preserveUserSeqSet(잔존 target) 제외 전원"으로 결정.</li>
     * <li><b>SPECIFIC_USERS (대상 방식)</b> — 빠진 유저가 열거됨 → 정밀 삭제.
     *     파일: partitionKeys = (빠진 유저, 리소스) 쌍 / 폴더: userResourceGroups = 유저별 접근 상실 하위 목록.
     *     preserveUserSeqSet 불필요(접근을 잃는 유저만 이미 골라져 들어옴 — keepsLinkAccess/상위 대상 게이트 통과분).</li>
     * </ul>
     * deleteDescendants = 폴더(하위 포함) 정리 여부 — 어느 필드를 소비할지 결정하는 분기 키.
     * 빈 컨텍스트(partitionKeys/userResourceGroups 빈)면 자연스럽게 no-op.
     * LINK — 파일: PartitionKey의 resourceSeq + 소유주 / 폴더: PartitionKey들의 resourceList + 소유주
     *  (소유주는 partitionKey의 userSeq칸이 아니라 dto의 sharedByUserSeq 별도 필드에서 옴 — partitionKey에선 resource만 의미)
     *  (실제 삭제 = 그 리소스에서 소유주 제외 + preserve 제외 전원)
     * SPECIFIC — 파일: PartitionKey의 공유 잃는 유저 + 리소스 / 폴더: UserResourceGroup의 공유 잃는 유저 + 접근 잃는 리소스 목록
     *  (preserve 불필요 — 잃는 유저만 정확히 열거돼 들어오므로)
     */
    @Override
    public void deleteShareCascade(ShareCleanUpDto dto) {

        var sharedByUserSeq = dto.sharedByUserSeq();

        switch (dto.shareType()){

            case ORG_LINK, PUBLIC_LINK -> {

                // 폴더: partitionKeys 에서 리소스 목록만 뽑아 범위 삭제 (소유주·잔존 target 제외 전원)
                if(dto.deleteDescendants()){
                    var resourceSeqList = dto.partitionKeys().stream().map(PartitionKey::resourceSeq).toList();
                    deleteLinkSharesCascade(resourceSeqList, sharedByUserSeq, dto.preserveUserSeqSet());
                    return;
                }

                // 파일: 리소스 1건 (linkFile 컨텍스트는 항상 단건)
                if(!dto.partitionKeys().isEmpty()){
                    deleteLinkShareCascade(dto.partitionKeys().get(0).resourceSeq(), sharedByUserSeq, dto.preserveUserSeqSet());
                }
            }

            case SPECIFIC_USERS -> {

                // 폴더: 빠진 유저별 접근 상실 하위 목록으로 삭제
                if(dto.deleteDescendants()){
                    deleteTargetShareCascade(dto.userResourceGroups(), dto.sharedByUserSeq());
                    return;
                }

                // 파일: (빠진 유저, 리소스) 쌍 정밀 삭제
                if(!dto.partitionKeys().isEmpty()){
                    deleteShareTargetsCascade(dto.partitionKeys(), sharedByUserSeq);
                }

            }

            default -> throw ApiRuntimeException.type(CleanUpShareErrorCode.CLEAN_UP_SHARE_INVALID_SHARE_TYPE);
        }


    }

    /**
     * 전체공개(PUBLIC) → 조직링크(ORG) 축소 전용 정리.
     * <p>
     * 축소로 접근을 잃는 건 외부 테넌트 유저뿐이므로, partitionKeys(리소스 범위 — userSeq 칸은 운반용)에 대해
     * "USER_SEQ NOT IN (소유주 테넌트 유저) AND NOT IN preserveUserSeqSet(잔존 target)" 조건으로 삭제.
     * 같은 테넌트 유저는 테넌트 조건에 걸리지 않아 자동 보존(조직 링크 접근 유지),
     * 외부 유저라도 교체 후 target 으로 남으면 preserve 로 보존.
     */
    @Override
    public void deleteOtherTenantShareCascade(ShareExternalCleanUpDto dto) {

        if(dto.partitionKeys() == null || dto.partitionKeys().isEmpty()){
            return;
        }

        var sharedByUserSeq = dto.sharedByUserSeq();
        var ownerTenantSeq = dto.ownerTenantSeq();
        var preserveUserSeqSet = dto.preserveUserSeqSet();
        var resourceSeqList = dto.partitionKeys().stream().map(PartitionKey::resourceSeq).toList();

        starredService.deleteOtherTenantByResources(resourceSeqList, sharedByUserSeq, ownerTenantSeq, preserveUserSeqSet);
        recentService.deleteOtherTenantByResources(resourceSeqList, sharedByUserSeq, ownerTenantSeq, preserveUserSeqSet);

        // 핀폴더는 폴더 공유(하위 포함 정리)일 때만 존재
        if(dto.deleteDescendants()){
            var excludeUserSeqList = preserveUserSeqSet == null || preserveUserSeqSet.isEmpty() ? null : List.copyOf(preserveUserSeqSet);
            var query = new PinnedFolderDeleteResourcesQuery(null, sharedByUserSeq, resourceSeqList, excludeUserSeqList, ownerTenantSeq);
            pinnedFolderService.unpinOtherTenantByResources(query);
        }
    }
}

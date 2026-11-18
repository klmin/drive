package com.thinkfree.drive.permission.service;

import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.permission.converter.PermissionConverter;
import com.thinkfree.drive.permission.data.ResourcePermissionData;
import com.thinkfree.drive.permission.data.ResourceShareChainData;
import com.thinkfree.drive.permission.data.ResourceShareChainItem;
import com.thinkfree.drive.permission.enums.PermissionErrorCode;
import com.thinkfree.drive.permission.model.ResourcePermission;
import com.thinkfree.drive.permission.model.ResourcePermissionWithFarthest;
import com.thinkfree.drive.permission.model.ResourceShareFarthest;
import com.thinkfree.drive.permission.repository.ResourcePermissionQueryRepository;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResourcePermissionFindService {

    private final ResourcePermissionQueryRepository resourcePermissionQueryRepository;
    private final PermissionConverter permissionConverter;

    public ResourcePermissionData findResourcePermissionData(int userSeq, PartitionKey key, Boolean isDelete){
        var query = permissionConverter.toResourcePermissionQuery(userSeq, key, isDelete);
        var data = resourcePermissionQueryRepository.findResourcePermission(query);
        DriveAssert.throwIfNull(data, PermissionErrorCode.RESOURCE_NOT_SHARED);
        return data;
    }

    public List<ResourcePermissionData> findResourcesPermissionData(int userSeq, List<PartitionKey> keys, Boolean isDelete){
        var query = permissionConverter.toResourcesPermissionQuery(userSeq, keys, isDelete);
        return resourcePermissionQueryRepository.findResourcePermissions(query);
    }

    public ResourceShareChainData findResourceShareChainData(int userSeq, PartitionKey key, Boolean isDelete){
        var query = permissionConverter.toResourcePermissionQuery(userSeq, key, isDelete);
        var data = resourcePermissionQueryRepository.findResourceShareChain(query);
        DriveAssert.throwIfNull(data, PermissionErrorCode.RESOURCE_NOT_SHARED);
        return data;
    }

    public List<ResourceShareChainData> findResourcesShareChainData(int userSeq, List<PartitionKey> keys, Boolean isDelete){
        var query = permissionConverter.toResourcesPermissionQuery(userSeq, keys, isDelete);
        return resourcePermissionQueryRepository.findResourcesShareChain(query);
    }

    public ResourcePermission findResourcePermission(int userSeq, PartitionKey key, Boolean isDelete){
        var data = findResourcePermissionData(userSeq, key, isDelete);
        return ResourcePermission.from(data, userSeq);
    }

    public Map<PartitionKey, ResourcePermission> findResourcesPermissionMap(int userSeq, List<PartitionKey> keys, Boolean isDelete){
        var list = findResourcesPermissionData(userSeq, keys, isDelete);
        return list.stream()
                   .map(r -> ResourcePermission.from(r, userSeq))
                   .collect(Collectors.toMap(
                        rp -> new PartitionKey(rp.getSharedByUserSeq(), rp.getResourceSeq()),
                        Function.identity()
                   ));

    }

    public ResourceShareFarthest findResourceShareFarthest(int userSeq, PartitionKey key, Boolean isDelete){
        var data = findResourceShareChainData(userSeq, key, isDelete);
        return createResourceShareFarthest(data, userSeq);
    }

    public Map<PartitionKey, ResourceShareFarthest> findResourcesShareFarthestMap(int userSeq, List<PartitionKey> keys, Boolean isDelete){
        var list = findResourcesShareChainData(userSeq, keys, isDelete);
        return list.stream()
                   .map(r -> createResourceShareFarthest(r, userSeq))
                   .collect(Collectors.toMap(
                        r -> new PartitionKey(r.getSharedByUserSeq(), r.getResourceSeq()),
                            Function.identity()
                   ));
    }

    /**
     * 없으면 예외 대신 null을 반환한다.
     * 조회 실패가 트랜잭션을 되돌려선 안 되는 경우(감사 로그 경로 조회 등)에 사용한다.
     */
    public ResourceShareFarthest findResourceShareFarthestOrNull(int userSeq, PartitionKey key, Boolean isDelete){
        var query = permissionConverter.toResourcePermissionQuery(userSeq, key, isDelete);
        var data = resourcePermissionQueryRepository.findResourceShareChain(query);
        return data == null ? null : createResourceShareFarthest(data, userSeq);
    }

    public ResourcePermissionWithFarthest findResourcePermissionWithFarthest(int userSeq, PartitionKey key, Boolean isDelete){
        var resourceShareFarthest = findResourceShareFarthest(userSeq, key, isDelete);
        var resourcePermission = findResourcePermission(userSeq, key, isDelete);
        return ResourcePermissionWithFarthest.from(resourceShareFarthest, resourcePermission);
    }

    public Map<PartitionKey, ResourcePermissionWithFarthest> findResourcesPermissionWithFarthestMap(int userSeq, List<PartitionKey> keys, Boolean isDelete){
        var list = findResourcesPermissionData(userSeq, keys, isDelete);
        var resourcesShareFarthestMap = findResourcesShareFarthestMap(userSeq, keys, isDelete);
        // 두 조회는 문장마다 시점이 달라(READ COMMITTED) 사이에 삭제·비활성화된 리소스는 사슬 맵에 없다.
        // 그 항목은 맵에서 빼고, 호출부는 null(권한·경로 없음)로 받는다 — 권한 조회 전에 삭제된 경우와 같은 처리.
        return list.stream()
                                   .map(d -> {
                                       PartitionKey partitionKey = new PartitionKey(d.getUserSeq(), d.getResourceSeq());
                                       ResourceShareFarthest resourceShareFarthest = resourcesShareFarthestMap.get(partitionKey);
                                       if(resourceShareFarthest == null){
                                           return null;
                                       }
                                       ResourcePermission resourcePermission = ResourcePermission.from(d, userSeq);
                                       return ResourcePermissionWithFarthest.from(resourceShareFarthest, resourcePermission);
                                   })
                                   .filter(Objects::nonNull)
                                   .collect(Collectors.toMap(
                                           r -> new PartitionKey(r.getSharedByUserSeq(), r.getResourceSeq()),
                                        Function.identity()
                                   ));

    }

    private ResourceShareFarthest createResourceShareFarthest(ResourceShareChainData data, int requestUserSeq) {

        var userSeq = data.getUserSeq();

        // 소유자 본인: 자기 리소스라 절대경로 전체 노출
        if(userSeq == requestUserSeq){
            return ResourceShareFarthest.forOwner(data.getChains().get(0));
        }

        // chains: depth 0(리소스 자신) → 상위 조상 순.
        // 내가 접근할 수 있는 공유가 "연속"된 구간의 가장 바깥 조상을 찾아(farthest),
        // 그 조상 기준 상대경로만 from() 에서 만든다(그 위 소유자 사적 경로는 숨김).
        //   farthest == null : 시작점 탐색 단계
        //   farthest != null : 시작점 이후, 연속 구간을 바깥으로 확장하는 단계
        ResourceShareChainItem farthest = null;

        for(ResourceShareChainItem item : data.getChains()) {

            // 접근 근거는 둘 중 하나다. 공유 범위가 링크여도 대상자를 따로 지정할 수 있어 둘은 배타적이지 않다.
            //   - 이 조상에 내 직접 target 이 있다
            //   - ORG/PUBLIC 링크라 target 행 없이도 열려 있다
            // 남에게만 지정된 SPECIFIC 공유는 내 접근 근거가 아니므로 여기서 멈춰 그 위 경로를 숨긴다.
            boolean isMyTarget = item.getShareTargetSeq() != null;
            boolean isLink = item.getShareType() != null && item.getShareType().isPublic();
            boolean accessible = isMyTarget || isLink;

            if(farthest == null) {                  // [탐색] 내가 접근 가능한 첫 조상 찾기
                if(!accessible){
                    continue;                       //   근거 없음 → 상위로 계속 (리소스가 직접 공유가 아닐 수 있음)
                }
                farthest = item;                    //   시작점 확정
                continue;
            }

            if(!accessible){                        // [확장] 연속 구간이 끊김 → 멈춤
                break;
            }
            farthest = item;                        //   더 바깥 조상으로 갱신
        }

        // 시작점 없음(미공유 / 링크로만 접근) → 경로 없음
        if(farthest == null){
            return ResourceShareFarthest.noDirectShared(userSeq, data.getResourceSeq());
        }

        // 가장 바깥 조상 기준 상대경로 (그 조상이 리소스 자신이면 from() 내부에서 전부 null)
        return ResourceShareFarthest.from(farthest);
    }


}

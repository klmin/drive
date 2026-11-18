package com.thinkfree.drive.resource.version.repository;

import com.thinkfree.drive.resource.data.ResourceStorageKeyData;
import com.thinkfree.drive.resource.data.ResourceUsageSummaryData;
import com.thinkfree.drive.resource.version.data.ResourceVersionData;
import com.thinkfree.drive.resource.version.data.ResourceVersionWithResourceData;
import com.thinkfree.drive.resource.version.data.ResourceVersionKeyData;
import com.thinkfree.drive.resource.version.query.ResourceVersionFindQuery;
import com.thinkfree.drive.resource.version.query.ResourceVersionListQuery;

import java.util.List;

/**
 * 읽기 전용(FindService 가 주입). 버전 목록 DTO·단건(JdbcClient)·버전 키 DTO(JPQL 생성자 식 — 엔티티 반환 없음).
 * 쓰기와 쓰기 흐름 안의 읽기(FOR UPDATE 잠금·처리 중/만료 버전 조회)는 {@link ResourceVersionRepository}.
 */
public interface ResourceVersionQueryRepository {

    /** 버전 목록 — 이력(tb_resource_version, 상태 필터) UNION ALL 현재(tb_resource) + 드라이브 소유자·버전 등록자 조인, 소유 계정 노출 가능 상태, version_number DESC 페이지 */
    List<ResourceVersionData> findAll(ResourceVersionListQuery query);
    Long countAll(ResourceVersionListQuery query);
    List<ResourceVersionKeyData> findAllByResource(ResourceVersionListQuery query);
    List<ResourceVersionKeyData> findAllByResourceList(ResourceVersionListQuery query);
    ResourceVersionWithResourceData findResourceVersionWithResource(ResourceVersionFindQuery query);
    ResourceVersionKeyData findResourceVersion(ResourceVersionFindQuery query);
    List<ResourceStorageKeyData> findStorageKeysByUserSeqList(List<Integer> userSeqList);
    ResourceUsageSummaryData findUsageSummaryByUserSeq(int userSeq);
}

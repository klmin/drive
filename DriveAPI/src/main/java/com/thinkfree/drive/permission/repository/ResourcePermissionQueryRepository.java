package com.thinkfree.drive.permission.repository;

import com.thinkfree.drive.permission.data.ResourcePermissionData;
import com.thinkfree.drive.permission.data.ResourceShareChainData;
import com.thinkfree.drive.permission.query.ResourcePermissionQuery;
import com.thinkfree.drive.permission.query.ResourcesPermissionQuery;

import java.util.List;

/**
 * 리소스 권한 읽기 — {@code ResourcePermissionFindService} 가 주입. 쓰기 없음(조회 전용 리포라 Query 리포만 둔다).
 * 조상 재귀 CTE + 윈도우 함수 DTO 조회라 JPA 로 표현되지 않아 전부 JdbcClient.
 */
public interface ResourcePermissionQueryRepository {

    /** 리소스 1건의 가장 가까운 링크 공유(l)·요청자 대상 공유(t). 리소스가 없거나 소유 계정이 노출 불가면 null. */
    ResourcePermissionData findResourcePermission(ResourcePermissionQuery query);

    /** {@link #findResourcePermission} 의 N건(키 튜플 IN). 키가 비면 SQL 없이 빈 목록. */
    List<ResourcePermissionData> findResourcePermissions(ResourcesPermissionQuery query);

    /** 리소스 자신(depth 0)부터 조상까지의 공유 사슬(depth 순). 리소스가 없으면 null. */
    ResourceShareChainData findResourceShareChain(ResourcePermissionQuery query);

    /** {@link #findResourceShareChain} 의 N건(리소스별 1개, 첫 등장 순). 키가 비면 SQL 없이 빈 목록. */
    List<ResourceShareChainData> findResourcesShareChain(ResourcesPermissionQuery query);
}

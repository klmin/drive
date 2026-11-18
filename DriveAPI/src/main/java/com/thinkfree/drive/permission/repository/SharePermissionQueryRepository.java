package com.thinkfree.drive.permission.repository;

import com.thinkfree.drive.permission.data.SharePermissionData;
import com.thinkfree.drive.permission.query.SharePermissionQuery;

/**
 * 공유(share_seq) 기준 권한 읽기 — {@code SharePermissionFindService} 가 주입. 쓰기 없음. 조상 재귀 CTE DTO 조회라 JdbcClient.
 */
public interface SharePermissionQueryRepository {

    /** 공유 1건의 리소스에 대한 요청자의 가장 가까운 링크 공유(l)·대상 공유(t). 공유/리소스가 없거나(is_delete 불일치 포함) 소유 계정이 노출 불가면 null. */
    SharePermissionData findSharePermission(SharePermissionQuery query);
}

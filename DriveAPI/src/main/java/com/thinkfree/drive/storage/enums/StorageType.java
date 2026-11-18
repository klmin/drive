package com.thinkfree.drive.storage.enums;


import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 스토리지 종류({@code storage.type}). 빈 등록은 {@code @ConditionalOnStorageType}으로 고른다.
 * <ul>
 *   <li>접속: 셋 모두 S3 프로토콜 계층({@code S3ClientConfig}, {@code S3StorageService})을 공유하며
 *       {@code storage.connection.*} 하나로 붙는다. CEPH·RUSTFS는 endpoint 지정, S3는 region 지정</li>
 *   <li>용량 조회({@code StorageAdminService}): 종류별 구현. CEPH=대시보드 API({@code storage.admin.*}), S3·RUSTFS=무제한</li>
 * </ul>
 * S3 프로토콜이 아닌 종류를 추가할 때는 그 종류만 가진 별도 config·service를 띄운다.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public enum StorageType{
    CEPH,
    S3,
    RUSTFS
}

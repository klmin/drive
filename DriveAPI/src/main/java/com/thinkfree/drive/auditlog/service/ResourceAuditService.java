package com.thinkfree.drive.auditlog.service;

import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.resource.data.ResourceHierarchyData;
import com.thinkfree.drive.resource.data.ResourceDetailData;
import com.thinkfree.drive.resource.data.ResourceIdentity;
import com.thinkfree.drive.user.data.UserIdentity;

import java.util.List;
import java.util.function.Function;

/**
 * FILE_FOLDER 카테고리 감사 로그 기록의 단일 진입점. 호출부는 {@link com.thinkfree.drive.auditlog.support.ResourceAuditSupport}를
 * 직접 부르지 않고 이 서비스만 의존한다(Support는 내부 구현).
 *
 * <p>경로가 필요한 이벤트는 소유자가 아닌 행위자({@link com.thinkfree.drive.util.SecurityUtil}) 기준으로 경로를 조회해 기록한다.
 * 경로가 없는 영구 삭제는 {@link #recordPermanentDelete}로 조회 없이 기록한다.
 */
public interface ResourceAuditService {

    /**
     * 감사 로그 기록을 위한 리소스를 소유자 스코프로 조회한다.
     * 변경 전 이름/경로 기준이 필요한 FILE_RENAME에서 사용한다.
     */
    ResourceDetailData findResource(Long resourceSeq, Integer ownerSeq);

    /**
     * 리소스 경로를 조회하여 감사 로그를 기록한다.
     * <p>FOLDER_CREATE, FILE_OPEN, FILE_EDIT, FILE_PREVIEW, FILE_DOWNLOAD,
     * FILE_DELETE, FOLDER_DELETE, FILE_RESTORE, FOLDER_RESTORE
     */
    void record(EventName eventName, ResourceIdentity resource, Integer ownerSeq);

    /**
     * 행위자를 명시로 받아 감사 로그를 기록한다(웹오피스 전용).
     * 웹오피스는 시큐리티 필터를 타지 않아 SecurityContext가 없으므로 호출부가 조회한 User를 넘긴다.
     * 익명 접근은 시스템 공용 익명 계정 1건이라 모든 익명 행위자가 같은 값으로 기록된다.
     * <p>FILE_OPEN, FILE_PREVIEW, FILE_EDIT, FILE_DOWNLOAD
     */
    void record(EventName eventName, ResourceIdentity resource, Integer ownerSeq, UserIdentity actor);

    /**
     * 리소스와 경로를 조회하여 감사 로그를 기록한다. 호출부에 엔티티가 없을 때 사용한다.
     * <p>FILE_CREATE, FILE_UPLOAD, FILE_EDIT(덮어쓰기 업로드), FILE_COPY
     */
    void record(EventName eventName, Long resourceSeq, Integer ownerSeq);

    /**
     * 다중 다운로드 fan-out을 위한 리소스 경로를 조회하여 감사 로그를 기록한다.
     * <p>FOLDER_DOWNLOAD (단건 폴더 다운로드)
     */
    void record(EventName eventName, ResourceHierarchyData item, Integer ownerSeq);

    /**
     * 다중 다운로드 fan-out의 리소스 경로를 일괄 조회하여 감사 로그를 기록한다.
     * 항목별 이벤트는 {@code eventNameResolver}가 결정한다.
     * <p>FILE_DOWNLOAD, FOLDER_DOWNLOAD (멀티 다운로드)
     */
    void recordAll(List<ResourceHierarchyData> items, Integer ownerSeq,
                   Function<ResourceHierarchyData, EventName> eventNameResolver);

    /**
     * 이름 변경 감사 로그를 기록한다. 변경 후 이름은 {@code resource}에서 가져온다.
     * <p>FOLDER_RENAME
     */
    void recordRename(EventName eventName, ResourceIdentity resource, Integer ownerSeq, String beforeName);

    /**
     * 이름 변경 감사 로그를 기록한다. 변경 전/후 리소스 정보가 서로 다른 객체에 있을 때 사용한다
     * (FILE_RENAME은 변경 후 이름이 transient 엔티티에, 경로 기준은 변경 전 엔티티에 있음).
     * <p>FILE_RENAME
     */
    void recordRename(
            EventName eventName,
            Long resourceSeq,
            Long parentFolderSeq,
            String objectName,
            Integer ownerSeq,
            String beforeName
    );

    /**
     * 파일 이동 감사 로그를 기록한다. before/after는 부모 폴더 경로 + 파일명으로 조립한다.
     * <p>FILE_MOVE
     *
     * <p>이동 전 경로는 호출부가 이동 전에 캡처해서 넘긴다 — 이동이 끝나면 부모 폴더 seq가 이미 새 값이다.
     *
     * @param oldParentPath    이동 전 부모 폴더 경로 (이동 전 캡처분)
     * @param oldResourceName  이동 전 리소스 이름 (이동 위치에 같은 이름이 있으면 구분 번호가 붙어 바뀐다)
     * @param targetOwnerSeq   이동한 곳의 소유자. null이면 소유자 변경이 없는 이동
     */
    void recordMove(
            ResourceIdentity resource,
            String oldParentPath,
            String oldResourceName,
            Long newParentFolderSeq,
            Integer sourceOwnerSeq,
            Integer targetOwnerSeq
    );

    /**
     * 영구 삭제 감사 로그를 기록한다. 리소스가 이미 삭제돼 경로가 없으므로 조회 없이 seq/이름만 남긴다.
     * <p>FILE_PERMANENT_DELETE, FOLDER_PERMANENT_DELETE (사용자 휴지통 영구 삭제 fan-out)
     *
     * <p>다른 메서드와 달리 엔티티가 아닌 seq/이름 스칼라로 받는 이유: 호출부 소스 타입이 제각각이고
     * (ResourceBrief · TrashCleanUpResourceItem · ResourceDeleteParentData) 공통 엔티티가 없어, 최소 식별자로 통일한다.
     */
    void recordPermanentDelete(EventName eventName, Long resourceSeq, String resourceName);

    /**
     * 영구 삭제 감사 로그를 기록한다(스케줄러 SYSTEM 자동정리 전용).
     * 요청/인증 컨텍스트가 없어 actor(슈퍼어드민)와 tenantSeq(대상 소유 테넌트)를 명시로 넘긴다.
     */
    void recordPermanentDelete(EventName eventName, Long resourceSeq, String resourceName, Integer tenantSeq, UserIdentity actor);

    /**
     * 행위자 기준 리소스 경로를 조회한다.
     *
     * <p>경로 조회와 기록을 분리해야 하는 경우에 사용한다.
     * <p>FILE_MOVE (이동 전 경로 캡처),
     * SHARE_CREATE / SHARE_REMOVE / SHARE_PERMISSION_CHANGE (1회 조회분을 대상자 N건에 재사용)
     *
     * @param resourceSeq 경로를 조회할 리소스 seq
     * @param parentFolderSeq 리소스의 부모 폴더 seq
     * @param ownerSeq 리소스 소유자 seq
     * @return 표시용 리소스 경로. 루트 또는 링크 전용 접근인 경우 {@code null}
     */
    String findResourcePath(Long resourceSeq, Long parentFolderSeq, Integer ownerSeq);

    /**
     * 위치 루트(내 드라이브/공유 문서함)까지 붙인 표시용 경로를 조회한다.
     * <p>SHARE_CREATE / SHARE_REMOVE / SHARE_PERMISSION_CHANGE
     *
     * @return 예 {@code "MY/영업팀/2026"}. 루트 항목이면 {@code "MY"}, 위치·경로가 모두 불명이면 {@code null}
     */
    String findDisplayPath(Long resourceSeq, Long parentFolderSeq, Integer ownerSeq);
}

package com.thinkfree.drive.resource.service;

import com.thinkfree.drive.resource.data.ResourceDetailData;
import com.thinkfree.drive.resource.dto.ResourceMoveDto;
import com.thinkfree.drive.resource.dto.ResourceRenameDto;
import com.thinkfree.drive.resource.dto.ResourceRestoreDto;
import com.thinkfree.drive.resource.dto.file.FileUploadDto;
import com.thinkfree.drive.resource.dto.folder.FolderUploadDto;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.query.ResourceDeleteSeqListQuery;
import com.thinkfree.drive.resource.query.ResourceDeleteStateQuery;
import com.thinkfree.drive.resource.query.ResourceRevertOverWriteQuery;
import com.thinkfree.drive.resource.result.*;

import java.util.List;

public interface ResourceService {
    /** 버전 작업 전 미삭제 리소스 행 잠금(값). 없으면 RESOURCE NOT_FOUND */
    ResourceDetailData lockResourceForVersioning(long resourceSeq, int userSeq);
    /**
     * 휴지통 보내기·영구 삭제 전 잠금 + 잠김 확인 — 호출부 트랜잭션 안에서, 잠금이 끝까지 유지된다.
     * 1) 대상 행 FOR UPDATE + 상세(값): 미삭제·소유 계정 노출 가능, type 이 null 이 아니면 종류 일치까지 — 아니면 RESOURCE NOT_FOUND
     * 2) 폴더면 하위 미삭제 행 FOR UPDATE  3) 잠김 확인(폴더면 하위까지) — 잠겨 있으면 RESOURCE_LOCKED
     * 값(DTO)으로 돌려주는 이유: 뒤이은 휴지통 이동·삭제가 벌크 JPQL 이라 관리 엔티티로 쥐면 캐시가 옛 값이 된다.
     */
    ResourceDetailData lockActiveUnlocked(long resourceSeq, int ownerSeq, ResourceType type);
    ResourceSaveResult saveWithCopyCheck(FileUploadDto dto);
    /** 폴더 생성(복사본 이름 처리) — 저장된 폴더의 식별 값(감사 인자). 경로는 커밋 때 변경 감지 */
    ResourceFolderCreateResult saveFolderWithCopyCheck(FolderUploadDto dto);
    ResourceSaveResult updateWithVersioning(FileUploadDto dto);
    ResourceSaveResult updateWithOverwrite(FileUploadDto dto);
    void rollbackVersion(ResourceSaveResult result);
    void delete(long resourceSeq, int userSeq);
    ResourceDeleteResult deleteAllVersions(long resourceSeq, int userSeq);
    ResourceDeleteResult deleteResourceAndVersions(long resourceSeq, int userSeq);
    ResourceDeleteResult deleteResourcesAndVersions(long resourceSeq, int userSeq);
    /** 파일 휴지통 이동(삭제 여부 Y) — 관리 엔티티 변경 감지. 없으면 DELETE_FAIL */
    void updateIsDeleteByFile(long resourceSeq, int userSeq);
    void updateIsDeleteByFullPathSeq(ResourceDeleteStateQuery query);
    void folderRenameWithChildFolders(ResourceRenameDto dto);
    ResourceMoveResult fileMove(ResourceMoveDto dto);
    ResourceMoveResult folderMove(ResourceMoveDto dto);
    void restoreFile(ResourceRestoreDto dto);
    void restoreFolder(ResourceRestoreDto dto);
    void revertOverwrite(ResourceRevertOverWriteQuery query);
    void deleteByResourceSeqList(ResourceDeleteSeqListQuery query);
    ResourceDeleteVersionResult deleteVersion(long resourceSeq, long resourceVersionSeq, int userSeq);
    void deleteByUsers(List<Integer> userSeqList);
    ResourceRenameResult resolveName(ResourceRenameDto dto);
}

package com.thinkfree.drive.application.file;

import com.thinkfree.drive.application.file.command.FileCopyCommand;
import com.thinkfree.drive.application.file.command.FileUploadCommand;
import com.thinkfree.drive.application.file.converter.FileCommandConverter;
import com.thinkfree.drive.application.file.info.FileCopyInfo;
import com.thinkfree.drive.application.file.info.FileUploadInfo;
import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.file.write.orchestrator.FileWriteOrchestrator;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.messagesource.service.MessageSourceService;
import com.thinkfree.drive.permission.enums.PermissionErrorCode;
import com.thinkfree.drive.permission.enums.SharePermissionType;
import com.thinkfree.drive.permission.support.RequestActor;
import com.thinkfree.drive.permission.service.ResourcePermissionService;
import com.thinkfree.drive.resource.dto.file.FileContentSource;
import com.thinkfree.drive.resource.enums.ResourceSaveType;
import com.thinkfree.drive.resource.exception.ResourceErrorCode;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.util.MapStructUtil;
import com.thinkfree.drive.util.ResourceUtil;
import com.thinkfree.drive.valid.DriveAssert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static com.thinkfree.drive.messagesource.keys.MessageKeys.FILE_COPY_SUFFIX;

/**
 * 진입점(내부 API·MCP) 공용 파일 usecase — 업로드/복제.
 * 삭제(휴지통 이동)는 휴지통 수명주기 소속이라 {@link com.thinkfree.drive.application.trash.TrashUseCase#moveFileToTrash}로,
 * 이동·이름변경은 타입 무관한 리소스 소속이라 {@link com.thinkfree.drive.application.resource.ResourceUseCase#moveFile}·
 * {@link com.thinkfree.drive.application.resource.ResourceUseCase#renameFile}로 이관.
 * 입출력은 application 소유의 Command/Info만 사용하며, 어댑터 객체(api.v1 request/response)를 알지 않는다.
 * 다운로드·미리보기(스트리밍·토큰)는 전송 결합이라 {@link FileDownloadUseCase}로 분리.
 * 권한 검사는 어댑터 책임 — 웹은 controller aspect(@CheckResourcePermission), MCP는 툴의 명시 호출.
 * 잠김 검사는 이동·이름 변경·휴지통 이동에서 도메인(ResourceService)이 행 잠금 뒤 같은 트랜잭션에서 한다. 단 upload는 저장 유형별 검사 분기가 usecase 안에 있다(웹 시절과 동일).
 * CustomUserDetails는 (userSeq, anonymous)·(userSeq, userName)으로 해체해 받는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileUseCase {

    private final FileCommandConverter fileCommandConverter;
    private final ResourceFindService resourceFindService;
    private final MessageSourceService messageSourceService;
    private final ResourcePermissionService resourcePermissionService;
    private final FileWriteOrchestrator fileWriteOrchestrator;
    private final ResourceAuditService resourceAuditService;

    /**
     * @param fromIntellect 인텔릭트 편집 화면에서 온 저장 요청 여부 — 감사 이벤트 구분용
     */
    @AuditLog
    public FileUploadInfo upload(FileUploadCommand command, int requestUserSeq, boolean anonymous, boolean fromIntellect) {

        var fileUploadDto = fileCommandConverter.toUploadDto(command, requestUserSeq);
        int userSeq = fileUploadDto.getUserSeq();
        var resourceSaveType = fileUploadDto.getResourceSaveType();

        RequestActor actor = RequestActor.of(requestUserSeq, anonymous);
        if(resourceSaveType.isDuplicate()){
            resourcePermissionService.checkPermission(SharePermissionType.UPLOAD, fileUploadDto.getParentFolderSeq(), fileUploadDto.getUserSeq(), actor, false);
        }else{
            validResourceSeq(fileUploadDto.getResourceSeq());
            resourcePermissionService.checkPermission(SharePermissionType.EDIT, fileUploadDto.getResourceSeq(), fileUploadDto.getUserSeq(), actor, false);
        }

        // 익명인 경우 Overwrite 허용 (인텔릭트 편집 가능하도록)
        DriveAssert.throwIfTrue(!resourceSaveType.isOverwrite() && anonymous, PermissionErrorCode.ACCESS_DENIED);

        // 저장 유형별 핸들러(FileWriteService)로 위임 — 크기 정책·쿼터·롤백(스토리지 실패만)·후처리(비동기 이벤트)는
        // 오케스트레이터+핸들러 소유. VERSION 도 5차 패스(D1)부터 같은 경로. MCP drive_upload_file(FileContentUploadUseCase)과 공유한다.
        var result = fileWriteOrchestrator.write(fileUploadDto, requestUserSeq);
        recordUploadEvent(resourceSaveType, fromIntellect, result.resourceSeq(), userSeq);
        return new FileUploadInfo(result.resourceSeq(), result.resourceName());
    }

    /** 덮어쓰기와 인텔릭트 닫기 저장(VERSION)은 기존 파일 내용 수정이라 편집, 나머지는 업로드로 기록한다. */
    private void recordUploadEvent(ResourceSaveType resourceSaveType, boolean fromIntellect, Long resourceSeq, int userSeq) {
        boolean edit = resourceSaveType.isOverwrite() || (resourceSaveType.isVersion() && fromIntellect);
        resourceAuditService.record(edit ? EventName.FILE_EDIT : EventName.FILE_UPLOAD, resourceSeq, userSeq);
    }

    /**
     * 파일 복사 — DUPLICATE 쓰기(FileWriteOrchestrator)에 새 내용 출처 = 원본의 스토리지 객체(5차 패스).
     * 대상 사용자 기준으로 크기 정책·사용량 예약·메타 저장(한 트랜잭션), 스토리지 복사 실패만 롤백, 사용량 알림은 비동기 이벤트.
     */
    @AuditLog
    public FileCopyInfo copy(Long resourceSeq, FileCopyCommand command, int requestUserSeq){

        int userSeq = MapStructUtil.resolveUserSeq(command.sharedByUserSeq(), requestUserSeq);
        int targetUserSeq = MapStructUtil.resolveUserSeq(command.targetUserSeq(), requestUserSeq);

        var query = fileCommandConverter.toFindQuery(resourceSeq, userSeq, false);
        var resource = resourceFindService.findDetail(query);

        String contentType = resource.getContentType();
        long contentLength = resource.getSizeByte();

        // 사본 확장자 = 원본 ext(이름의 마지막 점이 아님)
        String ext = resource.getExt() != null ? resource.getExt() : ResourceUtil.getFileExtension(resource.getResourceName());
        String originalFileNameWithoutExt = ResourceUtil.nameWithoutExtension(resource.getResourceName(), ext);
        String copyFileName = messageSourceService.getMessage(FILE_COPY_SUFFIX, new Object[]{ originalFileNameWithoutExt }) + ResourceUtil.generateExtension(ext);

        var fileUploadDto = fileCommandConverter.toCopyUploadDto(command.parentFolderSeq(), targetUserSeq, requestUserSeq, copyFileName, ext, contentType, contentLength)
                .toBuilder().contentSource(new FileContentSource.StorageCopy(resource.getResourceSaveName())).build();

        var result = fileWriteOrchestrator.write(fileUploadDto, requestUserSeq);

        resourceAuditService.record(EventName.FILE_COPY, result.resourceSeq(), targetUserSeq);

        return new FileCopyInfo(result.resourceSeq());

    }

    private void validResourceSeq(Long resourceSeq){
        if(resourceSeq == null){
            throw ApiRuntimeException.type(ResourceErrorCode.MISSING_RESOURCE_SEQ);
        }
    }

}

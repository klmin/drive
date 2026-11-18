package com.thinkfree.drive.application.folder;

import com.thinkfree.drive.application.folder.command.FolderCreateCommand;
import com.thinkfree.drive.application.folder.converter.FolderCommandConverter;
import com.thinkfree.drive.application.folder.info.FolderCreateInfo;
import com.thinkfree.drive.application.resource.ResourceNameRule;
import com.thinkfree.drive.auditlog.aspect.annotation.AuditLog;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.resource.service.ResourceRetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 진입점(내부 API·MCP) 공용 폴더 usecase — 생성.
 * 삭제(휴지통 이동)는 휴지통 수명주기 소속이라 {@link com.thinkfree.drive.application.trash.TrashUseCase#moveFolderToTrash}로,
 * 이동·이름변경은 타입 무관한 리소스 소속이라 {@link com.thinkfree.drive.application.resource.ResourceUseCase#moveFolder}·
 * {@link com.thinkfree.drive.application.resource.ResourceUseCase#renameFolder}로 이관.
 * 입출력은 application 소유의 Command/Info만 사용하며, 어댑터 객체(api.v1 request/response)를 알지 않는다.
 * 다운로드(스트리밍·토큰)는 전송 결합이라 {@link FolderDownloadUseCase}로 분리.
 * 권한 검사는 어댑터 책임 — 웹은 controller aspect(@CheckResourcePermission), MCP는 툴의 명시 호출.
 * 잠김 검사는 이동·이름 변경·휴지통 이동에서 도메인(ResourceService)이 행 잠금 뒤 같은 트랜잭션에서 한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FolderUseCase {

    private final FolderCommandConverter folderCommandConverter;
    private final ResourceRetryService resourceRetryService;
    private final ResourceAuditService resourceAuditService;

    @AuditLog
    public FolderCreateInfo create(FolderCreateCommand command, int requestUserSeq) {

        ResourceNameRule.validate(command.folderName());

        var dto = folderCommandConverter.toFolderUploadDto(command, requestUserSeq);

        try {
            var resource = resourceRetryService.saveFolderWithCopyCheck(dto);

            resourceAuditService.record(EventName.FOLDER_CREATE, resource, dto.getUserSeq());

            return new FolderCreateInfo(resource.getResourceSeq(), resource.getResourceName());
        } catch (ApiRuntimeException e) {
            log.error("[FolderUseCase saveFolderWithCopyCheck fail]", e);
            throw e;
        }
    }

}

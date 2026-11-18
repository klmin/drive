package com.thinkfree.drive.mcp.tool;

import com.thinkfree.drive.application.document.DocumentCreateUseCase;
import com.thinkfree.drive.application.document.command.DocumentCreateCommand;
import com.thinkfree.drive.application.file.FileContentUploadUseCase;
import com.thinkfree.drive.application.file.FileDownloadContentUseCase;
import com.thinkfree.drive.application.file.FileReadUseCase;
import com.thinkfree.drive.application.file.FileUseCase;
import com.thinkfree.drive.application.file.command.FileContentUploadCommand;
import com.thinkfree.drive.application.file.command.FileCopyCommand;
import com.thinkfree.drive.application.file.command.FileDownloadCommand;
import com.thinkfree.drive.application.file.command.FileReadCommand;
import com.thinkfree.drive.document.enums.DocumentTemplateType;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.service.ClientDomainService;
import com.thinkfree.drive.mcp.support.McpToolResult;
import com.thinkfree.drive.mcp.tool.converter.FileToolConverter;
import com.thinkfree.drive.mcp.tool.response.FileCopyToolResponse;
import com.thinkfree.drive.mcp.tool.response.FileCreateToolResponse;
import com.thinkfree.drive.mcp.tool.response.FileDownloadToolResponse;
import com.thinkfree.drive.mcp.tool.response.FileReadToolResponse;
import com.thinkfree.drive.mcp.tool.response.FileUploadToolResponse;
import com.thinkfree.drive.mcp.util.McpToolParams;
import com.thinkfree.drive.oauth2.enums.OAuth2Scope;
import com.thinkfree.drive.mcp.util.McpUrls;
import com.thinkfree.drive.permission.enums.SharePermissionType;
import com.thinkfree.drive.permission.service.ResourcePermissionService;
import com.thinkfree.drive.permission.support.RequestActor;
import com.thinkfree.drive.util.MapStructUtil;
import com.thinkfree.drive.weboffice.enums.WebOfficeEditableExtension;
import com.thinkfree.drive.mcp.security.McpToolRole;
import com.thinkfree.drive.mcp.security.McpToolScope;
import com.thinkfree.drive.user.enums.RoleType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 파일 MCP 툴 — application.file·application.document의 usecase를 내부 API와 동일하게 호출한다.
 * 이름변경·이동은 DriveResourceTools, 삭제는 DriveTrashTools의 몫이다.
 *
 * 웹 controller의 @CheckResourcePermission aspect는 MCP 경로에 적용되지 않으므로 동일한 검사
 * (사본 위치 UPLOAD + 원본 VIEW)를 툴 본문에서 명시 호출한다.
 * 감사 로그(@AuditLog·FILE_COPY)는 usecase에 있어 MCP 경로에도 동일하게 남는다.
 *
 * 에러 규약: 성공은 {data}, 예상된 실패(검증·권한·비즈니스 예외)는 McpToolResult.execute가
 * {error[, errorCode]} 인밴드 반환으로 변환하고, 예상 못한 예외는 전파시켜 프레임워크의 isError 처리에 맡긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@McpToolRole(RoleType.USER)
public class DriveFileTools {

    private final FileUseCase fileUseCase;
    private final FileContentUploadUseCase fileContentUploadUseCase;
    private final FileReadUseCase fileReadUseCase;
    private final FileDownloadContentUseCase fileDownloadContentUseCase;
    private final DocumentCreateUseCase documentCreateUseCase;
    private final ResourcePermissionService resourcePermissionService;
    private final FileToolConverter fileToolConverter;
    private final ClientDomainService clientDomainService;

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_copy_file",
            description = "드라이브의 파일 하나를 복제한다(폴더 복제는 지원하지 않는다). 원본 파일의 resourceSeq가 필요하다 — "
                    + "모르면 먼저 drive_search_resources 등 목록 툴로 찾는다. 사본 위치 지정 레시피: ① 내 드라이브 최상위에 "
                    + "만들 때는 parentFolderSeq 생략 ② 특정 폴더 안에 만들 때는 그 폴더의 resourceSeq를 parentFolderSeq로 지정 "
                    + "③ 공유받은 폴더 안에 만들 때는 parentFolderSeq와 함께 그 폴더 소유자의 userSeq(항목의 sharedByUserSeq가 있으면 그 값, 없으면 userSeq — ownerSeq 아님)를 "
                    + "targetUserSeq로 지정 — 업로드 권한(canUpload)이 있어야 한다. 원본에는 편집 권한(canEdit)이 있어야 한다. 원본이 공유받은 파일이면 원본 소유자의 "
                    + "userSeq를 sharedByUserSeq로 지정한다(내 파일이면 생략). 사본 이름은 서버가 자동으로 정하며"
                    + "(예: \"보고서.docx\" → \"보고서의 사본.docx\", 확장자는 원본 ext 그대로라 확장자 없는 파일은 붙이지 않는다), 응답은 사본의 resourceSeq 하나다 — 사본의 이름·경로가 "
                    + "필요하면 drive_get_resource_metadata로 조회한다. 파일 내용 수정·이름변경·이동·삭제는 이 툴이 하지 않는다.")
    public McpToolResult<FileCopyToolResponse> copyFile(
            @ToolParam(description = "복제할 원본 파일의 resourceSeq") Long resourceSeq,
            @ToolParam(description = "사본을 넣을 폴더의 resourceSeq. 내 드라이브 최상위에 만들면 생략",
                    required = false) Long parentFolderSeq,
            @ToolParam(description = "원본이 공유받은 파일일 때 원본 소유자의 userSeq (항목의 sharedByUserSeq가 있으면 그 값, 없으면 userSeq — ownerSeq 아님). "
                    + "내 파일이면 생략", required = false) Integer sharedByUserSeq,
            @ToolParam(description = "사본을 공유받은 폴더 안에 넣을 때 그 폴더 소유자의 userSeq (항목의 sharedByUserSeq가 있으면 그 값, 없으면 userSeq — ownerSeq 아님). "
                    + "내 드라이브에 넣으면 생략", required = false) Integer targetUserSeq) {

        log.info("[MCP] drive_copy_file called. resourceSeq : {}, parentFolderSeq : {}, sharedByUserSeq : {}, targetUserSeq : {}",
                resourceSeq, parentFolderSeq, sharedByUserSeq, targetUserSeq);

        return McpToolResult.executeAuthed(details -> {
            int requestUserSeq = details.getUserSeq();

            McpToolParams.require(resourceSeq, "resourceSeq");

            // 웹 파일 복제 API의 @CheckResourcePermission 2건과 동일한 검사 —
            // 사본 위치(pfs, ts 기준)에 UPLOAD + 원본(resourceSeq, ss 기준)에 EDIT
            RequestActor actor = RequestActor.of(requestUserSeq, details.isAnonymous());
            resourcePermissionService.checkPermission(SharePermissionType.UPLOAD, parentFolderSeq,
                    MapStructUtil.resolveUserSeq(targetUserSeq, requestUserSeq), actor, false);
            resourcePermissionService.checkPermission(SharePermissionType.EDIT, resourceSeq,
                    MapStructUtil.resolveUserSeq(sharedByUserSeq, requestUserSeq), actor, false);

            var command = FileCopyCommand.builder()
                    .sharedByUserSeq(sharedByUserSeq)
                    .targetUserSeq(targetUserSeq)
                    .parentFolderSeq(parentFolderSeq)
                    .build();
            return fileToolConverter.toCopyResponse(fileUseCase.copy(resourceSeq, command, requestUserSeq));
        });
    }

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_create_file",
            description = "새 문서 파일을 하나 생성한다. templateType은 word(docx), excel(xlsx), ppt(pptx), note(tfi 노트) "
                    + "중 하나다. 빈 템플릿으로 만들어지며 문서명은 서버가 계정 언어에 맞춰 자동 부여한다(예: \"새 문서.docx\") — "
                    + "같은 위치에 동명 문서가 있으면 실패하지 않고 번호를 붙인 이름으로 생성된다. 위치 지정 레시피: "
                    + "① 내 드라이브 최상위에 만들 때는 parentFolderSeq 생략 ② 특정 폴더 안에 만들 때는 그 폴더의 resourceSeq를 "
                    + "parentFolderSeq로 지정 ③ 공유받은 폴더 안에 만들 때는 parentFolderSeq와 함께 그 폴더 소유자의 "
                    + "userSeq(항목의 sharedByUserSeq가 있으면 그 값, 없으면 userSeq — ownerSeq 아님)를 targetUserSeq로 지정 — 업로드 권한(canUpload)이 있어야 한다. "
                    + "응답은 생성된 문서의 resourceSeq와 webViewUrl이다 — 실제 생성된 문서명·경로가 필요하면 "
                    + "drive_get_resource_metadata로 조회한다. 내용 작성·수정은 이 툴이 하지 않는다 — 생성 후 편집은 "
                    + "webViewUrl을 사용자에게 안내해 웹오피스에서 이어가게 한다.")
    public McpToolResult<FileCreateToolResponse> createFile(
            @ToolParam(description = "생성할 문서 종류: word | excel | ppt | note") String templateType,
            @ToolParam(description = "문서를 만들 폴더의 resourceSeq. 내 드라이브 최상위에 만들면 생략",
                    required = false) Long parentFolderSeq,
            @ToolParam(description = "공유받은 폴더 안에 만들 때 그 폴더 소유자의 userSeq (항목의 sharedByUserSeq가 있으면 그 값, 없으면 userSeq — ownerSeq 아님). "
                    + "내 드라이브에 만들면 생략", required = false) Integer targetUserSeq) {

        log.info("[MCP] drive_create_file called. templateType : {}, parentFolderSeq : {}, targetUserSeq : {}",
                templateType, parentFolderSeq, targetUserSeq);

        return McpToolResult.executeAuthed(details -> {
            int requestUserSeq = details.getUserSeq();

            McpToolParams.requireText(templateType, "templateType");
            // 대소문자 관용 파싱 — 미지 값(예: "hwp")은 IllegalArgumentException → 인밴드 {error}
            DocumentTemplateType type = DocumentTemplateType.from(templateType);

            // 웹 새 문서 생성 API의 @CheckResourcePermission(UPLOAD)과 동일한 검사 — 검사 대상은 부모 폴더(pfs).
            // 내 드라이브(소유자=요청자)는 즉시 통과라 parentFolderSeq=null(최상위)도 웹과 동일하게 허용된다
            resourcePermissionService.checkPermission(SharePermissionType.UPLOAD, parentFolderSeq,
                    MapStructUtil.resolveUserSeq(targetUserSeq, requestUserSeq),
                    RequestActor.of(requestUserSeq, details.isAnonymous()), false);

            var command = DocumentCreateCommand.builder()
                    .targetUserSeq(targetUserSeq)
                    .parentFolderSeq(parentFolderSeq)
                    .templateType(type)
                    .build();
            var info = documentCreateUseCase.create(command, requestUserSeq);

            // 웹 링크 조립은 어댑터 책임 — read/download와 동일한 /preview 딥링크 (웹오피스 편집 진입점)
            String webViewUrl = McpUrls.previewUrl(
                    clientDomainService.getDomain(details.getTenantSeq()), info.resourceSeq(), info.ownerUserSeq());
            return fileToolConverter.toCreateFileResponse(info, webViewUrl);
        });
    }

    @McpToolScope(OAuth2Scope.MCP_WRITE)
    @Tool(name = "drive_upload_file",
            description = "내용을 담은 새 파일을 드라이브에 업로드한다. fileName은 확장자를 포함한 파일명이고(그 확장자가 파일 형식 ext로 저장되며 이후 이름 변경으로 바뀌지 않는다), 내용은 "
                    + "textContent(UTF-8 평문 — 텍스트·코드·마크다운 등)나 base64Content(비텍스트 바이너리의 base64 인코딩) 중 "
                    + "정확히 하나로 넘긴다. contentType·크기는 서버가 확장자와 내용에서 판정하므로 보내지 않는다. "
                    + "내용 크기 상한은 512KB(디코딩 후 기준)다 — 이보다 큰 파일은 사용자에게 웹 업로드를 안내한다. "
                    + "내용이 매우 길면 모델 출력 한계로 잘릴 수 있다 — 응답의 sizeByte를 의도한 크기와 대조해 잘림을 확인하고, "
                    + "긴 문서는 나눠 올리지 말고(이어쓰기 없음) 사용자에게 웹 업로드를 안내한다. 위치 지정 레시피: "
                    + "① 내 드라이브 최상위는 parentFolderSeq 생략 ② 특정 폴더 안은 그 폴더의 resourceSeq를 parentFolderSeq로 지정 "
                    + "③ 공유받은 폴더 안은 parentFolderSeq와 함께 그 폴더의 접근 스코프 userSeq(항목의 sharedByUserSeq가 있으면 "
                    + "그 값, 없으면 userSeq — ownerSeq 아님)를 targetUserSeq로 지정 — 업로드 권한(canUpload)이 있어야 한다. "
                    + "같은 위치에 동명 파일이 있으면 실패하지 않고 번호를 붙인 이름으로 저장된다(응답 resourceName이 실제 이름). "
                    + "기존 파일 내용 수정(덮어쓰기)은 이 툴이 하지 않는다. 빈 오피스 문서 생성은 drive_create_file을 쓴다.")
    public McpToolResult<FileUploadToolResponse> uploadFile(
            @ToolParam(description = "확장자를 포함한 파일명 (예: \"회의록.md\")") String fileName,
            @ToolParam(description = "UTF-8 평문 내용. base64Content와 동시 지정 불가", required = false) String textContent,
            @ToolParam(description = "바이너리 내용의 base64 인코딩. textContent와 동시 지정 불가", required = false) String base64Content,
            @ToolParam(description = "파일을 넣을 폴더의 resourceSeq. 내 드라이브 최상위면 생략", required = false) Long parentFolderSeq,
            @ToolParam(description = "공유받은 폴더에 올릴 때 그 폴더의 접근 스코프 userSeq (항목의 sharedByUserSeq가 있으면 그 값, 없으면 userSeq — ownerSeq 아님). "
                    + "내 드라이브에 올리면 생략", required = false) Integer targetUserSeq) {

        log.info("[MCP] drive_upload_file called. fileName : {}, textLength : {}, base64Length : {}, parentFolderSeq : {}, targetUserSeq : {}",
                fileName, textContent == null ? null : textContent.length(),
                base64Content == null ? null : base64Content.length(), parentFolderSeq, targetUserSeq);

        return McpToolResult.executeAuthed(details -> {
            int requestUserSeq = details.getUserSeq();

            McpToolParams.requireText(fileName, "fileName");

            // 웹 업로드 API의 UPLOAD 검사와 동일 — 대상은 부모 폴더(pfs), 내 최상위(null)는 즉시 통과 (create_file 동형)
            int ownerSeq = MapStructUtil.resolveUserSeq(targetUserSeq, requestUserSeq);
            RequestActor actor = RequestActor.of(requestUserSeq, details.isAnonymous());
            resourcePermissionService.checkPermission(SharePermissionType.UPLOAD, parentFolderSeq, ownerSeq, actor, false);

            var command = new FileContentUploadCommand(fileName, textContent, base64Content, parentFolderSeq, targetUserSeq);
            var info = fileContentUploadUseCase.upload(command, requestUserSeq);

            // 웹 링크 조립은 어댑터 책임 — read와 동일한 /preview 딥링크, 오피스 편집 가능이면 editUrl 동봉
            String domain = clientDomainService.getDomain(details.getTenantSeq());
            String webViewUrl = McpUrls.previewUrl(domain, info.resourceSeq(), info.ownerUserSeq());
            String editUrl = isWebOfficeEditable(info.extension(), info.resourceSeq(), ownerSeq, actor)
                    ? McpUrls.editUrl(domain, info.resourceSeq(), info.ownerUserSeq())
                    : null;
            return fileToolConverter.toUploadResponse(info, webViewUrl, editUrl);
        });
    }

    @Tool(name = "drive_read_file",
            description = "파일의 내용을 읽는다. 텍스트 계열(txt, md, log, json, xml, yaml, yml, ini, conf, csv, tsv와 "
                    + "html, css, js, ts, java, py, c, cpp, php 등 코드 파일)과 "
                    + "문서 계열(doc, docx, xls, xlsx, ppt, pptx, odt, ods, odp, pdf, rtf, hwp, hwpx, cell, show — "
                    + "서버가 본문 텍스트를 추출)은 본문이 text 필드에 평문으로 반환된다. 그 외 형식(이미지·압축 등)과 크기 초과 파일은 "
                    + "text가 null이고 textUnavailableReason에 사유가 오는데, 이는 에러가 아니다 — 이때는 함께 반환되는 "
                    + "webViewUrl(웹 미리보기 링크)을 사용자에게 안내한다. webViewUrl은 항상 반환되며 사용자가 브라우저에서 "
                    + "열면 형식에 따라 미리보기 또는 웹오피스로 이어진다. 웹오피스에서 편집 가능한 파일(워드·셀·쇼 계열)이고 "
                    + "편집 권한(canEdit)이 있으면 editUrl(웹오피스 편집 링크)도 함께 반환된다 — 사용자가 내용 수정을 원하면 "
                    + "이 링크를 안내한다. editUrl이 없으면 편집 불가한 형식이거나 편집 권한이 없는 것이다. "
                    + "본문이 길면 일부만 반환되고 truncated=true가 "
                    + "온다 — 다음 조각은 offset을 직전 응답의 offset+returnedChars로 지정해 이어 읽는다. "
                    + "폴더는 읽을 수 없다. 잠긴(사용 중) 파일도 읽기는 가능하다. resourceSeq는 drive_search_resources 등 "
                    + "목록 툴 결과 항목에서 얻는다. 대상이 공유받은 파일이면 그 소유자의 userSeq(항목의 sharedByUserSeq가 있으면 그 값, 없으면 userSeq — ownerSeq 아님)를 "
                    + "sharedByUserSeq로 지정한다(내 파일이면 생략) — 보기 권한(canView)이 있어야 한다. "
                    + "성공 판정 기준은 응답에 error 필드가 없는 것이다.")
    public McpToolResult<FileReadToolResponse> readFile(
            @ToolParam(description = "내용을 읽을 파일의 resourceSeq (drive_search_resources 등 목록 툴 결과 항목의 resourceSeq)") Long resourceSeq,
            @ToolParam(description = "공유받은 파일일 때 그 소유자의 userSeq (항목의 sharedByUserSeq가 있으면 그 값, 없으면 userSeq — ownerSeq 아님). "
                    + "내 파일이면 생략", required = false) Integer sharedByUserSeq,
            @ToolParam(description = "이어 읽기 시작 문자 위치. 직전 응답이 truncated=true일 때 "
                    + "offset+returnedChars 값으로 지정한다. 처음 읽을 때는 생략", required = false) Integer offset) {

        log.info("[MCP] drive_read_file called. resourceSeq : {}, sharedByUserSeq : {}, offset : {}",
                resourceSeq, sharedByUserSeq, offset);

        return McpToolResult.executeAuthed(details -> {
            int requestUserSeq = details.getUserSeq();

            McpToolParams.require(resourceSeq, "resourceSeq");

            // 웹 파일 미리보기 API의 @CheckResourcePermission(VIEW, isDelete=ANY)과 동일한 검사 — ANY는 isDelete=null.
            // 잠금 검사는 없다 — 잠긴 파일도 읽기는 허용(웹 미리보기 동형)
            int ownerSeq = MapStructUtil.resolveUserSeq(sharedByUserSeq, requestUserSeq);
            RequestActor actor = RequestActor.of(requestUserSeq, details.isAnonymous());
            resourcePermissionService.checkPermission(SharePermissionType.VIEW, resourceSeq, ownerSeq, actor, null);

            var info = fileReadUseCase.read(new FileReadCommand(resourceSeq, sharedByUserSeq, offset), requestUserSeq);

            // 웹 링크 조립은 어댑터 책임 — 프런트 /preview 딥링크(?rs=&ss=, 홈 미리보기 플로우로 위임)가
            // 미리보기 다이얼로그를 열고 형식에 따라 미리보기/미리보기 없음/웹오피스로 분기한다 (기존 웹 플로우 재사용)
            String domain = clientDomainService.getDomain(details.getTenantSeq());
            String webViewUrl = McpUrls.previewUrl(domain, resourceSeq, info.ownerUserSeq());
            String editUrl = isWebOfficeEditable(info.extension(), resourceSeq, ownerSeq, actor)
                    ? McpUrls.editUrl(domain, resourceSeq, info.ownerUserSeq())
                    : null;
            return fileToolConverter.toReadResponse(info, webViewUrl, editUrl);
        });
    }
    
    @Tool(name = "drive_download_file",
            description = "파일의 원본 바이트를 base64 텍스트로 반환한다 — 원본 그대로가 필요할 때 쓰고, 내용 파악이 "
                    + "목적이면 drive_read_file을 쓴다. 형식 제한은 없고 크기 상한(10MB)만 있다 — 초과 파일은 "
                    + "base64Content가 null이고 contentUnavailableReason에 사유가 오는데, 이는 에러가 아니다 — 이때는 함께 "
                    + "반환되는 webViewUrl(웹 링크)을 사용자에게 안내한다. webViewUrl은 항상 반환되며 사용자가 브라우저에서 "
                    + "열면 미리보기·다운로드로 이어진다. 폴더는 다운로드할 수 없다. 잠긴(사용 중) 파일도 다운로드는 가능하다. "
                    + "resourceSeq는 drive_search_resources 등 목록 툴 결과 항목에서 얻는다. 대상이 공유받은 파일이면 그 "
                    + "소유자의 userSeq(항목의 sharedByUserSeq가 있으면 그 값, 없으면 userSeq — ownerSeq 아님)를 sharedByUserSeq로 지정한다(내 파일이면 생략) — 다운로드 "
                    + "권한(canDownload)이 있어야 한다. 성공 판정 기준은 응답에 error 필드가 없는 것이다.")
    public McpToolResult<FileDownloadToolResponse> downloadFile(
            @ToolParam(description = "다운로드할 파일의 resourceSeq (drive_search_resources 등 목록 툴 결과 항목의 resourceSeq)") Long resourceSeq,
            @ToolParam(description = "공유받은 파일일 때 그 소유자의 userSeq (항목의 sharedByUserSeq가 있으면 그 값, 없으면 userSeq — ownerSeq 아님). "
                    + "내 파일이면 생략", required = false) Integer sharedByUserSeq) {

        log.info("[MCP] drive_download_file called. resourceSeq : {}, sharedByUserSeq : {}", resourceSeq, sharedByUserSeq);

        return McpToolResult.executeAuthed(details -> {
            int requestUserSeq = details.getUserSeq();

            McpToolParams.require(resourceSeq, "resourceSeq");

            // 웹 다운로드 API의 @CheckResourcePermission(DOWNLOAD, isDelete=ANY)과 동일한 검사 — ANY는 isDelete=null
            int ownerSeq = MapStructUtil.resolveUserSeq(sharedByUserSeq, requestUserSeq);
            resourcePermissionService.checkPermission(SharePermissionType.DOWNLOAD, resourceSeq, ownerSeq,
                    RequestActor.of(requestUserSeq, details.isAnonymous()), null);

            var info = fileDownloadContentUseCase.downloadContent(
                    new FileDownloadCommand(resourceSeq, sharedByUserSeq), requestUserSeq);

            // 웹 링크 조립은 어댑터 책임 — read와 동일한 /preview 딥링크
            String webViewUrl = McpUrls.previewUrl(
                    clientDomainService.getDomain(details.getTenantSeq()), resourceSeq, info.ownerUserSeq());
            return fileToolConverter.toDownloadResponse(info, webViewUrl);
        });
    }

    /**
     * editUrl 포함 여부 — 웹오피스 편집 가능 확장자(프런트 isOfficeEditable 동형)이고 요청자에게
     * EDIT 권한이 있을 때만. 필드 유무가 곧 시그널이라 권한 없음은 에러가 아니라 미포함으로 처리한다.
     * 공유 파일은 isDelete=false 조회라 휴지통이면 미포함이지만, 내 소유 파일은 checkPermission의
     * 소유자 숏컷으로 항상 포함된다 — 내 휴지통 파일의 editUrl은 프런트 진입 시 상세 조회 실패로
     * 홈 폴백된다(webViewUrl의 기존 동작과 동일).
     */
    private boolean isWebOfficeEditable(String extension, Long resourceSeq, int ownerSeq, RequestActor actor) {
        if (!WebOfficeEditableExtension.contains(extension)) {
            return false;
        }
        try {
            resourcePermissionService.checkPermission(SharePermissionType.EDIT, resourceSeq, ownerSeq, actor, false);
            return true;
        } catch (ApiRuntimeException e) {
            return false;
        }
    }

}

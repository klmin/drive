package com.thinkfree.drive.weboffice.service.impl;
import com.thinkfree.drive.common.AuditMockTest;

import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.file.write.orchestrator.FileWriteOrchestrator;
import com.thinkfree.drive.resource.converter.ResourceConverter;
import com.thinkfree.drive.resource.converter.WebOfficeToResourceConverter;
import com.thinkfree.drive.resource.dto.file.FileUploadDto;
import com.thinkfree.drive.resource.data.ResourceDetailData;
import com.thinkfree.drive.resource.data.ResourceIdentity;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.result.ResourceSaveResult;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.weboffice.converter.WebOfficeConverter;
import com.thinkfree.drive.common.file.model.FileData;
import com.thinkfree.drive.weboffice.dto.WebOfficeGetDto;
import com.thinkfree.drive.weboffice.dto.WebOfficeInfoDto;
import com.thinkfree.drive.weboffice.dto.WebOfficeSaveDto;
import com.thinkfree.drive.weboffice.enums.WebOfficeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * FILE_FOLDER 카테고리 중 DefaultWebOfficeService가 기록하는 FILE 이벤트(OPEN/PREVIEW/DOWNLOAD/EDIT)의 감사 로그 기록 호출 테스트.
 * get(파일 로드) 시 편집기·관전 모드면 FILE_OPEN, 뷰어 모드(미리보기)면 FILE_PREVIEW, 저장(버전 생성)하면 FILE_EDIT.
 * info는 요청 유형(type)이 다운로드·PDF 다운로드일 때만 FILE_DOWNLOAD를 기록하고, 그 외 유형은 기록하지 않는다.
 */
@DisplayName("DefaultWebOfficeService(문서 열기/편집) 감사 로그 기록 호출 테스트")
class DefaultWebOfficeServiceAuditTest extends AuditMockTest {

    private static final int USER = 5;

    @Mock private WebOfficeToResourceConverter webOfficeToResourceConverter;
    @Mock private ResourceConverter resourceConverter;
    @Mock private WebOfficeConverter webOfficeConverter;
    @Mock private ResourceFindService resourceFindService;
    @Mock private FileWriteOrchestrator fileWriteOrchestrator;
    @Mock private StorageService storageService;
    @Mock private ResourceAuditService resourceAuditService;

    @InjectMocks
    private DefaultWebOfficeService webOfficeService;

    /** 웹오피스는 SecurityContext가 없어 호출부가 조회한 User를 actor로 싣는다. */
    private UserDetailData actorMock() {
        return Mockito.mock(UserDetailData.class);
    }

    private ResourceDetailData resourceMock(long seq, String name) {
        ResourceDetailData r = Mockito.mock(ResourceDetailData.class);
        given(r.getResourceSeq()).willReturn(seq);
        given(r.getResourceName()).willReturn(name);
        given(r.getParentFolderSeq()).willReturn(10L);
        given(r.getResourceType()).willReturn(ResourceType.FILE);
        given(r.getContentType()).willReturn("application/vnd");
        given(r.getSizeByte()).willReturn(10L);
        return r;
    }

    /** 주어진 모드로 fetchInfo를 호출하고 기대한 이벤트가 기록됐는지 검증한다. */
    private void fetchInfoAndVerify(WebOfficeType webOfficeType, String resourceName, EventName expected) {
        fetchInfoAndVerify(webOfficeType, null, resourceName, expected);
    }

    /** 주어진 모드·요청 유형으로 fetchInfo를 호출하고 기대한 이벤트가 기록됐는지 검증한다. expected가 null이면 미기록을 검증한다. */
    private void fetchInfoAndVerify(WebOfficeType webOfficeType, String type, String resourceName, EventName expected) {
        WebOfficeInfoDto dto = Mockito.mock(WebOfficeInfoDto.class);
        given(dto.getResourceSeq()).willReturn(800L);
        given(dto.getUserSeq()).willReturn(USER);
        given(dto.getLoginUserSeq()).willReturn(USER);
        given(dto.getWebOfficeType()).willReturn(webOfficeType);
        given(dto.getType()).willReturn(type);
        UserDetailData actor = actorMock();
        given(dto.getActor()).willReturn(actor);

        ResourceDetailData resource = resourceMock(800L, resourceName);
        given(resourceFindService.findDetail(any())).willReturn(resource);

        webOfficeService.fetchInfo(dto);

        if (expected == null) {
            verify(resourceAuditService, never()).record(any(EventName.class), any(ResourceIdentity.class), any(), any());
            return;
        }
        verify(resourceAuditService).record(expected, resource, USER, actor);
    }

    /** 주어진 모드로 loadFile을 호출하고 기대한 이벤트가 기록됐는지 검증한다. */
    private void loadFileAndVerify(WebOfficeType webOfficeType, String resourceName, EventName expected) {
        WebOfficeGetDto dto = Mockito.mock(WebOfficeGetDto.class);
        given(dto.getResourceSeq()).willReturn(800L);
        given(dto.getUserSeq()).willReturn(USER);
        given(dto.getLoginUserSeq()).willReturn(USER);
        given(dto.getWebOfficeType()).willReturn(webOfficeType);
        UserDetailData actor = actorMock();
        given(dto.getActor()).willReturn(actor);

        ResourceDetailData resource = resourceMock(800L, resourceName);
        given(resource.getResourceSaveName()).willReturn("save-name");
        given(resourceFindService.findDetail(any())).willReturn(resource);
        given(storageService.getFileData("save-name"))
                .willReturn(new FileData("application/vnd", new java.io.ByteArrayInputStream(new byte[0]), 0L));

        webOfficeService.loadFile(dto);

        verify(resourceAuditService).record(expected, resource, USER, actor);
    }

    // EventName: FILE_OPEN
    @Test
    @DisplayName("loadFile - 편집기 모드는 FILE_OPEN 기록")
    void loadFile_editor_recordsFileOpen() {
        loadFileAndVerify(WebOfficeType.WRITE_EDITOR, "열람문서.docx", EventName.FILE_OPEN);
    }

    @Test
    @DisplayName("loadFile - PDF 뷰어 모드는 FILE_PREVIEW 기록")
    void loadFile_pdfViewer_recordsFilePreview() {
        loadFileAndVerify(WebOfficeType.PDF_VIEWER, "열람문서.pdf", EventName.FILE_PREVIEW);
    }

    // EventName: FILE_PREVIEW
    @Test
    @DisplayName("loadFile - 뷰어 모드는 FILE_PREVIEW 기록")
    void loadFile_viewer_recordsFilePreview() {
        loadFileAndVerify(WebOfficeType.WRITE_VIEWER, "열람문서.docx", EventName.FILE_PREVIEW);
    }

    @Test
    @DisplayName("fetchInfo - 요청 유형 미지정은 get에서 기록하므로 미기록")
    void fetchInfo_noType_recordsNothing() {
        fetchInfoAndVerify(WebOfficeType.WRITE_EDITOR, "열람문서.docx", null);
    }

    @Test
    @DisplayName("fetchInfo - 요청 유형이 열기면 get에서 기록하므로 미기록")
    void fetchInfo_typeOpen_recordsNothing() {
        fetchInfoAndVerify(WebOfficeType.WRITE_VIEWER, "open", "열람문서.docx", null);
    }

    // EventName: FILE_DOWNLOAD
    @Test
    @DisplayName("fetchInfo - 요청 유형이 다운로드면 FILE_DOWNLOAD 기록")
    void fetchInfo_typeDownload_recordsFileDownload() {
        fetchInfoAndVerify(WebOfficeType.WRITE_EDITOR, "download", "열람문서.docx", EventName.FILE_DOWNLOAD);
    }

    // EventName: FILE_DOWNLOAD - PDF 변환본도 다운로드와 동일하게 기록한다.
    @Test
    @DisplayName("fetchInfo - 요청 유형이 PDF 다운로드면 FILE_DOWNLOAD 기록")
    void fetchInfo_typePdfDownload_recordsFileDownload() {
        fetchInfoAndVerify(WebOfficeType.WRITE_EDITOR, "pdf_download", "열람문서.docx", EventName.FILE_DOWNLOAD);
    }

    // EventName: FILE_DOWNLOAD - 다운로드는 앱 모드와 무관하게 기록한다(뷰어 모드라도 FILE_PREVIEW로 새지 않는다).
    @Test
    @DisplayName("fetchInfo - 뷰어 모드에서 다운로드해도 FILE_DOWNLOAD 기록")
    void fetchInfo_typeDownloadViewer_recordsFileDownload() {
        fetchInfoAndVerify(WebOfficeType.WRITE_VIEWER, "download", "열람문서.docx", EventName.FILE_DOWNLOAD);
    }

    // EventName: FILE_DOWNLOAD - PDF 뷰어에서 PDF로 내려받는 실사용 경로.
    @Test
    @DisplayName("fetchInfo - PDF 뷰어 모드에서 PDF 다운로드해도 FILE_DOWNLOAD 기록")
    void fetchInfo_typePdfDownloadPdfViewer_recordsFileDownload() {
        fetchInfoAndVerify(WebOfficeType.PDF_VIEWER, "pdf_download", "열람문서.pdf", EventName.FILE_DOWNLOAD);
    }

    @Test
    @DisplayName("fetchInfo - 요청 유형이 인쇄면 대응 이벤트가 없어 미기록")
    void fetchInfo_typePrint_recordsNothing() {
        fetchInfoAndVerify(WebOfficeType.WRITE_EDITOR, "print", "열람문서.docx", null);
    }

    @Test
    @DisplayName("fetchInfo - 모르는 요청 유형은 미기록")
    void fetchInfo_unknownType_recordsNothing() {
        fetchInfoAndVerify(WebOfficeType.WRITE_EDITOR, "export", "열람문서.docx", null);
    }

    // EventName: FILE_EDIT
    @Test
    @DisplayName("save - TFO 문서 저장(버전 생성) 시 FILE_EDIT 기록")
    void save_recordsFileEdit() {
        WebOfficeSaveDto dto = Mockito.mock(WebOfficeSaveDto.class);
        given(dto.getResourceSeq()).willReturn(801L);
        given(dto.getUserSeq()).willReturn(USER);
        given(dto.getLoginUserSeq()).willReturn(USER);
        given(dto.getSize()).willReturn(10L);
        given(dto.getWebOfficeSaveType()).willReturn(null); // null이면 버전 저장 경로 진입
        given(dto.getInputStream()).willReturn(new java.io.ByteArrayInputStream(new byte[0]));
        UserDetailData actor = actorMock();
        given(dto.getActor()).willReturn(actor);

        ResourceDetailData resource = resourceMock(801L, "편집문서.docx");
        given(resourceFindService.findDetail(any())).willReturn(resource);
        given(webOfficeToResourceConverter.toFileUploadDto(any(), Mockito.anyInt(), any(), Mockito.anyInt())).willReturn(FileUploadDto.builder().build());
        given(fileWriteOrchestrator.write(any(), Mockito.anyInt())).willReturn(
                new ResourceSaveResult(null, USER, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null));

        webOfficeService.save(dto);

        verify(resourceAuditService).record(EventName.FILE_EDIT, resource, USER, actor);
    }
}

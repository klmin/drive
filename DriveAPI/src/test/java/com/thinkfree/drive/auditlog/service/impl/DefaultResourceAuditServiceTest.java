package com.thinkfree.drive.auditlog.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkfree.drive.auditlog.context.AuditLogContextHolder;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.common.AuditMockTest;
import com.thinkfree.drive.global.security.CustomUserDetails;
import com.thinkfree.drive.objectmapper.util.ObjectMapperUtil;
import com.thinkfree.drive.partition.PartitionKey;
import com.thinkfree.drive.permission.model.ResourceShareFarthest;
import com.thinkfree.drive.permission.service.ResourcePermissionFindService;
import com.thinkfree.drive.resource.converter.ResourceConverter;
import com.thinkfree.drive.resource.data.ResourceHierarchyData;
import com.thinkfree.drive.resource.data.ResourceDetailData;
import com.thinkfree.drive.resource.query.ResourceFindQuery;
import com.thinkfree.drive.resource.service.ResourceFindService;
import com.thinkfree.drive.user.data.UserDetailData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 감사 경로 조회 분기와 기록 내용 테스트.
 * 경로 절단 규칙 자체는 {@link com.thinkfree.drive.permission.model.ResourceShareFarthest}가 담당하므로
 * 여기서는 어느 조회를 타는지·결과를 어떻게 기록하는지만 검증한다.
 */
@DisplayName("DefaultResourceAuditService(감사 경로 조회·기록) 테스트")
class DefaultResourceAuditServiceTest extends AuditMockTest {

    private static final int OWNER = 10;
    private static final int ACTOR = 20;
    private static final long FILE_SEQ = 100L;
    private static final long PARENT_SEQ = 4L;
    private static final String OWNER_PATH = "영업팀/2026/제안서/";
    private static final String ACTOR_PATH = "2026/제안서/";

    @Mock private ResourceFindService resourceFindService;
    @Mock private ResourceConverter resourceConverter;
    @Mock private ResourcePermissionFindService resourcePermissionFindService;

    private DefaultResourceAuditService resourceAuditService;

    @BeforeEach
    void setUp() {
        resourceAuditService = new DefaultResourceAuditService(resourceFindService, resourceConverter,
                resourcePermissionFindService, new ObjectMapperUtil(new ObjectMapper()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(int userSeq) {
        CustomUserDetails details = Mockito.mock(CustomUserDetails.class);
        given(details.getUserSeq()).willReturn(userSeq);
        SecurityContextHolder.getContext()
                             .setAuthentication(new UsernamePasswordAuthenticationToken(details, null, List.of()));
    }

    /** 웹오피스처럼 시큐리티 컨텍스트가 없는 경로에서 호출부가 넘기는 행위자 */
    private UserDetailData actorMock() {
        UserDetailData actor = Mockito.mock(UserDetailData.class);
        given(actor.getUserSeq()).willReturn(ACTOR);
        given(actor.getUserId()).willReturn("actor@thinkfree.com");
        given(actor.getUserName()).willReturn("행위자");
        return actor;
    }

    private ResourceDetailData fileMock(long seq, String name, Long parentFolderSeq) {
        ResourceDetailData r = Mockito.mock(ResourceDetailData.class);
        given(r.getResourceSeq()).willReturn(seq);
        given(r.getResourceName()).willReturn(name);
        given(r.getParentFolderSeq()).willReturn(parentFolderSeq);
        return r;
    }

    /** 소유자 스코프에서 부모 폴더 단건 조회가 OWNER_PATH를 반환하도록 스텁 */
    private void givenOwnerPath(String fullPath) {
        ResourceDetailData parentFolder = Mockito.mock(ResourceDetailData.class);
        given(parentFolder.getFullPath()).willReturn(fullPath);
        given(resourceConverter.toFindQuery(any(), any(), any(), any())).willReturn(Mockito.mock(ResourceFindQuery.class));
        given(resourceFindService.findDetailOrNull(any())).willReturn(parentFolder);
    }

    /** 행위자 스코프 단건 조회가 ACTOR_PATH를 반환하도록 스텁 */
    private void givenActorPath(String fullPath) {
        ResourceShareFarthest farthest = Mockito.mock(ResourceShareFarthest.class);
        given(farthest.getFullPath()).willReturn(fullPath);
        given(resourcePermissionFindService.findResourceShareFarthestOrNull(anyInt(), any(), any()))
                .willReturn(farthest);
    }

    /** 행위자 스코프 일괄 조회(fan-out)가 ACTOR_PATH를 반환하도록 스텁 */
    private void givenActorPathBatch(long resourceSeq, String fullPath) {
        ResourceShareFarthest farthest = Mockito.mock(ResourceShareFarthest.class);
        given(farthest.getFullPath()).willReturn(fullPath);
        given(resourcePermissionFindService.findResourcesShareFarthestMap(anyInt(), anyList(), any()))
                .willReturn(Map.of(new PartitionKey(OWNER, resourceSeq), farthest));
    }

    @Test
    @DisplayName("표시용 경로 - 소유자 본인이면 MY 루트를 붙이고 trailing slash를 제거한다")
    void findDisplayPath_owner_prefixesMy() {
        loginAs(OWNER);
        givenOwnerPath(OWNER_PATH);

        var path = resourceAuditService.findDisplayPath(FILE_SEQ, PARENT_SEQ, OWNER);

        assertThat(path).isEqualTo("MY/영업팀/2026/제안서");
    }

    @Test
    @DisplayName("표시용 경로 - 행위자가 소유자와 다르면 SHARE 루트를 붙인다")
    void findDisplayPath_actor_prefixesShare() {
        loginAs(ACTOR);
        givenActorPath(ACTOR_PATH);

        var path = resourceAuditService.findDisplayPath(FILE_SEQ, PARENT_SEQ, OWNER);

        assertThat(path).isEqualTo("SHARE/2026/제안서");
    }

    @Test
    @DisplayName("표시용 경로 - 드라이브 루트 항목은 위치 루트만 남는다")
    void findDisplayPath_driveRoot_returnsLocationOnly() {
        loginAs(OWNER);

        var path = resourceAuditService.findDisplayPath(FILE_SEQ, null, OWNER);

        assertThat(path).isEqualTo("MY");
    }

    @Test
    @DisplayName("드라이브 루트 - 조회 없이 null")
    void findResourcePath_driveRoot_returnsNullWithoutQuery() {
        loginAs(OWNER);

        var path = resourceAuditService.findResourcePath(FILE_SEQ, null, OWNER);

        assertThat(path).isNull();
        verify(resourceFindService, never()).findDetailOrNull(any());
        verify(resourcePermissionFindService, never()).findResourceShareFarthestOrNull(anyInt(), any(), any());
    }

    @Test
    @DisplayName("소유자 본인 - 부모 폴더 단건 조회로 절대경로 반환(공유 체인 조회 안 함)")
    void findResourcePath_owner_usesParentFolderLookup() {
        loginAs(OWNER);
        givenOwnerPath(OWNER_PATH);

        var path = resourceAuditService.findResourcePath(FILE_SEQ, PARENT_SEQ, OWNER);

        assertThat(path).isEqualTo(OWNER_PATH);
        verify(resourcePermissionFindService, never()).findResourceShareFarthestOrNull(anyInt(), any(), any());
    }

    @Test
    @DisplayName("요청 컨텍스트 없음(스케줄러 등 SYSTEM) - 소유자 기준으로 조회")
    void findResourcePath_noRequestContext_usesOwnerScope() {
        givenOwnerPath(OWNER_PATH);

        var path = resourceAuditService.findResourcePath(FILE_SEQ, PARENT_SEQ, OWNER);

        assertThat(path).isEqualTo(OWNER_PATH);
        verify(resourcePermissionFindService, never()).findResourceShareFarthestOrNull(anyInt(), any(), any());
    }

    @Test
    @DisplayName("행위자가 소유자와 다르면 - 공유 체인 조회로 잘린 경로 반환")
    void findResourcePath_actorDiffersFromOwner_usesShareChain() {
        loginAs(ACTOR);
        givenActorPath(ACTOR_PATH);

        var path = resourceAuditService.findResourcePath(FILE_SEQ, PARENT_SEQ, OWNER);

        assertThat(path).isEqualTo(ACTOR_PATH);
        verify(resourceFindService, never()).findDetailOrNull(any());
    }

    @Test
    @DisplayName("공유 체인 결과가 없으면(미공유/링크 전용) null")
    void findResourcePath_noShareChainEntry_returnsNull() {
        loginAs(ACTOR);
        given(resourcePermissionFindService.findResourceShareFarthestOrNull(anyInt(), any(), any()))
                .willReturn(null);

        var path = resourceAuditService.findResourcePath(FILE_SEQ, PARENT_SEQ, OWNER);

        assertThat(path).isNull();
    }

    @Test
    @DisplayName("부모 폴더가 조회되지 않으면 예외 없이 null")
    void findResourcePath_parentFolderNotFound_returnsNull() {
        loginAs(OWNER);
        given(resourceConverter.toFindQuery(any(), any(), any(), any())).willReturn(Mockito.mock(ResourceFindQuery.class));
        given(resourceFindService.findDetailOrNull(any())).willReturn(null);

        var path = resourceAuditService.findResourcePath(FILE_SEQ, PARENT_SEQ, OWNER);

        assertThat(path).isNull();
    }

    @Test
    @DisplayName("공유 체인 조회가 실패해도 예외를 던지지 않고 null (업무 트랜잭션 보호)")
    void findResourcePath_lookupFails_swallowsException() {
        loginAs(ACTOR);
        willThrow(new IllegalStateException("boom"))
                .given(resourcePermissionFindService).findResourceShareFarthestOrNull(anyInt(), any(), any());

        var path = resourceAuditService.findResourcePath(FILE_SEQ, PARENT_SEQ, OWNER);

        assertThat(path).isNull();
    }

    @Test
    @DisplayName("소유자 분기 조회가 실패해도 예외를 던지지 않고 null")
    void findResourcePath_ownerLookupFails_swallowsException() {
        loginAs(OWNER);
        given(resourceConverter.toFindQuery(any(), any(), any(), any())).willReturn(Mockito.mock(ResourceFindQuery.class));
        willThrow(new IllegalStateException("boom"))
                .given(resourceFindService).findDetailOrNull(any());

        var path = resourceAuditService.findResourcePath(FILE_SEQ, PARENT_SEQ, OWNER);

        assertThat(path).isNull();
    }

    /**
     * 감사 경로는 예외를 던지지 않는 조회로만 구해야 한다.
     *
     * <p>findResourceShareFarthest는 미공유 리소스면 예외를 던지는데, 그 예외가 조회 서비스의 트랜잭션
     * 경계를 벗어나는 순간 진행 중인 트랜잭션에 "롤백 확정" 표시가 붙는다. 감사 쪽에서 예외를 잡아도
     * 커밋할 때 실패해서, 감사 로그 때문에 파일 이동 같은 업무가 되돌아간다.
     */
    @Test
    @DisplayName("예외를 던지는 findResourceShareFarthest는 쓰지 않는다")
    void findResourcePath_neverUsesThrowingLookup() {
        loginAs(ACTOR);
        givenActorPath(ACTOR_PATH);

        resourceAuditService.findResourcePath(FILE_SEQ, PARENT_SEQ, OWNER);

        verify(resourcePermissionFindService, never()).findResourceShareFarthest(anyInt(), any(), any());
    }

    @Test
    @DisplayName("record - 이벤트/objectSeq/objectName/경로를 기록")
    void record_writesResourceShape() {
        loginAs(OWNER);
        givenOwnerPath(OWNER_PATH);
        ResourceDetailData resource = fileMock(FILE_SEQ, "제안서_최종.pptx", PARENT_SEQ);

        resourceAuditService.record(EventName.FILE_DOWNLOAD, resource, OWNER);

        var contexts = AuditLogContextHolder.getAll();
        assertThat(contexts).hasSize(1);
        var context = contexts.get(0);
        assertThat(context.getEventName()).isEqualTo(EventName.FILE_DOWNLOAD);
        assertThat(context.getObjectSeq()).isEqualTo(FILE_SEQ);
        assertThat(context.getObjectName()).isEqualTo("제안서_최종.pptx");
        // 위치 루트(MY)가 경로 앞에 붙고 trailing slash는 제거됨
        assertThat(context.getResourcePath()).isEqualTo("MY/영업팀/2026/제안서");
    }

    @Test
    @DisplayName("record(actor) - 시큐리티 컨텍스트가 없어도 넘겨받은 actor를 기록한다")
    void record_withActor_writesActorWithoutSecurityContext() {
        givenActorPath(ACTOR_PATH);
        ResourceDetailData resource = fileMock(FILE_SEQ, "열람문서.docx", PARENT_SEQ);

        resourceAuditService.record(EventName.FILE_OPEN, resource, OWNER, actorMock());

        var context = AuditLogContextHolder.getAll().get(0);
        assertThat(context.getEventName()).isEqualTo(EventName.FILE_OPEN);
        assertThat(context.getActorUserSeq()).isEqualTo(ACTOR);
        assertThat(context.getActorUserId()).isEqualTo("actor@thinkfree.com");
        assertThat(context.getActorUserName()).isEqualTo("행위자");
        // 넘겨받은 actor 기준으로 경로를 잡으므로 소유자와 다르면 SHARE 루트가 붙는다
        assertThat(context.getResourcePath()).isEqualTo("SHARE/2026/제안서");
    }

    @Test
    @DisplayName("record(seq) - 리소스를 못 찾아도 objectSeq만으로 기록하고 예외를 던지지 않는다")
    void record_bySeq_resourceNotFound_recordsSeqOnly() {
        loginAs(OWNER);
        given(resourceConverter.toFindQuery(any(), any(), any(), any())).willReturn(Mockito.mock(ResourceFindQuery.class));
        given(resourceFindService.findDetailOrNull(any())).willReturn(null);

        resourceAuditService.record(EventName.FILE_UPLOAD, FILE_SEQ, OWNER);

        var context = AuditLogContextHolder.getAll().get(0);
        assertThat(context.getEventName()).isEqualTo(EventName.FILE_UPLOAD);
        assertThat(context.getObjectSeq()).isEqualTo(FILE_SEQ);
        assertThat(context.getObjectName()).isNull();
        assertThat(context.getResourcePath()).isNull();
    }

    @Test
    @DisplayName("recordRename - before/after에 변경 전후 이름을 JSON으로 기록")
    void recordRename_writesNameChange() {
        loginAs(OWNER);
        givenOwnerPath(OWNER_PATH);
        ResourceDetailData resource = fileMock(FILE_SEQ, "기술명세서_v2.docx", PARENT_SEQ);

        resourceAuditService.recordRename(EventName.FILE_RENAME, resource, OWNER, "기술명세서_초안.docx");

        var context = AuditLogContextHolder.getAll().get(0);
        assertThat(context.getEventName()).isEqualTo(EventName.FILE_RENAME);
        assertThat(context.getObjectName()).isEqualTo("기술명세서_v2.docx");
        assertThat(context.getBeforeValue()).isEqualTo("{\"resourceName\":\"기술명세서_초안.docx\"}");
        assertThat(context.getAfterValue()).isEqualTo("{\"resourceName\":\"기술명세서_v2.docx\"}");
    }

    @Test
    @DisplayName("recordMove - before는 넘겨받은 이동 전 경로, after는 새 부모 경로에 파일명을 붙여 기록")
    void recordMove_writesPathChange() {
        loginAs(OWNER);
        givenOwnerPath("내작업/");
        ResourceDetailData resource = fileMock(FILE_SEQ, "제안서_최종.pptx", PARENT_SEQ);

        resourceAuditService.recordMove(resource, ACTOR_PATH, "제안서_최종.pptx", PARENT_SEQ, OWNER, OWNER);

        var context = AuditLogContextHolder.getAll().get(0);
        assertThat(context.getEventName()).isEqualTo(EventName.FILE_MOVE);
        assertThat(context.getResourcePath()).isEqualTo("MY/내작업");
        assertThat(context.getBeforeValue()).isEqualTo("{\"path\":\"MY/2026/제안서/제안서_최종.pptx\"}");
        assertThat(context.getAfterValue()).isEqualTo("{\"path\":\"MY/내작업/제안서_최종.pptx\"}");
    }

    @Test
    @DisplayName("recordMove - 이동 위치에 같은 이름이 있어 구분 번호가 붙어도 before는 이동 전 이름으로 기록")
    void recordMove_renamedOnCollision_beforeKeepsOldName() {
        loginAs(OWNER);
        givenOwnerPath("3333/");
        // 이동 과정에서 이름이 "새 스프레드시트 (1).xlsx"로 바뀐 상태의 엔티티
        ResourceDetailData resource = fileMock(FILE_SEQ, "새 스프레드시트 (1).xlsx", PARENT_SEQ);

        resourceAuditService.recordMove(resource, "감사로그테스트/", "새 스프레드시트.xlsx", PARENT_SEQ, OWNER, OWNER);

        var context = AuditLogContextHolder.getAll().get(0);
        assertThat(context.getBeforeValue()).isEqualTo("{\"path\":\"MY/감사로그테스트/새 스프레드시트.xlsx\"}");
        assertThat(context.getAfterValue()).isEqualTo("{\"path\":\"MY/3333/새 스프레드시트 (1).xlsx\"}");
    }

    @Test
    @DisplayName("recordMove - 이동 전 경로가 null이면(루트에서 이동) 파일명만으로 조립")
    void recordMove_nullOldParentPath_usesFileNameOnly() {
        loginAs(OWNER);
        givenOwnerPath("내작업/");
        ResourceDetailData resource = fileMock(FILE_SEQ, "제안서_최종.pptx", PARENT_SEQ);

        resourceAuditService.recordMove(resource, null, "제안서_최종.pptx", PARENT_SEQ, OWNER, OWNER);

        var context = AuditLogContextHolder.getAll().get(0);
        assertThat(context.getBeforeValue()).isEqualTo("{\"path\":\"MY/제안서_최종.pptx\"}");
    }

    @Test
    @DisplayName("recordMove - 소유자 변경 이동이면 새 부모 경로를 이동한 곳 소유자로 조회")
    void recordMove_ownerChanged_usesTargetOwnerForNewPath() {
        loginAs(ACTOR);
        givenActorPath(ACTOR_PATH);
        ResourceDetailData resource = fileMock(FILE_SEQ, "제안서_최종.pptx", PARENT_SEQ);

        resourceAuditService.recordMove(resource, "이전경로/", "제안서_최종.pptx", PARENT_SEQ, OWNER, ACTOR);

        // targetOwnerSeq(ACTOR)와 행위자가 같으므로 소유자 스코프로 빠져 부모 폴더 단건 조회를 타야 한다
        verify(resourceFindService).findDetailOrNull(any());
    }

    @Test
    @DisplayName("recordAll - 항목별 이벤트를 resolver로 결정하고 경로를 일괄 조회")
    void recordAll_batchLookupPerItem() {
        loginAs(ACTOR);
        ResourceHierarchyData file = Mockito.mock(ResourceHierarchyData.class);
        given(file.getResourceSeq()).willReturn(FILE_SEQ);
        given(file.getResourceName()).willReturn("파일.txt");
        given(file.getIsFolder()).willReturn(false);
        givenActorPathBatch(FILE_SEQ, ACTOR_PATH);

        resourceAuditService.recordAll(List.of(file), OWNER,
                item -> item.getIsFolder() ? EventName.FOLDER_DOWNLOAD : EventName.FILE_DOWNLOAD);

        var context = AuditLogContextHolder.getAll().get(0);
        assertThat(context.getEventName()).isEqualTo(EventName.FILE_DOWNLOAD);
        assertThat(context.getObjectName()).isEqualTo("파일.txt");
        // 행위자(ACTOR)≠소유자(OWNER)이므로 위치 루트 SHARE가 경로 앞에 붙음
        assertThat(context.getResourcePath()).isEqualTo("SHARE/2026/제안서");
        // 항목마다 단건 조회하지 않고 한 번에 조회
        verify(resourcePermissionFindService).findResourcesShareFarthestMap(anyInt(), anyList(), any());
        verify(resourcePermissionFindService, never()).findResourceShareFarthestOrNull(anyInt(), any(), any());
    }

    @Test
    @DisplayName("recordAll - 빈 목록이면 조회도 기록도 하지 않음")
    void recordAll_emptyItems_doesNothing() {
        loginAs(ACTOR);

        resourceAuditService.recordAll(List.of(), OWNER, item -> EventName.FILE_DOWNLOAD);

        assertThat(AuditLogContextHolder.getAll()).isEmpty();
        verify(resourcePermissionFindService, never()).findResourcesShareFarthestMap(anyInt(), anyList(), any());
    }
}

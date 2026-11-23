package com.thinkfree.drive.api.v1.resource.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.page.request.CursorPagingRequest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.response.DownloadLinkResponse;
import com.thinkfree.drive.api.v1.resource.request.*;
import com.thinkfree.drive.api.v1.resource.response.ResourceDetailResponse;
import com.thinkfree.drive.api.v1.resource.response.ResourceListResponse;
import com.thinkfree.drive.api.v1.resource.response.ResourceShareLinkResponse;
import com.thinkfree.drive.common.search.enums.FileType;
import com.thinkfree.drive.common.search.enums.SortOrder;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.constants.DriveConstants;
import com.thinkfree.drive.resource.enums.ResourceSearchType;
import com.thinkfree.drive.resource.enums.ResourceSortField;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.thinkfree.drive.common.DriveFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpHeaders;
import org.springframework.util.MultiValueMap;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@WithMockUserCustom(roles = "ROLE_USER")
class ResourceControllerTest extends AbstractMvcTest {

    @Autowired
    private JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/resources");
    }
    
    @Test
    @Disabled("dev DB 고정 데이터 의존 — user 914 의 리소스 필요. DriveFixtures 로 옮길 때 다시 켠다")
    @WithMockUserCustom(roles = "ROLE_USER", userSeq = 914)
    void findById() {
        
        Long resourceSeq = 40095L;
        Integer ss = 859;
        String url = StringUtil.buildUrl(resourceSeq);
        Boolean d = false;
        var request = new ResourceFindRequest(null, ss, d);
        
        ApiResponse<ResourceDetailResponse> response = getForObject(url, request, new TypeReference<>() {}, status().isOk());
        
        Assertions.assertNotNull(response.getData());
        Assertions.assertEquals(OK.value(), response.getCode());
        Assertions.assertEquals(resourceSeq, response.getData().resourceSeq());
        
    }

    @WithMockUserCustom(roles = "ROLE_USER", userSeq = 859)
    @Disabled("dev DB 데이터 의존 — AbstractMvcTest·@SpringBootTest 가 local 프로필(공유 dev PG)에 붙음. 859 가 675 의 폴더 40008 을 공유받은 데이터 없음(PERMISSION_002)")
    @Test
    void listWithCursor() {
        
        Integer pi = 1;
        Integer ps = 5;
        Integer ss = 675;
        String sk = null;
        Long pfs = 40008L;
        ResourceType rt = null;
        ResourceSearchType st = ResourceSearchType.MY;
        String oi = null;
        LocalDate sd = null;
        LocalDate ed = null;
        FileType ft = null;
        ResourceSortField sb = null;
        SortOrder so = null;
        Long ci = null;
        Object cv = null; // sb 기준에 맞춰서 리소스 값 저장 ex) UPDATE -> LocalDate.of(2025, 6, 12)
        ResourceType ct = null;
        //var request = new ResourceListRequest(ss, sk, pfs, rt, st, oi, sd, ed, ft, sb, so);
       // var request = new ResourceListRequest(ss, null, pfs, rt, st, null, sd, ed, null, sb, so);
        var request = new ResourceListRequest(ss, sk, pfs, rt, st, oi, null, null, ft, sb, so);

        var cursorPagingRequest = new CursorPagingRequest(cv, ci, ct, ps);

        MultiValueMap<String, String> params = multiValueMap(request, cursorPagingRequest);

        ApiResponse<List<ResourceListResponse>> response = getForObjectWithMultiParams(null, params, new TypeReference<>() {}, status().isOk());

        Assertions.assertNotNull(response.getData());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
        assertEquals(ps, response.getPageSize());
        Assertions.assertNotNull(response.getHasNext());
    }
    
    @Test
    @WithMockUserCustom(roles = "ROLE_USER", userSeq = 669)
    void exists() {
        
        Integer ss = null;
        Long pfs = null;
        String rn = "_12_15_윤선경_음료비_거래명세표 (1) (1).pdf";
        ResourceType rt = ResourceType.FILE;
        
        var request = new ResourceExistsRequest(ss, pfs, rn, rt);
        ApiResponse<Void> response = getForObject("exists", request, new TypeReference<>() {}, status().isOk());
        
        Assertions.assertEquals(OK.value(), response.getCode());
        Assertions.assertEquals(OK.getReasonPhrase(), response.getMessage());
        
    }
    
    @WithMockUserCustom(roles = "ROLE_USER", userSeq = 938, tenantSeq = 3)
    @Disabled("dev DB 데이터 의존 — AbstractMvcTest·@SpringBootTest 가 local 프로필(공유 dev PG)에 붙음. 938(tenant 3) 이 859 의 40101·40109·40105·40098 을 공유받은 데이터 없음(PERMISSION_002)")
    @Test
    void downloadToken() {
        setTestHost("default-add.drive.thinkfree.com");
        String url = StringUtil.buildUrl("download-token");

        LinkedHashSet<ResourceDownloadRequest.FileDownloadItem> item = new LinkedHashSet<>();
        item.add(new ResourceDownloadRequest.FileDownloadItem(859, 40101L));
        item.add(new ResourceDownloadRequest.FileDownloadItem(859, 40109L));
        item.add(new ResourceDownloadRequest.FileDownloadItem(859, 40105L));
        item.add(new ResourceDownloadRequest.FileDownloadItem(859, 40098L));
//        item.add(new ResourceDownloadRequest.FileDownloadItem(21, 516L));
//        item.add(new ResourceDownloadRequest.FileDownloadItem(21, 519L));
//        item.add(new ResourceDownloadRequest.FileDownloadItem(1, 488L));
//        item.add(new ResourceDownloadRequest.FileDownloadItem(1, 547L));
//        item.add(new ResourceDownloadRequest.FileDownloadItem(1, 367L));
//        item.add(new ResourceDownloadRequest.FileDownloadItem(1, 546L));
//        item.add(new ResourceDownloadRequest.FileDownloadItem(1, 590L));
//        item.add(new ResourceDownloadRequest.FileDownloadItem(1, 599L));
//        item.add(new ResourceDownloadRequest.FileDownloadItem(1, 590L));
//        item.add(new ResourceDownloadRequest.FileDownloadItem(21, 433L));
//        item.add(new ResourceDownloadRequest.FileDownloadItem(21, 513L));
//        item.add(new ResourceDownloadRequest.FileDownloadItem(21, 511L));
//        item.add(new ResourceDownloadRequest.FileDownloadItem(21, 504L));
//        item.add(new ResourceDownloadRequest.FileDownloadItem(21, 497L));

        ResourceDownloadRequest request = new ResourceDownloadRequest(item);
        
        ApiResponse<DownloadLinkResponse> response =  postForObject(url, request, new TypeReference<>(){}, status().isOk());

        assertTrue(response.isResult());
        assertEquals(OK.value(), response.getCode());
        

    }
    
    @WithMockUserCustom(roles = "ROLE_USER", userSeq = 21)
    @Test
    void findResourceTree(){
        
        String url = StringUtil.buildUrl("tree");
        
        ApiResponse<Void> response = getForObject(url, null, new TypeReference<>() {}, status().isOk());
        
        Assertions.assertEquals(OK.value(), response.getCode());
        Assertions.assertEquals(OK.getReasonPhrase(), response.getMessage());
    }
    
    @WithMockUserCustom(roles = "ROLE_USER", userSeq = 668)
    @Disabled("dev DB 데이터 의존 — AbstractMvcTest·@SpringBootTest 가 local 프로필(공유 dev PG)에 붙음. 668 이 669 의 25285 를 편집 권한으로 공유받은 데이터 없음(PERMISSION_002), 실행 시 dev 리소스 잠금")
    @Test
    void lock(){
        
        Long resourceSeq = 25285L;
        Integer ss = 669;
        var request = new ResourceSharedByUserRequest(resourceSeq, ss);
        String url = StringUtil.buildUrl(resourceSeq, "lock");
        
        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>() {}, status().isOk());
        
        Assertions.assertEquals(OK.value(), response.getCode());
        Assertions.assertEquals(OK.getReasonPhrase(), response.getMessage());
        
    }
    
    /** 소유자(V1 시드 admin)가 자기 잠긴 파일을 푼다(ss 없음 = 내 리소스). 테스트 트랜잭션으로 되돌린다 */
    @WithMockUserCustom(roles = "ROLE_USER", userSeq = DriveFixtures.ADMIN, tenantSeq = DriveFixtures.DEFAULT_TENANT)
    @Test
    @Transactional
    void unlock(){
        var fixtures = DriveFixtures.of(jdbc);
        long resourceSeq = fixtures.file(DriveFixtures.ADMIN, null, "locked.txt");
        fixtures.lock(DriveFixtures.ADMIN, resourceSeq);

        Integer ss = null;
        var request = new ResourceSharedByUserRequest(resourceSeq, ss);
        String url = StringUtil.buildUrl(resourceSeq, "unlock");

        ApiResponse<Void> response = patchForObject(url, request, new TypeReference<>() {}, status().isOk());

        Assertions.assertEquals(OK.value(), response.getCode());
        Assertions.assertEquals(OK.getReasonPhrase(), response.getMessage());
        // 응답 뒤 JPA 변경 감지가 아직 flush 되지 않았을 수 있어 같은 트랜잭션에서 flush 후 확인한다
        entityManager.flush();
        Assertions.assertEquals("N", jdbc.queryForObject(
                "SELECT is_locked FROM tb_resource WHERE user_seq = ? AND resource_seq = ?", String.class, DriveFixtures.ADMIN, resourceSeq));
    }
    
    @WithMockUserCustom(roles = "ROLE_USER", userSeq = 876, tenantSeq = 5)
    @Disabled("dev DB 데이터 의존 — AbstractMvcTest·@SpringBootTest 가 local 프로필(공유 dev PG)에 붙음. 876 이 902 의 40059 를 공유받은 데이터 없음(PERMISSION_002)")
    @Test
    void copyShareLink() {
        setTestHost("test5.drive.thinkfree.com");
        Long resourceSeq = 40059L;
        Integer ss = 902;
        var request = new ResourceSharedByUserRequest(resourceSeq, ss);
        String url = StringUtil.buildUrl(resourceSeq, "link");
        
        ApiResponse<ResourceShareLinkResponse> response = getForObject(url, request, new TypeReference<>() {
        }, status().isOk());
        
        Assertions.assertNotNull(response.getData());
        Assertions.assertEquals(OK.value(), response.getCode());
        Assertions.assertEquals(OK.getReasonPhrase(), response.getMessage());
    }
}
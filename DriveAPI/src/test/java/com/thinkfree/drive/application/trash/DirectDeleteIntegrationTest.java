package com.thinkfree.drive.application.trash;
import com.thinkfree.drive.cleanup.storage.service.StorageDeletionProcessor;
import com.thinkfree.drive.cleanup.storage.service.StorageDeletionService;
import com.thinkfree.drive.auditlog.service.ResourceAuditService;
import com.thinkfree.drive.global.security.*;
import com.thinkfree.drive.resource.enums.ResourceType;
import com.thinkfree.drive.resource.dto.folder.FolderUploadDto;
import com.thinkfree.drive.resource.service.ResourceRetryService;
import com.thinkfree.drive.postprocess.orchestrator.PostProcessingOrchestratorRetry;
import com.thinkfree.drive.storage.result.StorageDeleteObjectsResult;
import com.thinkfree.drive.storage.service.StorageService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/** Real PostgreSQL transactions: failure after metadata deletion must restore files, versions and quota.
 * Physical deletion goes through the shared storage deletion queue (ResourceDeletedEvent → tb_storage_deletion).
 * Uses Testcontainers only; no real tenant, S3 account or production DB is contacted.
 * The storage deletion scheduler is disabled in application-test.yml (cron "-"), so the processor is called directly.
 */
@SpringBootTest @ActiveProfiles("test")
class DirectDeleteIntegrationTest {
 static final String QUEUE_WHERE="storage_key LIKE 'direct-delete-test/%'";
 @Autowired TrashUseCase trash;@Autowired JdbcTemplate jdbc;@Autowired ResourceRetryService resources;
 @Autowired StorageDeletionProcessor processor;
 @MockitoSpyBean StorageDeletionService deletions;
 @MockitoBean StorageService storage;
 @MockitoBean com.thinkfree.drive.storage.config.S3BucketInitializer bucketInitializer;
 @MockitoBean ResourceAuditService audit;
 @MockitoBean PostProcessingOrchestratorRetry postprocessing;
 List<Long> created=new ArrayList<>();Map<Long,String> keys=new HashMap<>();long quotaBefore;
 @BeforeEach void setup(){TenantContext.set(1);var user=mock(CustomUserDetails.class);when(user.getUserSeq()).thenReturn(2);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of()));quotaBefore=quota();when(storage.deleteObjectsReportingFailures(anyList())).thenReturn(new StorageDeleteObjectsResult(Map.of()));}
 @AfterEach void cleanup(){for(long id:created){jdbc.update("DELETE FROM tb_resource_version WHERE user_seq=2 AND resource_seq=?",id);jdbc.update("DELETE FROM tb_trash WHERE user_seq=2 AND resource_seq=?",id);jdbc.update("DELETE FROM tb_resource WHERE user_seq=2 AND resource_seq=?",id);}jdbc.update("DELETE FROM tb_storage_deletion WHERE "+QUEUE_WHERE);jdbc.update("UPDATE tb_user SET use_quota_byte=? WHERE user_seq=2",quotaBefore);TenantContext.clear();SecurityContextHolder.clearContext();}
 long quota(){return jdbc.queryForObject("SELECT use_quota_byte FROM tb_user WHERE user_seq=2",Long.class);}
 long file(Long parent){String key="direct-delete-test/"+UUID.randomUUID();long id=jdbc.queryForObject("INSERT INTO tb_resource(user_seq,owner_seq,latest_owner_seq,resource_type,resource_name,original_file_name,resource_save_name,ext,content_type,size_byte,register_user_seq,parent_folder_seq,full_path_seq) VALUES(2,2,2,'FILE',?,?,?,'txt','text/plain',10,2,?,?) RETURNING resource_seq",Long.class,key.substring(19)+".txt","test.txt",key,parent,parent==null?"/":jdbc.queryForObject("SELECT full_path_seq FROM tb_resource WHERE resource_seq=? AND user_seq=2",String.class,parent));created.add(id);keys.put(id,key);jdbc.update("INSERT INTO tb_resource_version(user_seq,resource_seq,owner_seq,resource_save_name,size_byte,status,register_user_seq) VALUES(2,?,2,?,5,'SUCCESS',2)",id,key+"-version");jdbc.update("UPDATE tb_user SET use_quota_byte=use_quota_byte+15 WHERE user_seq=2");return id;}
 int count(String table,long id){return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE user_seq=2 AND resource_seq=?",Integer.class,id);}
 int queued(String extraWhere){return jdbc.queryForObject("SELECT count(*) FROM tb_storage_deletion WHERE "+QUEUE_WHERE+extraWhere,Integer.class);}
 StorageDeletionService deletionsTarget(){return AopTestUtils.getUltimateTargetObject(deletions);}
 @Test void commitsMetadataQuotaAndDurableCleanupThenDeletesStorageOutsideTransaction(){long id=file(null);String key=keys.get(id);trash.deleteActivePermanently(id,2,"fixture",ResourceType.FILE);assertThat(count("tb_resource",id)).isZero();assertThat(count("tb_resource_version",id)).isZero();assertThat(count("tb_trash",id)).isZero();assertThat(quota()).isEqualTo(quotaBefore);assertThat(queued("")).isEqualTo(2);verifyNoInteractions(storage);doAnswer(call->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return new StorageDeleteObjectsResult(Map.of());}).when(storage).deleteObjectsReportingFailures(anyList());processor.processDue();verify(storage,times(1)).deleteObjectsReportingFailures(argThat(list->list.containsAll(List.of(key,key+"-version"))));assertThat(queued("")).isZero();}
 @Test void enqueueFailureRollsBackBothDeleteStepsAndQuota(){long id=file(null);doThrow(new IllegalStateException("simulated queue failure")).when(deletionsTarget()).register(anyList());assertThatThrownBy(()->trash.deleteActivePermanently(id,2,"fixture",ResourceType.FILE)).isInstanceOf(RuntimeException.class);assertThat(count("tb_resource",id)).isOne();assertThat(jdbc.queryForObject("SELECT is_delete FROM tb_resource WHERE user_seq=2 AND resource_seq=?",String.class,id)).isEqualTo("N");assertThat(count("tb_resource_version",id)).isOne();assertThat(count("tb_trash",id)).isZero();assertThat(quota()).isEqualTo(quotaBefore+15);assertThat(queued("")).isZero();verifyNoInteractions(storage);}
 @Test void lockedTargetAndWrongTypeRemainUnchanged(){long id=file(null);jdbc.update("UPDATE tb_resource SET is_locked='Y' WHERE user_seq=2 AND resource_seq=?",id);assertThatThrownBy(()->trash.deleteActivePermanently(id,2,"fixture",ResourceType.FILE)).isInstanceOf(RuntimeException.class);jdbc.update("UPDATE tb_resource SET is_locked='N' WHERE user_seq=2 AND resource_seq=?",id);/* 다른 테넌트 차단은 인증 단계(사용자·API 키가 요청 테넌트에 묶임) — 여기서는 종류 불일치·없는 대상이 아무것도 바꾸지 않는지만 본다 */assertThatThrownBy(()->trash.deleteActivePermanently(id,2,"fixture",ResourceType.FOLDER)).isInstanceOf(RuntimeException.class);assertThatThrownBy(()->trash.deleteActivePermanently(-1L,2,"fixture",ResourceType.FILE)).isInstanceOf(RuntimeException.class);assertThat(count("tb_resource",id)).isOne();assertThat(quota()).isEqualTo(quotaBefore+15);assertThat(queued("")).isZero();}
 @Test void storageFailureRetainsRetryAndDoesNotRestoreDeletedMetadata(){long id=file(null);String key=keys.get(id);trash.deleteActivePermanently(id,2,"fixture",ResourceType.FILE);when(storage.deleteObjectsReportingFailures(anyList())).thenReturn(new StorageDeleteObjectsResult(Map.of(key,"storage unavailable",key+"-version","storage unavailable")));Instant before=Instant.now();processor.processDue();assertThat(queued(" AND attempt_count=1 AND last_error='storage unavailable'")).isEqualTo(2);for(Timestamp next:jdbc.queryForList("SELECT next_attempt_date FROM tb_storage_deletion WHERE "+QUEUE_WHERE,Timestamp.class))assertThat(next.toInstant()).isBetween(before.plus(Duration.ofSeconds(50)),Instant.now().plus(Duration.ofSeconds(70)));assertThat(count("tb_resource",id)).isZero();assertThat(quota()).isEqualTo(quotaBefore);when(storage.deleteObjectsReportingFailures(anyList())).thenReturn(new StorageDeleteObjectsResult(Map.of()));jdbc.update("UPDATE tb_storage_deletion SET next_attempt_date=? WHERE "+QUEUE_WHERE,Timestamp.from(Instant.now().minusSeconds(1)));/* 처리기는 JVM 시각으로 비교 — DB now() 는 컨테이너 시계라 앞설 수 있다 */processor.processDue();assertThat(queued("")).isZero();}
 @Test void folderDeletionIncludesVersionsAndRollsBackOnFailure(){long folder=resources.saveFolderWithCopyCheck(FolderUploadDto.builder().userSeq(2).ownerSeq(2).baseFolderName("direct-delete-folder").originalFolderName("direct-delete-folder").build()).resourceSeq();created.add(folder);long child=file(folder);doThrow(new IllegalStateException("queue failed")).when(deletionsTarget()).register(anyList());assertThatThrownBy(()->trash.deleteActivePermanently(folder,2,"fixture",ResourceType.FOLDER)).isInstanceOf(RuntimeException.class);assertThat(count("tb_resource",folder)).isOne();assertThat(count("tb_resource",child)).isOne();assertThat(quota()).isEqualTo(quotaBefore+15);assertThat(queued("")).isZero();doCallRealMethod().when(deletionsTarget()).register(anyList());trash.deleteActivePermanently(folder,2,"fixture",ResourceType.FOLDER);assertThat(count("tb_resource",folder)).isZero();assertThat(count("tb_resource",child)).isZero();assertThat(quota()).isEqualTo(quotaBefore);assertThat(queued("")).isEqualTo(2);}
}

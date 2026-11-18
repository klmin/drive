package com.thinkfree.drive.user.service.impl;

import com.thinkfree.drive.user.dto.UserUpdateDto;
import com.thinkfree.drive.auditlog.service.UserAuditService;
import com.thinkfree.drive.storage.service.StorageService;
import com.thinkfree.drive.user.entity.User;
import com.thinkfree.drive.user.data.UserDetailData;
import com.thinkfree.drive.user.repository.UserRepository;
import com.thinkfree.drive.user.service.UserFindService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 프로필 이미지 변경의 스토리지·DB 순서(작업 E10)와 현재 키 출처 검증.
 * <ul>
 *   <li>삭제는 DB 커밋 뒤 — 커밋 전에 지우면 DB 갱신 실패 시 키만 남은 깨진 아바타가 된다.</li>
 *   <li>새로 올린 객체는 롤백되면 삭제 — DB 에 키가 남지 않아 고아가 된다.</li>
 *   <li>현재 키는 DB 값 — 요청의 sk 를 믿으면 임의 스토리지 객체를 지우거나 프로필로 지정(다운로드)할 수 있다.</li>
 * </ul>
 * 트랜잭션 동기화는 {@link TransactionSynchronizationManager} 를 직접 초기화해 커밋/롤백 콜백을 손으로 부른다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DefaultUserService 프로필 이미지 — 커밋 뒤 삭제·롤백 시 새 객체 삭제·현재 키는 DB 값")
class DefaultUserServiceProfileImageTest {

    private static final int USER_SEQ = 20;
    private static final String OWN_KEY = "profiles/ko/user-" + USER_SEQ + ".png";
    private static final String VICTIM_KEY = "storage/tenant-1/user-99/secret.docx";

    @Mock private UserRepository userRepository;
    @Mock private UserFindService userFindService;
    @Mock private StorageService storageService;
    @Mock private UserAuditService userAuditService;

    @InjectMocks
    private DefaultUserService userService;

    /** PNG 시그니처 — 업로드 형식 검사(ProfileImageValidator)를 통과하는 최소 내용 */
    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    /** 쓰기 리포가 돌려주는 관리 엔티티(W7 — 변경 감지로 저장, 저장 값 = 엔티티 필드) */
    private User managed;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        managed = User.builder().userSeq(USER_SEQ).build();
        given(userRepository.findById(USER_SEQ)).willReturn(managed);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
        LocaleContextHolder.resetLocaleContext();
    }

    private void givenDbProfileKey(String key) {
        UserDetailData user = Mockito.mock(UserDetailData.class);
        given(user.getProfileImageKey()).willReturn(key);
        given(userFindService.findDetailByUserSeq(USER_SEQ)).willReturn(user);
    }

    private static UserUpdateDto request(MockMultipartFile file, String sk, boolean dpi) {
        // 웹 요청의 sk 는 어댑터가 service 입력(UserUpdateDto)으로 넘기지 않는다 — 시나리오 표기용으로만 받는다
        return new UserUpdateDto(null, null, file, null, null, dpi);
    }

    private static void commit() {
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCommit();
            s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }
    }

    private static void rollback() {
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
    }

    /** 변경 감지로 저장될 값 = 관리 엔티티의 키(changeProfile 이 불렸는지도 함께 — 로드 1회) */
    private String savedProfileKey() {
        verify(userRepository).findById(USER_SEQ);
        return managed.getProfileImageKey();
    }

    @Test
    @DisplayName("삭제 요청 — 스토리지 삭제는 커밋 뒤에만, 롤백되면 지우지 않는다")
    void delete_runsAfterCommitOnly() {
        givenDbProfileKey(OWN_KEY);

        userService.updateCurrentUser(USER_SEQ, request(null, OWN_KEY, true));

        verify(storageService, never()).delete(anyString());
        assertThat(savedProfileKey()).isNull();
        rollback();
        verify(storageService, never()).delete(anyString());
        commit();
        verify(storageService).delete(OWN_KEY);
    }

    @Test
    @DisplayName("삭제 요청 — 요청 sk 가 다른 객체 키여도 지우지 않는다(현재 키는 DB 값)")
    void delete_ignoresRequestStorageKey() {
        givenDbProfileKey(null);

        userService.updateCurrentUser(USER_SEQ, request(null, VICTIM_KEY, true));
        commit();

        verify(storageService, never()).delete(anyString());
        assertThat(savedProfileKey()).isNull();
    }

    @Test
    @DisplayName("변경 없음 — 요청 sk 가 아니라 DB 의 현재 키를 그대로 저장한다")
    void keep_usesDbKeyNotRequestKey() {
        givenDbProfileKey(OWN_KEY);

        userService.updateCurrentUser(USER_SEQ, request(null, VICTIM_KEY, false));

        assertThat(savedProfileKey()).isEqualTo(OWN_KEY);
    }

    @Test
    @DisplayName("새 이미지 업로드 — 롤백되면 새로 올린 객체를 지운다")
    void upload_rollbackDeletesUploadedObject() {
        givenDbProfileKey(null);
        var file = new MockMultipartFile("pif", "a.png", "image/png", PNG_BYTES);

        userService.updateCurrentUser(USER_SEQ, request(file, null, false));
        String newKey = savedProfileKey();
        verify(storageService).upload(any(org.springframework.web.multipart.MultipartFile.class), Mockito.eq(newKey));

        rollback();
        verify(storageService).delete(newKey);
    }

    @Test
    @DisplayName("새 이미지 업로드 — 커밋되면 새 객체는 남기고, DB 의 다른 키가 이 사용자 프로필 경로면 커밋 뒤 지운다")
    void upload_commitKeepsNewAndDeletesOldOwnKey() {
        String oldKey = "profiles/en/user-" + USER_SEQ + ".png";
        givenDbProfileKey(oldKey);
        LocaleContextHolder.setLocale(Locale.KOREAN); // 새 키 = profiles/ko/… (이전 키와 다른 경로)
        var file = new MockMultipartFile("pif", "a.png", "image/png", PNG_BYTES);

        userService.updateCurrentUser(USER_SEQ, request(file, null, false));
        String newKey = savedProfileKey();
        assertThat(newKey).isEqualTo(OWN_KEY);
        verify(storageService, never()).delete(anyString());
        commit();
        verify(storageService).delete(oldKey);
        verify(storageService, never()).delete(newKey);
    }

    @Test
    @DisplayName("새 이미지 업로드 — DB 의 이전 키가 이 사용자 프로필 경로가 아니면 지우지 않는다")
    void upload_doesNotDeleteForeignOldKey() {
        givenDbProfileKey(VICTIM_KEY);
        var file = new MockMultipartFile("pif", "a.png", "image/png", PNG_BYTES);

        userService.updateCurrentUser(USER_SEQ, request(file, null, false));
        commit();

        verify(storageService, never()).delete(VICTIM_KEY);
    }

    @Test
    @DisplayName("다운로드 — 프로필 이미지가 없으면 예외 없이 빈 결과를 돌려준다")
    void download_noImageReturnsEmpty() {
        givenDbProfileKey(null);

        assertThat(userService.downloadProfileImage(USER_SEQ).isEmpty()).isTrue();
        verify(storageService, never()).getFileData(anyString());
    }
}

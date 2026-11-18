package com.thinkfree.drive.smtpconfig.service;

import com.thinkfree.drive.smtpconfig.dto.SmtpConfigSaveDto;
import com.thinkfree.drive.email.service.EmailService;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.security.TenantContext;
import com.thinkfree.drive.smtpconfig.data.SmtpConfigData;
import com.thinkfree.drive.smtpconfig.entity.SmtpConfig;
import com.thinkfree.drive.smtpconfig.enums.AuthType;
import com.thinkfree.drive.smtpconfig.enums.EncryptionType;
import com.thinkfree.drive.smtpconfig.enums.SmtpPassword;
import com.thinkfree.drive.smtpconfig.exception.SmtpConfigErrorCode;
import com.thinkfree.drive.smtpconfig.repository.SmtpConfigRepository;
import com.thinkfree.drive.smtpconfig.result.SmtpConfigDetailResult;
import com.thinkfree.drive.smtpconfig.util.SmtpPasswordEncryptor;
import com.thinkfree.drive.tenant.policy.TenantFeature;
import com.thinkfree.drive.tenant.policy.TenantPolicy;
import com.thinkfree.drive.util.MaskingUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link SmtpConfigService#saveOrUpdateSmtpConfig} — 수정은 쓰기 리포의 관리 엔티티를 한 번 로드해 변경 메서드로 고치고(save 없음 — 커밋 때 변경 감지),
 * 없거나 다른 테넌트 행이면 SMTP_CONFIG_NOT_FOUND, 등록은 새 엔티티를 값으로 채워 save.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SmtpConfigService — 등록 save / 수정 관리 엔티티 변경")
class SmtpConfigServiceTest {

    private static final int TENANT = 7;
    private static final int LOGIN_USER = 30;

    @Mock
    private SmtpConfigRepository smtpConfigRepository;
    @Mock
    private SmtpPasswordEncryptor smtpPasswordEncryptor;
    @Mock
    private SmtpConfigFindService smtpConfigFindService;
    @Mock
    private EmailService emailService;
    @Mock
    private TenantPolicy tenantPolicy;
    @InjectMocks
    private SmtpConfigService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        // 수정 없는 행·비밀번호 유지 케이스는 암호화를 부르지 않는다
        lenient().when(smtpPasswordEncryptor.encrypt(anyString())).thenAnswer(inv -> "enc(" + inv.getArgument(0) + ")");
        lenient().when(smtpPasswordEncryptor.decrypt(anyString())).thenAnswer(inv -> {
            String value = inv.getArgument(0);
            return value.substring("enc(".length(), value.length() - 1);
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static SmtpConfigSaveDto request(Integer smtpConfigSeq) {
        return request(smtpConfigSeq, " p w ");
    }

    private static SmtpConfigSaveDto request(Integer smtpConfigSeq, String encPwd) {
        return request(smtpConfigSeq, encPwd, null);
    }

    private static SmtpConfigSaveDto request(Integer smtpConfigSeq, String encPwd, String oauthInfo) {
        return new SmtpConfigSaveDto(smtpConfigSeq, "client", "smtp.example.com", 587, EncryptionType.TLS, AuthType.IDPW,
                "mailer", encPwd, oauthInfo, "from@example.com", "qa@example.com");
    }

    /** 비밀번호 "secret1"·OAuth 토큰 "token12" 가 저장된 같은 테넌트 행 */
    private static SmtpConfig managedWithPassword() {
        SmtpConfig managed = SmtpConfig.create(TENANT, 1);
        managed.changeSettings("old", "old.example.com", 25, EncryptionType.NONE, AuthType.IDPW,
                "old", SmtpPassword.of("enc(secret1)"), "token12", "old@example.com", "old@example.com");
        return managed;
    }

    private static SmtpConfigData dataWithPassword(String encryptedPwd) {
        return dataWith(encryptedPwd, null);
    }

    private static SmtpConfigData dataWith(String encryptedPwd, String oauthInfo) {
        return new SmtpConfigData(5, TENANT, "client", "smtp.example.com", 587, EncryptionType.TLS, AuthType.IDPW,
                "mailer", SmtpPassword.of(encryptedPwd), oauthInfo, "from@example.com", "qa@example.com", 1, null, null, null);
    }

    @Test
    @DisplayName("seq 없음 → 새 엔티티(테넌트·등록자 선언 값 + 요청 값, 비밀번호는 공백 제거 후 암호화) save")
    void insert() {
        service.saveOrUpdateSmtpConfig(LOGIN_USER, request(null));

        ArgumentCaptor<SmtpConfig> captor = ArgumentCaptor.forClass(SmtpConfig.class);
        verify(smtpConfigRepository).save(captor.capture());
        verify(smtpConfigRepository, never()).findById(anyInt());
        SmtpConfig saved = captor.getValue();
        assertThat(saved.getTenantSeq()).isEqualTo(TENANT);
        assertThat(saved.getRegisterUserSeq()).isEqualTo(LOGIN_USER);
        assertThat(saved.getClientName()).isEqualTo("client");
        assertThat(saved.getPort()).isEqualTo(587);
        assertThat(saved.getEncPwd()).isEqualTo(SmtpPassword.of("enc(pw)"));
        assertThat(saved.getTestReceiverEmail()).isEqualTo("qa@example.com");
    }

    @Test
    @DisplayName("seq 있음 → findById 관리 엔티티(같은 테넌트)를 changeSettings 로 고침(save 호출 없음)")
    void update() {
        SmtpConfig managed = SmtpConfig.create(TENANT, 1);
        given(smtpConfigRepository.findById(5)).willReturn(managed);

        service.saveOrUpdateSmtpConfig(LOGIN_USER, request(5));

        assertThat(managed.getClientName()).isEqualTo("client");
        assertThat(managed.getHost()).isEqualTo("smtp.example.com");
        assertThat(managed.getEncPwd()).isEqualTo(SmtpPassword.of("enc(pw)"));
        assertThat(managed.getRegisterUserSeq()).as("등록자 무변경").isEqualTo(1);
        verify(smtpConfigRepository, never()).save(any());
    }

    @Test
    @DisplayName("seq 있음 + 없는 행(findById null) → SMTP_CONFIG_NOT_FOUND, 저장 없음")
    void update_notFound() {
        given(smtpConfigRepository.findById(5)).willReturn(null);

        assertThatThrownBy(() -> service.saveOrUpdateSmtpConfig(LOGIN_USER, request(5)))
                .isInstanceOf(ApiRuntimeException.class)
                .satisfies(ex -> assertThat(((ApiRuntimeException) ex).getCode()).isEqualTo(SmtpConfigErrorCode.SMTP_CONFIG_NOT_FOUND));
        verify(smtpConfigRepository, never()).save(any());
    }

    @Test
    @DisplayName("seq 있음 + 다른 테넌트 행 → 없는 행과 같은 SMTP_CONFIG_NOT_FOUND(Service 의 테넌트 판정), 값 무변경·저장 없음")
    void update_otherTenant_notFound() {
        SmtpConfig otherTenant = SmtpConfig.create(TENANT + 1, 1);
        given(smtpConfigRepository.findById(5)).willReturn(otherTenant);

        assertThatThrownBy(() -> service.saveOrUpdateSmtpConfig(LOGIN_USER, request(5)))
                .isInstanceOf(ApiRuntimeException.class)
                .satisfies(ex -> assertThat(((ApiRuntimeException) ex).getCode()).isEqualTo(SmtpConfigErrorCode.SMTP_CONFIG_NOT_FOUND));
        assertThat(otherTenant.getClientName()).as("변경 메서드 미호출").isNull();
        verify(smtpConfigRepository, never()).save(any());
    }
    @Test
    @DisplayName("수정 + 비밀번호 비어 있음 → 기존 비밀번호 유지(암호화 안 함)")
    void update_blankPassword_keepsExisting() {
        SmtpConfig managed = managedWithPassword();
        given(smtpConfigRepository.findById(5)).willReturn(managed);

        service.saveOrUpdateSmtpConfig(LOGIN_USER, request(5, null));

        assertThat(managed.getEncPwd()).isEqualTo(SmtpPassword.of("enc(secret1)"));
        assertThat(managed.getClientName()).as("다른 값은 변경").isEqualTo("client");
        verify(smtpPasswordEncryptor, never()).encrypt(anyString());
    }

    @Test
    @DisplayName("수정 + 기존 비밀번호의 마스킹 값과 같은 * 문자열 → 실제 입력값이므로 새 비밀번호로 교체")
    void update_asterisksSameAsMasked_replaces() {
        SmtpConfig managed = managedWithPassword();
        given(smtpConfigRepository.findById(5)).willReturn(managed);

        service.saveOrUpdateSmtpConfig(LOGIN_USER, request(5, "*******"));

        assertThat(managed.getEncPwd()).isEqualTo(SmtpPassword.of("enc(*******)"));
    }

    @Test
    @DisplayName("수정 + 새 비밀번호 → 공백 제거 후 암호화해 교체")
    void update_newPassword_replaces() {
        SmtpConfig managed = managedWithPassword();
        given(smtpConfigRepository.findById(5)).willReturn(managed);

        service.saveOrUpdateSmtpConfig(LOGIN_USER, request(5, " new pw "));

        assertThat(managed.getEncPwd()).isEqualTo(SmtpPassword.of("enc(newpw)"));
    }

    @Test
    @DisplayName("수정 + 길이가 다른 * 문자열 → 새 비밀번호로 교체")
    void update_asterisksOfOtherLength_replaces() {
        SmtpConfig managed = managedWithPassword();
        given(smtpConfigRepository.findById(5)).willReturn(managed);

        service.saveOrUpdateSmtpConfig(LOGIN_USER, request(5, "***"));

        assertThat(managed.getEncPwd()).isEqualTo(SmtpPassword.of("enc(***)"));
    }

    @Test
    @DisplayName("등록 + 비밀번호 비어 있음 → 빈 문자열을 암호화해 저장(기존 동작)")
    void insert_blankPassword_encryptsEmpty() {
        service.saveOrUpdateSmtpConfig(LOGIN_USER, request(null, "  "));

        ArgumentCaptor<SmtpConfig> captor = ArgumentCaptor.forClass(SmtpConfig.class);
        verify(smtpConfigRepository).save(captor.capture());
        assertThat(captor.getValue().getEncPwd()).isEqualTo(SmtpPassword.of("enc()"));
    }

    @Test
    @DisplayName("조회(SELF_MANAGED) → 비밀번호 값 대신 저장 여부만")
    void getSmtpConfig_hasPassword() {
        given(smtpConfigFindService.findCurrentSmtpConfig(TENANT)).willReturn(dataWithPassword("enc(secret1)"));
        given(tenantPolicy.allows(TENANT, TenantFeature.SMTP_INFO_VIEW)).willReturn(true);

        SmtpConfigDetailResult result = service.getSmtpConfig();

        assertThat(result.hasPassword()).isTrue();
        verify(smtpPasswordEncryptor, times(1)).decrypt("enc(secret1)");
    }

    @Test
    @DisplayName("조회 + 저장된 비밀번호가 빈 값 → 저장 안 됨")
    void getSmtpConfig_emptyPassword_false() {
        given(smtpConfigFindService.findCurrentSmtpConfig(TENANT)).willReturn(dataWithPassword("enc()"));
        given(tenantPolicy.allows(TENANT, TenantFeature.SMTP_INFO_VIEW)).willReturn(true);

        assertThat(service.getSmtpConfig().hasPassword()).isFalse();
    }
    @Test
    @DisplayName("수정 + OAuth 토큰 비어 있음 → 기존 토큰 유지")
    void update_blankOauthInfo_keepsExisting() {
        SmtpConfig managed = managedWithPassword();
        given(smtpConfigRepository.findById(5)).willReturn(managed);

        service.saveOrUpdateSmtpConfig(LOGIN_USER, request(5, null, null));

        assertThat(managed.getOauthInfo()).isEqualTo("token12");
    }

    @Test
    @DisplayName("수정 + 기존 토큰의 마스킹 값과 같은 * 문자열 → 실제 입력값이므로 새 토큰으로 교체")
    void update_asterisksOauthInfo_replaces() {
        SmtpConfig managed = managedWithPassword();
        given(smtpConfigRepository.findById(5)).willReturn(managed);

        service.saveOrUpdateSmtpConfig(LOGIN_USER, request(5, null, "*******"));

        assertThat(managed.getOauthInfo()).isEqualTo("*******");
    }

    @Test
    @DisplayName("수정 + 새 OAuth 토큰 → 교체")
    void update_newOauthInfo_replaces() {
        SmtpConfig managed = managedWithPassword();
        given(smtpConfigRepository.findById(5)).willReturn(managed);

        service.saveOrUpdateSmtpConfig(LOGIN_USER, request(5, null, "newtoken"));

        assertThat(managed.getOauthInfo()).isEqualTo("newtoken");
    }

    @Test
    @DisplayName("조회(SELF_MANAGED) → OAuth 토큰 값 대신 저장 여부만")
    void getSmtpConfig_hasOauthInfo() {
        given(tenantPolicy.allows(TENANT, TenantFeature.SMTP_INFO_VIEW)).willReturn(true);
        given(smtpConfigFindService.findCurrentSmtpConfig(TENANT)).willReturn(dataWith("enc(secret1)", "token12"));
        assertThat(service.getSmtpConfig().hasOauthInfo()).isTrue();

        given(smtpConfigFindService.findCurrentSmtpConfig(TENANT)).willReturn(dataWith("enc(secret1)", null));
        assertThat(service.getSmtpConfig().hasOauthInfo()).isFalse();
    }
    @Test
    @DisplayName("조회 + 접속 정보 보기 허용(SELF_MANAGED) → 설정 행 그대로")
    void getSmtpConfig_allowed_asIs() {
        SmtpConfigData data = dataWith("enc(secret1)", null);
        given(smtpConfigFindService.findCurrentSmtpConfig(TENANT)).willReturn(data);
        given(tenantPolicy.allows(TENANT, TenantFeature.SMTP_INFO_VIEW)).willReturn(true);

        assertThat(service.getSmtpConfig().smtpConfig()).isSameAs(data);
    }

    @Test
    @DisplayName("조회 + 접속 정보 보기 불가(SYSTEM_MANAGED) → 이름·호스트·계정·발신·수신 주소 __MASKED__, 포트·방식·seq·비밀값 저장 여부 그대로")
    void getSmtpConfig_blocked_masksConnectionInfo() {
        given(smtpConfigFindService.findCurrentSmtpConfig(TENANT)).willReturn(dataWith("enc(secret1)", "token12"));
        given(tenantPolicy.allows(TENANT, TenantFeature.SMTP_INFO_VIEW)).willReturn(false);

        SmtpConfigDetailResult result = service.getSmtpConfig();

        SmtpConfigData masked = result.smtpConfig();
        assertThat(masked.getClientName()).isEqualTo(MaskingUtils.MASKED_PLACEHOLDER);
        assertThat(masked.getHost()).isEqualTo(MaskingUtils.MASKED_PLACEHOLDER);
        assertThat(masked.getPort()).isEqualTo(587);
        assertThat(masked.getUsername()).isEqualTo(MaskingUtils.MASKED_PLACEHOLDER);
        assertThat(masked.getFromAddress()).isEqualTo(MaskingUtils.MASKED_PLACEHOLDER);
        assertThat(masked.getTestReceiverEmail()).isEqualTo(MaskingUtils.MASKED_PLACEHOLDER);
        assertThat(masked.getEncryptionType()).isEqualTo(EncryptionType.TLS);
        assertThat(masked.getAuthType()).isEqualTo(AuthType.IDPW);
        assertThat(masked.getSmtpConfigSeq()).isEqualTo(5);
        assertThat(result.hasPassword()).isTrue();
        assertThat(result.hasOauthInfo()).isTrue();
    }
}

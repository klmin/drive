package com.thinkfree.drive.license.service;

import com.hancom.weboffice.common.license.LicenseManager;
import com.hancom.weboffice.common.license.exception.LicenseValidatorException;
import com.hancom.weboffice.common.license.model.LicenseInfos;
import com.hancom.weboffice.common.license.model.LicenseWrapper;
import com.hancom.weboffice.common.license.parser.AddFlagInfo;
import com.hancom.weboffice.common.license.parser.AddFlagParser;
import com.hancom.weboffice.common.license.v2.LicenseFormatDetector;
import com.hancom.weboffice.common.license.validation.LicenseExpirationValidator;
import com.nimbusds.jwt.SignedJWT;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.time.DriveTime;
import com.thinkfree.drive.license.Constants;
import com.thinkfree.drive.license.auth.enums.LicenseAuthStatus;
import com.thinkfree.drive.license.auth.result.LicenseAuthResult;
import com.thinkfree.drive.license.auth.result.SignedLicenseInfo;
import com.thinkfree.drive.license.auth.service.LicenseAuthService;
import com.thinkfree.drive.license.data.LicenseAuthStateData;
import com.thinkfree.drive.license.data.LicenseVersion;
import com.thinkfree.drive.license.exception.ResultCode;
import com.thinkfree.drive.license.repository.LicenseQueryRepository;
import com.thinkfree.drive.license.repository.LicenseRepository;
import com.thinkfree.drive.license.result.LicensePlanResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class LicenseService {
    
    private static final DateTimeFormatter AUTH_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LicenseRepository licenseRepository;
    private final LicenseQueryRepository licenseQueryRepository;
    private final LicenseAuthService licenseAuthService;

    /**
     * 테넌트별 라이선스 캐시 (license + lastRegDate + version 을 원자적으로 묶음).
     * Why: 라이선스가 인스턴스 단일에서 테넌트 단위로 바뀌면서, 두 필드를 분리해 두면
     *      한 테넌트의 캐시/lastRegDate 가 비원자적으로 갱신/제거되는 race 가 생긴다.
     *      single map + immutable entry 로 read/replace 모두 원자화한다.
     * version 은 캐시를 만든 시점의 DB 버전이다. 파일이 바뀌거나(updated_at) 온라인 인증 결과가
     * 바뀌면(auth_checked_at) 달라진다.
     */
    record CacheEntry(Map<String, Object> license, String lastRegDate, LicenseVersion version) {}

    private final ConcurrentMap<Integer, CacheEntry> cacheByTenant = new ConcurrentHashMap<>();

    public void init(int tenantSeq) {
        try {
            fetchAndSetLicense(tenantSeq, licenseQueryRepository.findLicenseVersion(tenantSeq));
            log.info("[ LicenseService.init ] License initialized successfully. tenantSeq : {}", tenantSeq);
        } catch (LicenseValidatorException e) {
            log.error("[ LicenseService.init ] License is not registered or invalid. tenantSeq : {} [{}]", tenantSeq, e.getResultCode());
        }
    }

    public Map<String, Object> getLicense(int tenantSeq) throws LicenseValidatorException {
        /*
        version 은 아래 두가지 의미를 가진다.
            1. 값이 존재하는 경우 : 마지막 라이선스 갱신시점 + 마지막 온라인 인증 시점
            2. 값이 null인 경우 : 라이선스 미등록

         - 참고: 라이선스는 테넌트당 최대 1개만 저장 됨
         */
        LicenseVersion version = licenseQueryRepository.findLicenseVersion(tenantSeq);

        // check 1. db에 라이선스가 없는 경우 캐시된 라이선스 제거
        if (version == null || version.updatedAt() == null) {
            cacheByTenant.remove(tenantSeq);
            return null;
        }

        CacheEntry entry = cacheByTenant.get(tenantSeq);

        // check 2. 캐시된 라이선스가 없는 경우 db에서 획득 후 반환
        if (entry == null) {
            return fetchAndSetLicense(tenantSeq, version);
        }

        // check 3. 캐시된 라이선스가 최신 라이선스가 아니거나 온라인 인증 결과가 바뀐 경우
        if (!version.equals(entry.version())) {
            return fetchAndSetLicense(tenantSeq, version);
        }

        // check 4. 마지막 라이선스 갱신일의 다음 날로 넘어간 경우
        refreshLicenseIfDateChanged(tenantSeq);

        // 마지막에 캐시를 한 번 더 읽되, 그 사이 다른 스레드가 remove 했더라도
        // 본 호출에서 검증된 local entry 로 graceful fallback (false negative 방지).
        CacheEntry fresh = cacheByTenant.get(tenantSeq);
        return fresh != null ? fresh.license() : entry.license();
    }

    /**
     * 날짜 boundary 를 넘었을 때 시간 의존 필드(spents/state/gracePeriod)를 재계산한다.
     * 진입 전제 (호출자 getLicense 가 보장):
     *   ① DB 에 라이선스 존재 (updatedAt != null)
     *   ② cache entry 존재
     *   ③ cache.UPDATED_AT == DB.updatedAt   ← 즉 캐시 데이터 == DB 데이터
     * 따라서 여기선 "라이선스 자체가 바뀌었나" 다시 확인할 필요 없음 (check 3 가 이미 했음).
     * 오직 "오늘이 어제와 같은 날인가" 만 체크.
     * - 같은 날 → 시간 필드 변화 없음 → 그대로 return
     * - 다른 날 → cache 데이터로 시간 필드만 재계산 (DB·blob 파싱 안 함)
     *            라이선스 자체 변경 감지는 다음 getLicense 호출의 check 3 가 담당.
     */
    private void refreshLicenseIfDateChanged(int tenantSeq) {
        CacheEntry entry = cacheByTenant.get(tenantSeq);

        // nowMs 를 1회만 측정해 isDiffer/populate/cache.put 까지 같은 시점 기준으로 전파.
        // (DB getServerTimeMS 호출도 3회→1회로 감소.)
        long nowMs = getCurrentTimeMS();

        if (!isDifferCurrentDate(entry.lastRegDate(), nowMs)) {
            // 같은 날 — 재계산 불필요
            return;
        }

        // 다른 날 — 시간 필드만 재계산 후 lastRegDate 를 오늘로 갱신
        Map<String, Object> updated = new HashMap<>(entry.license());
        Timestamp issued = (Timestamp) updated.get(LicenseInfos.DATE_ISSUED);
        Timestamp expired = (Timestamp) updated.get(LicenseInfos.DATE_EXPIRED);
        populateLicenseStateFields(updated, issued, expired, nowMs);
        applyAuthBlock(updated);

        cacheByTenant.put(tenantSeq, new CacheEntry(updated, formatDate(nowMs), entry.version()));
    }

    private Map<String, Object> fetchAndSetLicense(int tenantSeq, LicenseVersion version) throws LicenseValidatorException {
        Map<String, Object> licenseData = licenseQueryRepository.findLicense(tenantSeq);

        if (licenseData == null) {
            throw new LicenseValidatorException(ResultCode.LICENSE_FILE_NOT_EXIST.getCode());
        }

        return setLicense(tenantSeq, licenseData, version);
    }

    public Map<String, Object> setLicense(int tenantSeq, Map<String, Object> licenseData, LicenseVersion version) throws LicenseValidatorException {
        byte[] licenseBlob = (byte[]) licenseData.get(Constants.DB.FIELD.DATA);

        LicenseManager licenseManager = buildLicenseManager(licenseBlob);
        validateLicense(licenseManager);

        LicenseWrapper licenseWrapper = licenseManager.getLicenseWrapper();
        Map<String, Object> licenseValue = new HashMap<>();

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Timestamp issuedTime = stringToTimeStamp(licenseWrapper.getParams().getValue(LicenseInfos.DATE_ISSUED));
        Timestamp expiredTime = stringToTimeStamp(licenseWrapper.getParams().getValue(LicenseInfos.DATE_EXPIRED));

        String category = licenseWrapper.getParams().getValue(LicenseInfos.CATEGORY);
        String licenseType = licenseWrapper.getParams().getValue(LicenseInfos.LICENSE_TYPE);
        String licenseId = licenseWrapper.getParams().getValue(LicenseInfos.LICENSE_ID);

        licenseValue.put(Constants.LICENSE.UPDATED_AT, licenseData.get(Constants.DB.FIELD.UPDATED_AT));
        licenseValue.put(Constants.LICENSE.ISSUED_ON, dateFormat.format(new Date(issuedTime.getTime())));
        licenseValue.put(Constants.LICENSE.EXPIRES_ON, dateFormat.format(new Date(expiredTime.getTime())));
        licenseValue.put(Constants.LICENSE.PUBLISHER, Constants.LICENSE.PUBLISHER_THINKFREE);

        licenseValue.put(LicenseInfos.DATE_ISSUED, issuedTime);
        licenseValue.put(LicenseInfos.DATE_EXPIRED, expiredTime);

        licenseValue.put(LicenseInfos.CATEGORY, category);
        licenseValue.put(LicenseInfos.LICENSE_TYPE, licenseType);
        licenseValue.put(LicenseInfos.LICENSE_ID, licenseId);
        licenseValue.put(LicenseInfos.CLIENT_NAME, licenseWrapper.getParams().getValue(LicenseInfos.CLIENT_NAME));
        licenseValue.put(Constants.LICENSE.PAID_WHITE_LABEL, licenseWrapper.getParams().containsKey(LicenseInfos.ADD_FLAG_KEY.PAID_WHITE_LABEL));
        licenseValue.put(LicenseInfos.SUB_CATEGORY, licenseWrapper.getParams().getValue(LicenseInfos.SUB_CATEGORY));
        
        // todo. Trial License 라이선스 분기추가 (system config 추가 예정)

        if (Constants.LICENSE.LICENSE_TYPE_SEAT.equals(licenseType)) {
            licenseValue.put(LicenseInfos.CONSTRAIN_VALUE, licenseWrapper.getParams().getValue(LicenseInfos.CONSTRAIN_VALUE));
        }

        applyAuthResult(tenantSeq, licenseWrapper, licenseValue);

        // nowMs 1회 측정 → populate/cache.put 동일 시점 기준으로 전파 (DB 쿼리 2회→1회).
        long nowMs = getCurrentTimeMS();
        populateLicenseStateFields(licenseValue,
                (Timestamp) licenseValue.get(LicenseInfos.DATE_ISSUED),
                (Timestamp) licenseValue.get(LicenseInfos.DATE_EXPIRED),
                nowMs);
        applyAuthBlock(licenseValue);

        // license + lastRegDate 를 한 번에 원자적으로 갱신
        cacheByTenant.put(tenantSeq, new CacheEntry(licenseValue, formatDate(nowMs), version));

        return licenseValue;
    }

    /**
     * 온라인 인증 라이선스는 인증 서버가 서명해 준 원장 값(시작일·종료일·라이선스 타입·좌석 수·화이트라벨)이
     * 파일 값보다 우선한다. 발급한 쪽에서 종료일이나 좌석 수를 바꾸면 파일을 다시 발급하지 않아도 반영된다.
     * 서명은 저장할 때 확인했으므로 여기서는 내용만 읽는다.
     */
    private void applyAuthResult(int tenantSeq, LicenseWrapper licenseWrapper, Map<String, Object> licenseValue) {
        boolean online = LicenseAuthService.isOnline(licenseWrapper);
        licenseValue.put(Constants.LICENSE.ONLINE_AUTH, online);
        if (!online) {
            return;
        }

        LicenseAuthStateData authState = licenseQueryRepository.findAuthState(tenantSeq);
        if (authState == null) {
            return;
        }

        licenseValue.put(Constants.LICENSE.AUTH_STATUS, authState.authStatus());
        licenseValue.put(Constants.LICENSE.AUTH_FAIL_COUNT, authState.authFailCount() == null ? 0 : authState.authFailCount());
        licenseValue.put(Constants.LICENSE.LAST_AUTH_AT,
                authState.authSuccessAt() == null ? null : DriveTime.toLocal(authState.authSuccessAt()).format(AUTH_TIME_FORMAT));

        SignedLicenseInfo signed = parseSignedData(authState.authSignedData());
        if (signed == null || !String.valueOf(signed.licenseId()).equals(licenseValue.get(LicenseInfos.LICENSE_ID))) {
            return;
        }

        if (signed.startDate() != null && signed.endDate() != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            Timestamp issuedTime = stringToTimeStamp(signed.startDate());
            Timestamp expiredTime = stringToTimeStamp(signed.endDate());
            licenseValue.put(LicenseInfos.DATE_ISSUED, issuedTime);
            licenseValue.put(LicenseInfos.DATE_EXPIRED, expiredTime);
            licenseValue.put(Constants.LICENSE.ISSUED_ON, dateFormat.format(new Date(issuedTime.getTime())));
            licenseValue.put(Constants.LICENSE.EXPIRES_ON, dateFormat.format(new Date(expiredTime.getTime())));
        }

        String licenseType = signed.licenseType() != null ? signed.licenseType() : (String) licenseValue.get(LicenseInfos.LICENSE_TYPE);
        licenseValue.put(LicenseInfos.LICENSE_TYPE, licenseType);

        try {
            AddFlagInfo addFlag = AddFlagParser.parse(signed.addFlag(), licenseType);
            licenseValue.put(Constants.LICENSE.PAID_WHITE_LABEL, addFlag.isPaidWhiteLabel());
            if (Constants.LICENSE.LICENSE_TYPE_SEAT.equals(licenseType) && addFlag.getConstrainValue() != null) {
                licenseValue.put(LicenseInfos.CONSTRAIN_VALUE, addFlag.getConstrainValue());
            }
        } catch (LicenseValidatorException e) {
            log.warn("[ LicenseService.applyAuthResult ] 인증 서버가 준 addFlag 를 읽지 못해 파일 값을 쓴다. tenantSeq : {}", tenantSeq);
        }
    }

    private static SignedLicenseInfo parseSignedData(String signedData) {
        if (signedData == null || signedData.isBlank()) {
            return null;
        }
        try {
            return SignedLicenseInfo.parse(SignedJWT.parse(signedData).getJWTClaimsSet().getSubject());
        } catch (Exception e) {
            log.warn("[ LicenseService.parseSignedData ] 저장된 인증 결과를 읽지 못했다: {}", e.getMessage());
            return null;
        }
    }

    /** 온라인 인증의 만료·시작 전·무효화·인증 실패 누적은 만료일 기준 유예 없이 바로 막는다. */
    private static void applyAuthBlock(Map<String, Object> licenseValue) {
        LicenseAuthStatus authStatus = LicenseAuthStatus.fromName((String) licenseValue.get(Constants.LICENSE.AUTH_STATUS));
        if (authStatus != null && authStatus.isBlocked()) {
            licenseValue.put(Constants.LICENSE.STATE, false);
        }
    }

    /**
     * 발급일/만료일 기준으로 라이선스 시간 필드(spents/days)와 상태 필드(state/gracePeriod)를
     * 계산해 입력 맵에 채워 넣는다. (in-place mutation)
     * Why: 캐시 갱신과 상태 계산을 분리해 set/replace 가 명시적이도록 하기 위함.
     */
    private void populateLicenseStateFields(Map<String, Object> licenseValue, Timestamp issuedTime, Timestamp expiredTime, long nowMs) {
        long aDay = (1000 * 60 * 60 * 24);

        // 발급일로부터 경과일수 (발급 당일은 1일차로 보정)
        int spents = (int) ((nowMs - issuedTime.getTime()) / aDay);
        licenseValue.put(Constants.LICENSE.SPENTS, (spents == 0 ? 1 : spents));

        // 총 유효기간(일)
        int days = (int) ((expiredTime.getTime() - issuedTime.getTime()) / aDay);
        licenseValue.put(Constants.LICENSE.DAYS, days); // 유효기간

        // 라이선스 상태 판정
        if (spents < 0) {
            // 시작일 이전
            licenseValue.put(Constants.LICENSE.STATE, false);
        } else if (spents <= days) {
            // 정상 유효기간 내
            licenseValue.put(Constants.LICENSE.STATE, true);
        } else if (spents <= days + Constants.LICENSE.GRACE_PERIOD_DAYS) {
            // 유예기간
            licenseValue.put(Constants.LICENSE.STATE, false);
            licenseValue.put(Constants.LICENSE.GRACE_PERIOD, true);
        } else { // 만료 - 유예기간 초과
            licenseValue.put(Constants.LICENSE.STATE, false);
        }

        licenseValue.put(Constants.LICENSE.DAYS, days);
        licenseValue.remove("value");
    }

    /**
     * DB 에 저장된 라이선스를 읽어 들일 때 쓴다. 등록 시와 같은 조건을 다시 확인한다.
     * 조건을 어긴 행은 형식 오류로 다뤄 미등록과 구분한다.
     */
    private LicenseManager buildLicenseManager(byte[] licenseBlob) throws LicenseValidatorException {
        if (!isRsaLicense(licenseBlob)) {
            throw new LicenseValidatorException(ResultCode.LICENSE_FILE_NOT_VALID_FORMAT.getCode());
        }

        LicenseManager licenseManager = loadLicenseManager(licenseBlob);

        if (!isDriveLicense(licenseManager)) {
            throw new LicenseValidatorException(ResultCode.LICENSE_FILE_NOT_VALID_FORMAT.getCode());
        }
        return licenseManager;
    }

    private LicenseManager loadLicenseManager(byte[] licenseBlob) throws LicenseValidatorException {
        return new LicenseManager.Builder(licenseBlob, Constants.LICENSE.PRODUCT_VALIDATOR_KEY)
                .addValidator(new LicenseExpirationValidator())
                .load()
                .build();
    }

    /**
     * RSA 서명 라이선스("TR")만 받는다.
     */
    private boolean isRsaLicense(byte[] licenseBlob) {
        return LicenseFormatDetector.detect(licenseBlob) == LicenseFormatDetector.Format.THINKFREE_JWT;
    }

    /**
     * 서명 검증은 제품을 구분하지 않는다. 같은 키로 서명된 다른 제품의 라이선스도 통과하므로
     * 제품명이 Drive 인지 직접 확인한다.
     */
    private boolean isDriveLicense(LicenseManager licenseManager) {
        String productName = licenseManager.getLicenseWrapper().getParams().getValue(LicenseInfos.PRODUCT_NAME);
        return Constants.LICENSE.PRODUCT_VALIDATOR_KEY.equals(productName);
    }

    private void validateLicense(LicenseManager licenseManager) throws LicenseValidatorException {
        try {
            licenseManager.validate();
        } catch (LicenseValidatorException e) {
            if (e.getResultCode() == 6995) {
                // 유예기간 로직을 위해 라이선스 공통모듈(LicenseManager)의 유효기간 검사를 따르지 않음.
                log.warn("[ LicenseService.setLicense ] Grace period active: proceeding with D+8.");
            } else {
                throw e;
            }
        }
    }

    /**
     * 형식·서명·제품 중 무엇이 틀렸든 화면에는 같은 형식 오류(6997)로 알린다.
     * 다른 제품의 AES 라이선스가 복호화 단계에서 6997 로 거부되던 것과 같다. 원인은 로그로 남긴다.
     */
    public void registerLicense(int tenantSeq, byte[] licenseBlob) {
        if (!isRsaLicense(licenseBlob)) {
            throw new ApiRuntimeException(ResultCode.LICENSE_FILE_NOT_VALID_FORMAT).with("reason", "not RSA license");
        }

        LicenseManager licenseManager;
        try {
            licenseManager = loadLicenseManager(licenseBlob);
        } catch (LicenseValidatorException e) {
            throw new ApiRuntimeException(ResultCode.LICENSE_FILE_NOT_VALID_FORMAT)
                    .with("reason", "load failed")
                    .with("resultCode", e.getResultCode());
        }

        if (!isDriveLicense(licenseManager)) {
            throw new ApiRuntimeException(ResultCode.LICENSE_FILE_NOT_VALID_FORMAT)
                    .with("reason", "product mismatch")
                    .with("productName", licenseManager.getLicenseWrapper().getParams().getValue(LicenseInfos.PRODUCT_NAME));
        }

        LicenseWrapper licenseWrapper = licenseManager.getLicenseWrapper();
        LicenseAuthStateData previous = findSameLicenseAuthState(tenantSeq, licenseWrapper);
        LicenseAuthResult authResult = authenticateForRegistration(tenantSeq, licenseBlob, licenseWrapper, previous);

        int result = licenseRepository.replaceLicense(tenantSeq, licenseBlob,
                authResult == null ? null : authResult.toData(tenantSeq), previous != null);

        if (result == 1) {
            log.info("[ LicenseService.registerLicense ] 새로운 라이선스 등록. tenantSeq : {}", tenantSeq);
        } else if (result == 2) {
            log.info("[ LicenseService.registerLicense ] 기존 라이선스 갱신. tenantSeq : {}", tenantSeq);
        } else if (result == 0) {
            log.info("[ LicenseService.registerLicense ] 값이 동일하여 라이선스 변경 없음. tenantSeq : {}", tenantSeq);
        }

        // 등록/갱신 직후 DB 기준으로 캐시를 덮어쓴다 — 비운 채 두면 캐시만 읽는 조회(브랜딩 plan 등)가 미등록으로 응답한다.
        // 버전 비교 없이 put 하므로 동일 blob(result=0)이어도 갱신되고, remove 후 재적재 사이 빈 캐시도 노출되지 않는다.
        try {
            fetchAndSetLicense(tenantSeq, licenseQueryRepository.findLicenseVersion(tenantSeq));
        } catch (LicenseValidatorException e) {
            // 이전 라이선스가 남지 않도록 비운다 — 다음 getLicense 에서 재시도
            cacheByTenant.remove(tenantSeq);
            log.warn("[ LicenseService.registerLicense ] 등록 후 캐시 적재 실패. tenantSeq : {} [{}]", tenantSeq, e.getResultCode());
        } catch (RuntimeException e) {
            // DB 조회 오류 등 — 이전 라이선스가 남지 않도록 비운다
            cacheByTenant.remove(tenantSeq);
            throw e;
        }
    }

    /**
     * 같은 온라인 라이선스를 다시 등록하는 경우 저장된 인증 상태. 처음 등록하거나 다른 라이선스로 바꾸면 null.
     * 연장 후 다시 받은 파일도 같은 라이선스이므로 파일이 아니라 라이선스 ID 로 견준다.
     * 이 상태를 이어받지 않으면 무효화·인증 만료된 라이선스를 다시 올리는 것만으로 유예 1일차로 풀린다.
     */
    private LicenseAuthStateData findSameLicenseAuthState(int tenantSeq, LicenseWrapper licenseWrapper) {
        if (!LicenseAuthService.isOnline(licenseWrapper)) {
            return null;
        }
        LicenseAuthStateData state = licenseQueryRepository.findAuthState(tenantSeq);
        if (state == null || state.authStatus() == null) {
            return null;
        }

        String licenseId = licenseWrapper.getParams().getValue(LicenseInfos.LICENSE_ID);
        try {
            String savedLicenseId = loadLicenseManager(state.data()).getLicenseWrapper().getParams().getValue(LicenseInfos.LICENSE_ID);
            return licenseId != null && licenseId.equals(savedLicenseId) ? state : null;
        } catch (Exception e) {
            log.warn("[ LicenseService.findSameLicenseAuthState ] 저장된 라이선스를 읽지 못해 새 라이선스로 등록한다. tenantSeq : {}", tenantSeq, e);
            return null;
        }
    }

    /**
     * 온라인 인증 라이선스만 등록 전에 INIT 을 보낸다. 오프라인 라이선스면 null.
     * 인증 결과가 차단(만료·시작 전·무효화·인증 만료)이면 등록하지 않는다. 연결 실패는 등록하고 유예 기간으로 기록한다.
     * 같은 라이선스를 다시 등록하면 이전 상태에서 이어 판정한다. 차단된 라이선스는 인증에 성공해야 풀린다.
     */
    private LicenseAuthResult authenticateForRegistration(int tenantSeq, byte[] licenseBlob, LicenseWrapper licenseWrapper,
                                                          LicenseAuthStateData previous) {
        if (!LicenseAuthService.isOnline(licenseWrapper)) {
            return null;
        }
        if (LicenseAuthService.authBaseUrl(licenseWrapper) == null) {
            throw new ApiRuntimeException(ResultCode.LICENSE_FILE_NOT_VALID_FORMAT).with("reason", "online license without auth url");
        }

        LicenseAuthResult authResult = licenseAuthService.authenticateForRegistration(tenantSeq, licenseBlob, licenseWrapper, previous);
        if (authResult.status().isBlocked()) {
            throw new ApiRuntimeException(authResult.status().getResultCode()).with("authErrorCode", authResult.errorCode());
        }
        return authResult;
    }

    private boolean getLicenseState(int tenantSeq) {
        CacheEntry entry = cacheByTenant.get(tenantSeq);
        return entry != null && (boolean) entry.license().getOrDefault(Constants.LICENSE.STATE, false);
    }

    private int getSpents(int tenantSeq) {
        CacheEntry entry = cacheByTenant.get(tenantSeq);
        return (entry == null) ? -1 : (int) entry.license().getOrDefault(Constants.LICENSE.SPENTS, -1);
    }

    private int getDays(int tenantSeq) {
        CacheEntry entry = cacheByTenant.get(tenantSeq);
        return (entry == null) ? -1 : (int) entry.license().getOrDefault(Constants.LICENSE.DAYS, -1);
    }

    private String getExpiredTime(int tenantSeq) {
        CacheEntry entry = cacheByTenant.get(tenantSeq);
        return (entry == null) ? "" : (String) entry.license().getOrDefault(Constants.LICENSE.EXPIRES_ON, "");
    }

    /**
     * 라이선스 상태 정보를 반환한다.
     *
     * 반환되는 Map 구조:
     * {
     *   "state": Boolean,                  // 라이선스 유효 여부
     *   "days": Integer,                 // 라이선스 유효기간
     *   "spents": Integer,                 // 경과 일수
     *   "expiresOn": String,                // 라이선스 만료일 (yyyy-mm-dd)
     *   "resultCode": ResultCode           // 기간(SITE)에 따른 결과 코드
     *       - LICENSE_EXCEED_LIMIT : 라이선스 기간 만료
     *       - LICENSE_UNDER_LIMIT  : 라이선스 만료예정 (0일 ~ 10일 구간)
     * }
     *
     * @return 라이선스 상태 Map
     * @throws LicenseValidatorException 라이선스 검증 실패 시
     */
    public Map<String, Object> getLicenseStateMap(int tenantSeq) throws LicenseValidatorException {
        getLicense(tenantSeq);

        Map<String, Object> result = new HashMap<>();
        int days = getDays(tenantSeq);
        int spents = getSpents(tenantSeq);

        result.put(Constants.LICENSE.STATE, getLicenseState(tenantSeq));
        result.put(Constants.LICENSE.DAYS, days);
        result.put(Constants.LICENSE.SPENTS, spents);
        result.put(Constants.LICENSE.EXPIRES_ON, getExpiredTime(tenantSeq));
        result.put(Constants.LICENSE.PAID_WHITE_LABEL, isPaidWhiteLabel(tenantSeq));

        if (days - spents >= 0 && days - spents <= 10) { // 만료 예정
            result.put("code", ResultCode.LICENSE_UNDER_LIMIT.getCode());
            result.put(Constants.LICENSE.RESULT_MESSAGE, ResultCode.LICENSE_UNDER_LIMIT);
        } else if (days - spents < 0) {
            result.put("code", ResultCode.LICENSE_FILE_EXPIRATION.getCode());
            result.put(Constants.LICENSE.RESULT_MESSAGE, ResultCode.LICENSE_FILE_EXPIRATION);
        }

        // 온라인 인증으로 막힌 경우는 만료일 기준 코드보다 우선한다. 화면은 이 코드면 유예 없이 막는다
        ResultCode authBlockCode = getAuthBlockCode(tenantSeq);
        if (authBlockCode != null) {
            result.put("code", authBlockCode.getCode());
            result.put(Constants.LICENSE.RESULT_MESSAGE, authBlockCode);
        }

        return result;
    }

    private ResultCode getAuthBlockCode(int tenantSeq) {
        CacheEntry entry = cacheByTenant.get(tenantSeq);
        if (entry == null) {
            return null;
        }
        LicenseAuthStatus authStatus = LicenseAuthStatus.fromName((String) entry.license().get(Constants.LICENSE.AUTH_STATUS));
        return authStatus != null && authStatus.isBlocked() ? authStatus.getResultCode() : null;
    }

    public int getConstrainValue(int tenantSeq) {
        CacheEntry entry = cacheByTenant.get(tenantSeq);
        String constrainValueStr = (entry == null) ? "0" : (String) entry.license().getOrDefault(LicenseInfos.CONSTRAIN_VALUE, "0");
        return Integer.parseInt(constrainValueStr);
    }

    public boolean isSeatLicense(int tenantSeq) {
        CacheEntry entry = cacheByTenant.get(tenantSeq);
        if (entry == null){
            return false;
        }
        return Constants.LICENSE.LICENSE_TYPE_SEAT.equals(entry.license().get(LicenseInfos.LICENSE_TYPE));
    }
    
    /**
     * 플랜 속성 스냅샷 (category + subCategory).
     * Why: SeatPolicy 와 같은 이유 — 두 값을 개별 getter 로 읽으면 그 사이 갱신으로 어긋날 수 있어
     *      단일 cache read 로 묶는다. 미등록 테넌트는 EMPTY.
     */
    public LicensePlanResult getLicensePlan(int tenantSeq) {
        CacheEntry entry = cacheByTenant.get(tenantSeq);
        if (entry == null) {
            return LicensePlanResult.EMPTY;
        }
        Map<String, Object> license = entry.license();
        return new LicensePlanResult(
                (String) license.getOrDefault(LicenseInfos.CATEGORY, ""),
                (String) license.getOrDefault(LicenseInfos.SUB_CATEGORY, ""));
    }

    /**
     * 시트 정책 스냅샷.
     * Why: 사용자 등록 SQL 의 atomic seat check 에 isSeat / constrainValue 두 플래그가 동시에 필요.
     *      별도 호출(isSeatLicense + getConstrainValue)로 캐시를 두 번 읽으면 그 사이 갱신이
     *      들어와 두 값이 어긋날 가능성이 있어 단일 cache read 로 묶는다.
     */
    public record SeatPolicy(boolean isSeat, int constrainValue) {}

    public SeatPolicy getSeatPolicy(int tenantSeq) {
        CacheEntry entry = cacheByTenant.get(tenantSeq);
        if (entry == null){
            return new SeatPolicy(false, 0);
        }
        Map<String, Object> license = entry.license();
        boolean isSeat = Constants.LICENSE.LICENSE_TYPE_SEAT.equals(license.get(LicenseInfos.LICENSE_TYPE));
      
        int constrain = isSeat
                ? Integer.parseInt((String) license.getOrDefault(LicenseInfos.CONSTRAIN_VALUE, "0"))
                : 0;
        
        return new SeatPolicy(isSeat, constrain);
    }

    public boolean isPaidWhiteLabel(int tenantSeq) {
        CacheEntry entry = cacheByTenant.get(tenantSeq);
        if (entry == null) {
            return false;
        }
        Object value = entry.license().get(Constants.LICENSE.PAID_WHITE_LABEL);
        return Boolean.TRUE.equals(value);
    }

    /**
     * 일부 라이선스 정보를 화면 표시용으로 변환.
     * 입력 Map 은 캐시 인스턴스일 수 있으므로 방어 복사 후 변경 — 캐시 mutate race 회피.
     */
    public Map<String, Object> convertLicenseForDisplay(Map<String, Object> licenseMap, int currentActiveUserNum) {
        Map<String, Object> result = new HashMap<>(licenseMap);
        // 캐시 비교용 Instant 를 응답에는 설정 시간대 벽시계로 — 기존 LocalDateTime 시절 JSON 형식(yyyy-MM-ddTHH:mm:ss) 유지
        if (result.get(Constants.LICENSE.UPDATED_AT) instanceof Instant updatedAt) {
            result.put(Constants.LICENSE.UPDATED_AT, DriveTime.toLocal(updatedAt));
        }
        String licenseType = (String) result.get(LicenseInfos.LICENSE_TYPE);

        if (Constants.LICENSE.LICENSE_TYPE_SEAT.equals(licenseType)) {
            int constrainValue = Integer.parseInt((String) result.get(LicenseInfos.CONSTRAIN_VALUE));
            int availableSeatNum = constrainValue - currentActiveUserNum;

            result.put("currentSeatNum", currentActiveUserNum);
            result.put("availableSeatNum", availableSeatNum);
        }

        return result;
    }
    

    /**
     * 진입점에서 1회 측정한 nowMs 기준으로 targetDate 와 비교한다.
     * 같은 흐름의 다른 단계와 시점이 어긋나지 않도록 nowMs 를 명시적으로 받는 internal overload.
     */
    private boolean isDifferCurrentDate(String targetDate, long nowMs) {
        try {
            if (targetDate == null) {
                return true;
            }
            return !targetDate.equals(formatDate(nowMs));
        } catch (Exception e) {
            log.error("[ LicenseService.isDifferCurrentDate ] : ", e);
            return true;
        }
    }

    /**
     * 주어진 epoch ms 를 yyyy-MM-dd 문자열로 포맷.
     * 같은 흐름 안에서 spents 계산과 lastRegDate 갱신이 같은 시점 기준이 되도록,
     * 진입점에서 측정한 nowMs 를 그대로 전파해 쓰기 위한 헬퍼.
     */
    private String formatDate(long timeMs) {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date(timeMs));
    }

    public long getCurrentTimeMS() {
        try {
            return licenseQueryRepository.getServerTimeMS();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    public static Timestamp stringToTimeStamp(String strTime) {
        Timestamp timestamp = null;
        try {
            Date date = new SimpleDateFormat("yyyyMMdd").parse(strTime);
            timestamp = new Timestamp(date.getTime());

            return timestamp;
        } catch (Exception e) {
            log.error("[ LicenseService.stringToTimeStamp ] : ", e);
        }

        return timestamp;
    }
    /** 테넌트 삭제 — 이 테넌트의 행 전부 */
    public void deleteByTenant(Integer tenantSeq) {
        licenseRepository.deleteByTenant(tenantSeq);
        // 메모리 캐시는 커밋 후에 비운다 — 커밋 전에 비우면 그사이 getLicense 가 아직 보이는 행으로 다시 채운다.
        // DB 행이 없으면 getLicense 도 지우지만, 캐시만 읽는 조회(브랜딩 plan 등)가 그 전에 옛 값을 보지 않게
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cacheByTenant.remove(tenantSeq);
            }
        });
    }
}

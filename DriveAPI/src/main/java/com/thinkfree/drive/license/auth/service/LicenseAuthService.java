package com.thinkfree.drive.license.auth.service;

import com.hancom.weboffice.common.license.LicenseManager;
import com.hancom.weboffice.common.license.model.LicenseInfos;
import com.hancom.weboffice.common.license.model.LicenseWrapper;
import com.hancom.weboffice.common.license.v2.LicenseFormatDetector;
import com.thinkfree.drive.global.exception.ApiRuntimeException;
import com.thinkfree.drive.global.time.DriveTime;
import com.thinkfree.drive.license.Constants;
import com.thinkfree.drive.license.auth.client.LicenseAuthClientProvider;
import com.thinkfree.drive.license.auth.enums.LicenseAuthStatus;
import com.thinkfree.drive.license.auth.enums.LicenseAuthType;
import com.thinkfree.drive.license.auth.request.LicenseAuthRequest;
import com.thinkfree.drive.license.auth.response.LicenseAuthResponse;
import com.thinkfree.drive.license.auth.result.LicenseAuthResult;
import com.thinkfree.drive.license.auth.result.SignedLicenseInfo;
import com.thinkfree.drive.license.auth.support.AuthResponseVerifier;
import com.thinkfree.drive.license.auth.support.DeviceInfoProvider;
import com.thinkfree.drive.license.data.LicenseAuthStateData;
import com.thinkfree.drive.license.exception.ResultCode;
import com.thinkfree.drive.license.repository.LicenseQueryRepository;
import com.thinkfree.drive.license.repository.LicenseRepository;
import com.thinkfree.drive.license.repository.SeatLicenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 라이선스 온라인 인증.
 *
 * 온라인 인증 라이선스(ADD_FLAG 에 auth 토큰)만 대상이다. 판정은 CloudOffice 와 같다.
 * <ul>
 *   <li>성공 → SUCCESS, 실패 횟수 초기화</li>
 *   <li>서버 거절(6989·6995 만료, 6987 시작 전, 6985 무효화) → 해당 상태, 실패 횟수는 그대로</li>
 *   <li>그 밖의 실패(연결 실패, 응답 서명 불일치 등) → 하루 한 번만 실패 횟수 +1, 허용 일수를 넘으면 AUTH_EXPIRED</li>
 * </ul>
 * 서버가 거절한 라이선스는 연결 실패로 풀리지 않는다. 인증 서버에 닿지 못하게 막아 무효화를 피할 수 없게 하려는 것이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LicenseAuthService {

    private final LicenseRepository licenseRepository;
    private final LicenseQueryRepository licenseQueryRepository;
    private final SeatLicenseRepository seatLicenseRepository;
    private final LicenseAuthClientProvider clientProvider;
    private final AuthResponseVerifier responseVerifier;
    private final DeviceInfoProvider deviceInfoProvider;

    /** 온라인 인증 라이선스인지. ADD_FLAG 에 auth 토큰이 있으면 온라인이다. */
    public static boolean isOnline(LicenseWrapper licenseWrapper) {
        return licenseWrapper.getParams().getValue(LicenseInfos.ADD_FLAG_KEY.AUTH) != null;
    }

    /**
     * 인증 주소. 주소 없이 auth 만 있으면 공통 모듈이 값 자리에 "auth" 를 넣으므로 주소 없음(null)으로 본다.
     */
    public static String authBaseUrl(LicenseWrapper licenseWrapper) {
        String value = licenseWrapper.getParams().getValue(LicenseInfos.ADD_FLAG_KEY.AUTH);
        if (value == null || value.isBlank() || LicenseInfos.ADD_FLAG_KEY.AUTH.equals(value)) {
            return null;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /**
     * 등록하려는 라이선스로 INIT 을 보낸다. 아직 저장 전이므로 결과를 기록하지 않는다.
     * 같은 라이선스를 다시 등록하면 previous 에 저장된 상태를 넘긴다. 처음이면 null.
     */
    public LicenseAuthResult authenticateForRegistration(int tenantSeq, byte[] licenseBlob, LicenseWrapper licenseWrapper,
                                                         LicenseAuthStateData previous) {
        return request(tenantSeq, licenseBlob, licenseWrapper, LicenseAuthType.INIT, previous);
    }

    /**
     * 인증한 파일이 그대로일 때만 결과를 기록한다. 응답을 기다리는 동안 다른 라이선스가 등록됐으면 응답을 버린다.
     * 새 라이선스는 등록할 때 이미 인증했으므로 버려도 빠지는 인증이 없다.
     */
    void save(int tenantSeq, LicenseAuthResult authResult, Instant expectedUpdatedAt) {
        if (licenseRepository.updateAuthResultIfUnchanged(authResult.toData(tenantSeq), expectedUpdatedAt) == 0) {
            log.warn("[ LicenseAuthService.save ] 인증하는 동안 라이선스가 바뀌어 결과를 버린다. tenantSeq : {}", tenantSeq);
            throw new ApiRuntimeException(ResultCode.AUTH).with("reason", "license changed during auth");
        }
    }

    /**
     * 서버가 기동하면 온라인 라이선스마다 RESTART 를 보낸다.
     * 꺼져 있던 동안 바뀐 원장(무효화·종료일 변경)을 바로 반영하고, 설치를 복제·이전한 경우 인증 이력에 그 시점이 남게 한다.
     * 서버마다 한 번씩 보내는 것이 목적이라 분산 락을 걸지 않는다. 기동을 늦추지 않도록 비동기로 돈다.
     */
    @Async
    public void restartAll() {
        authenticateAll(LicenseAuthType.RESTART);
    }

    /**
     * 관리자 화면의 수동 인증. 화면은 온라인 라이선스일 때만 버튼을 보여 주므로
     * 오프라인 라이선스로 들어온 요청은 API 를 직접 부른 경우다.
     */
    public LicenseAuthResult authenticateManually(int tenantSeq) {
        LicenseAuthResult authResult = authenticate(tenantSeq, LicenseAuthType.MANUAL);
        if (authResult == null) {
            throw new ApiRuntimeException(ResultCode.AUTH).with("reason", "not online license");
        }
        return authResult;
    }

    /** 하루 한 번 온라인 라이선스마다 AUTO 를 보낸다. 스케줄러가 분산 락을 잡고 부른다. */
    public void autoAll() {
        authenticateAll(LicenseAuthType.AUTO);
    }

    private void authenticateAll(LicenseAuthType type) {
        for (Integer tenantSeq : licenseQueryRepository.findOnlineLicensedTenantSeqs()) {
            try {
                authenticate(tenantSeq, type);
            } catch (Exception e) {
                // 한 테넌트의 실패가 나머지 테넌트의 인증을 막지 않게 한다
                log.error("[ LicenseAuthService.authenticateAll ] {} 인증 중 오류. tenantSeq : {}", type, tenantSeq, e);
            }
        }
    }

    /**
     * 저장된 온라인 라이선스로 인증하고 결과를 기록한다. 오프라인 라이선스거나 라이선스가 없으면 null.
     * 아직 INIT 에 성공한 적이 없으면 RESTART·AUTO 대신 INIT 을 보낸다.
     * 인증하는 동안 라이선스가 바뀌면 결과를 기록하지 않고 6899 를 던진다.
     */
    public LicenseAuthResult authenticate(int tenantSeq, LicenseAuthType type) {
        LicenseAuthStateData state = licenseQueryRepository.findAuthState(tenantSeq);
        if (state == null || state.authStatus() == null) {
            return null;
        }

        LicenseWrapper licenseWrapper;
        try {
            licenseWrapper = new LicenseManager.Builder(state.data(), Constants.LICENSE.PRODUCT_VALIDATOR_KEY)
                    .load()
                    .build()
                    .getLicenseWrapper();
        } catch (Exception e) {
            log.error("[ LicenseAuthService.authenticate ] 저장된 라이선스를 읽지 못해 인증을 건너뛴다. tenantSeq : {}", tenantSeq, e);
            return null;
        }

        LicenseAuthType resolved = (type != LicenseAuthType.MANUAL && state.authInitAt() == null) ? LicenseAuthType.INIT : type;
        LicenseAuthResult authResult = request(tenantSeq, state.data(), licenseWrapper, resolved, state);
        save(tenantSeq, authResult, state.updatedAt());
        return authResult;
    }

    LicenseAuthResult request(int tenantSeq, byte[] licenseBlob, LicenseWrapper licenseWrapper,
                               LicenseAuthType type, LicenseAuthStateData previous) {
        long nowMs = currentTimeMs();
        Instant now = Instant.ofEpochMilli(nowMs);
        Long licenseId = Long.valueOf(licenseWrapper.getParams().getValue(LicenseInfos.LICENSE_ID));

        String baseUrl = authBaseUrl(licenseWrapper);
        if (baseUrl == null) {
            log.error("[ LicenseAuthService.request ] 인증 주소가 없는 온라인 라이선스. tenantSeq : {}, licenseId : {}", tenantSeq, licenseId);
            return failure(previous, now, null);
        }

        LicenseAuthResponse response;
        try {
            response = clientProvider.get(baseUrl).auth(type.getPath(),
                    buildRequest(tenantSeq, licenseBlob, licenseWrapper, licenseId, nowMs));
        } catch (Exception e) {
            log.warn("[ LicenseAuthService.request ] {} 인증 요청 실패. tenantSeq : {}, licenseId : {}, cause : {}",
                    type, tenantSeq, licenseId, e.getMessage());
            return failure(previous, now, null);
        }

        String signedData = verifiedSignedData(baseUrl, response, licenseId);

        if (response.isSuccess()) {
            if (signedData == null) {
                // 서명이 확인되지 않은 성공은 위조된 응답일 수 있어 성공으로 치지 않는다
                return failure(previous, now, response.errorCode());
            }
            log.info("[ LicenseAuthService.request ] {} 인증 성공. tenantSeq : {}, licenseId : {}", type, tenantSeq, licenseId);
            return new LicenseAuthResult(LicenseAuthStatus.SUCCESS, 0, now,
                    toInstant(response.lastAuthTime()), toInstant(parseMillis(response.initTime())),
                    signedData, null);
        }

        LicenseAuthStatus rejected = LicenseAuthStatus.fromRejectedCode(response.errorCode());
        if (rejected != null) {
            log.warn("[ LicenseAuthService.request ] {} 인증 서버가 거절. tenantSeq : {}, licenseId : {}, errorCode : {}",
                    type, tenantSeq, licenseId, response.errorCode());
            return new LicenseAuthResult(rejected, previousFailCount(previous), now, null, null, signedData, response.errorCode());
        }

        log.warn("[ LicenseAuthService.request ] {} 인증 실패. tenantSeq : {}, licenseId : {}, errorCode : {}, message : {}",
                type, tenantSeq, licenseId, response.errorCode(), response.message());
        return failure(previous, now, response.errorCode());
    }

    private LicenseAuthRequest buildRequest(int tenantSeq, byte[] licenseBlob, LicenseWrapper licenseWrapper,
                                            Long licenseId, long nowMs) {
        String clientId = licenseWrapper.getParams().getValue(LicenseInfos.CLIENT_ID);
        boolean isSeat = Constants.LICENSE.LICENSE_TYPE_SEAT.equals(licenseWrapper.getParams().getValue(LicenseInfos.LICENSE_TYPE));

        return new LicenseAuthRequest(
                licenseId,
                clientId == null || clientId.isBlank() ? 0L : Long.valueOf(clientId),
                String.valueOf(nowMs),
                isSeat ? (long) seatLicenseRepository.countActiveUsers(tenantSeq) : null,
                LicenseFormatDetector.extractCompactJws(licenseBlob),
                deviceInfoProvider.deviceId(),
                deviceInfoProvider.deviceHash());
    }

    /** 서명이 확인되고 같은 라이선스에 대한 응답일 때만 signedData 를 돌려준다. */
    private String verifiedSignedData(String baseUrl, LicenseAuthResponse response, Long licenseId) {
        if (response.signedData() == null || response.signedData().isBlank()) {
            return null;
        }
        try {
            SignedLicenseInfo info = responseVerifier.verify(baseUrl, response.signedData(), response.serverKid());
            if (info == null || !licenseId.equals(info.licenseId())) {
                log.warn("[ LicenseAuthService ] 다른 라이선스에 대한 응답. licenseId : {}", licenseId);
                return null;
            }
            return response.signedData();
        } catch (IllegalStateException e) {
            log.warn("[ LicenseAuthService ] 응답 서명 검증 실패. licenseId : {}, cause : {}", licenseId, e.getMessage());
            return null;
        }
    }

    private LicenseAuthResult failure(LicenseAuthStateData previous, Instant now, String errorCode) {
        LicenseAuthStatus previousStatus = previous == null ? null : LicenseAuthStatus.fromName(previous.authStatus());
        int previousCount = previousFailCount(previous);

        if (previousStatus != null && previousStatus.isRejected()) {
            return new LicenseAuthResult(previousStatus, previousCount, now, null, null, null, errorCode);
        }

        // 실패 횟수는 하루에 한 번만 센다. 같은 날 기동·수동·일일 인증이 겹쳐도 하루로 친다
        boolean countedToday = previousCount > 0
                && previous.authCheckedAt() != null
                && !DriveTime.toLocalDate(previous.authCheckedAt()).isBefore(DriveTime.toLocalDate(now));
        int failCount = countedToday ? previousCount : previousCount + 1;

        LicenseAuthStatus status = failCount > Constants.LICENSE.AUTH_MAX_FAIL_DAYS
                ? LicenseAuthStatus.AUTH_EXPIRED
                : LicenseAuthStatus.GRACE_PERIOD;
        return new LicenseAuthResult(status, failCount, now, null, null, null, errorCode);
    }

    private static int previousFailCount(LicenseAuthStateData previous) {
        return previous == null || previous.authFailCount() == null ? 0 : previous.authFailCount();
    }

    private long currentTimeMs() {
        try {
            return licenseQueryRepository.getServerTimeMS();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private static Long parseMillis(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Instant toInstant(Long epochMillis) {
        return epochMillis == null ? null : Instant.ofEpochMilli(epochMillis);
    }
}

package com.thinkfree.drive.api.v1.share.controller;

import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.type.TypeReference;
import com.thinkfree.drive.abstracts.AbstractMvcTest;
import com.thinkfree.drive.api.response.ApiResponse;
import com.thinkfree.drive.api.v1.share.request.ShareUpdateRequest;
import com.thinkfree.drive.api.v1.share.target.request.ShareTargetRequestItem;
import com.thinkfree.drive.config.security.WithMockUserCustom;
import com.thinkfree.drive.share.enums.ShareType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공유 수정(PATCH /api/v1/shares/{shareSeq}) — 메타 정리 룰셋 9케이스 + 검증 실패 케이스.
 * <p>
 * 룰셋/기대 효과는 SHARE_META_CLEANUP_RULESET.md 기준. 실DB 수동 검증용이라 각 테스트의
 * 전제(공유 유형/리소스/대상 상태)는 javadoc 에 기술하고, 실행 후 아래 4가지를 DB/알림에서 확인한다.
 * <ul>
 * <li>TB_SHARE        : SHARE_TYPE / (링크 유형이면) CAN_* 갱신</li>
 * <li>TB_SHARE_TARGET : ts 전체 교체(diff upsert+삭제). 미제공(null)이면 빈 목록으로 교체 → 기존 대상 전부 삭제</li>
 * <li>메타(TB_RECENT/TB_STARRED/TB_PINNED_FOLDER) : 케이스별 기대 정리 범위 (각 테스트 javadoc)</li>
 * <li>알림             : 추가 대상=공유 알림 / 빠진 대상=전원 해제 알림(메타 유지와 무관) / 링크로만 보던 유저=알림 없음</li>
 * </ul>
 * 테스트 데이터 시퀀스는 실DB 에 맞게 수정해서 사용.
 */
@Slf4j
@WithMockUserCustom
@Transactional
public class ShareControllerUpdateTest extends AbstractMvcTest {

    // ===== 테스트 데이터 (실DB 기준으로 수정해서 사용) =====
    private static final String TEST_HOST = "test6.drive.thinkfree.com";
    private static final int OWNER_USER_SEQ = 902;       // 공유 소유자
    private static final int OWNER_TENANT_SEQ = 6;       // 소유자 테넌트
    private static final int INTERNAL_USER_A = 937;      // 소유자와 같은 테넌트 유저
    private static final int INTERNAL_USER_B = 675;      // 소유자와 같은 테넌트 유저
    private static final int EXTERNAL_USER_A = 876;      // 다른 테넌트 유저 (external 허용 테넌트 전제)
    private static final int EXTERNAL_USER_B = 914;      // 다른 테넌트 유저

    private static final long NOTIFY_WAIT_MILLIS = 3000; // 비동기 알림 처리 대기

    @BeforeAll
    static void setUp() {
        initBaseUrl("/api/v1/shares");
    }

    // ============================================================
    // 1. 최종 PUBLIC_LINK — 메타 정리 없음 (전원 접근 유지)
    // ============================================================

    /**
     * [1-1] public → public : 권한 변경 + 대상 교체.
     * 전제: shareSeq 공유가 PUBLIC_LINK 파일, 대상에 INTERNAL_USER_A·EXTERNAL_USER_A 존재.
     * 기대:
     * - TB_SHARE CAN_* 갱신, TB_SHARE_TARGET 은 INTERNAL_USER_B 로 교체(빠진 대상 행 삭제)
     * - 메타: 빠진 대상 포함 전원 유지 (PUBLIC 링크 접근 유지 — keepsLinkAccess 게이트)
     * - 알림: INTERNAL_USER_B 공유 알림 / 빠진 대상(INTERNAL_USER_A, EXTERNAL_USER_A) 해제 알림
     */
    @WithMockUserCustom(userSeq = OWNER_USER_SEQ, tenantSeq = OWNER_TENANT_SEQ)
    @Test
    @Disabled("dev DB 고정 데이터 의존(REQUEST_006) — DriveFixtures 로 옮길 때 다시 켠다")
    void updatePublicToPublic_1_1() throws InterruptedException {
        Long shareSeq = 1081L;

        var ts = List.of(fileTarget(INTERNAL_USER_B, false));
        var request = publicLinkFileRequest(ts);

        updateOk(shareSeq, request);
        Thread.sleep(NOTIFY_WAIT_MILLIS);
    }

    /**
     * [1-2] org → public : 유형/권한 변경 + 대상 교체.
     * 전제: shareSeq 공유가 ORG_LINK 파일.
     * 기대: SHARE_TYPE=PUBLIC_LINK + CAN_* 갱신. 메타 정리 없음 (전원 접근 유지/확대).
     */
    @WithMockUserCustom(userSeq = OWNER_USER_SEQ, tenantSeq = OWNER_TENANT_SEQ)
    @Test
    @Disabled("dev DB 고정 데이터 의존(REQUEST_006) — DriveFixtures 로 옮길 때 다시 켠다")
    void updateOrgToPublic_1_2() throws InterruptedException {
        Long shareSeq = 1082L;

        var ts = List.of(fileTarget(INTERNAL_USER_A, false));
        var request = publicLinkFileRequest(ts);

        updateOk(shareSeq, request);
        Thread.sleep(NOTIFY_WAIT_MILLIS);
    }

    /**
     * [1-3] specific → public : 유형/권한 변경 + 대상 교체.
     * 전제: shareSeq 공유가 SPECIFIC_USERS 파일, 대상에 INTERNAL_USER_A·B 존재.
     * 기대: 빠진 대상(INTERNAL_USER_B)도 메타 유지 (최종 PUBLIC). target 행은 삭제 + 해제 알림.
     */
    @WithMockUserCustom(userSeq = OWNER_USER_SEQ, tenantSeq = OWNER_TENANT_SEQ)
    @Test
    @Disabled("dev DB 고정 데이터 의존(REQUEST_006) — DriveFixtures 로 옮길 때 다시 켠다")
    void updateSpecificToPublic_1_3() throws InterruptedException {
        Long shareSeq = 1083L;

        var ts = List.of(fileTarget(INTERNAL_USER_A, false));
        var request = publicLinkFileRequest(ts);

        updateOk(shareSeq, request);
        Thread.sleep(NOTIFY_WAIT_MILLIS);
    }

    // ============================================================
    // 2. 최종 ORG_LINK — 외부 테넌트만 정리
    // ============================================================

    /**
     * [2-1] org → org : 권한 변경 + 대상 교체.
     * 전제: shareSeq 공유가 ORG_LINK 파일, 대상에 INTERNAL_USER_A(내부)·EXTERNAL_USER_A(외부) 존재.
     * 기대:
     * - 메타: 빠진 대상 중 [외부 테넌트(EXTERNAL_USER_A)만] 삭제 (상위 대상 있으면 그 유저 제외).
     *   같은 테넌트 빠진 대상(INTERNAL_USER_A)은 조직 링크 접근 유지 → 메타 유지
     * - 알림: 빠진 대상 내·외부 [전원] 해제 알림 (메타 게이트와 무관)
     */
    @WithMockUserCustom(userSeq = OWNER_USER_SEQ, tenantSeq = OWNER_TENANT_SEQ)
    @Test
    @Disabled("dev DB 고정 데이터 의존(REQUEST_006) — DriveFixtures 로 옮길 때 다시 켠다")
    void updateOrgToOrg_2_1() throws InterruptedException {
        Long shareSeq = 1084L;

        var ts = List.of(orgFileTarget(INTERNAL_USER_B, false));
        var request = orgLinkFileRequest(ts);

        updateOk(shareSeq, request);
        Thread.sleep(NOTIFY_WAIT_MILLIS);
    }

    /**
     * [2-2] public → org (파일) : 링크 축소 — 9케이스 중 정리 범위 최대.
     * 전제: shareSeq 공유가 PUBLIC_LINK 파일, 대상에 EXTERNAL_USER_A·EXTERNAL_USER_B 존재.
     * 기대:
     * - externalShrink 채널: 외부 테넌트 유저 메타 일괄 삭제, 단 교체 후 target 으로 남는
     *   EXTERNAL_USER_A 는 보존(preserveUserSeqSet)
     * - deleteResult 채널: 빠진 외부 대상(EXTERNAL_USER_B) 메타 삭제 (sweep 과 겹침 — 멱등 허용)
     * - 같은 테넌트 유저 메타는 전부 유지 (org 링크 접근 유지)
     */
    @WithMockUserCustom(userSeq = OWNER_USER_SEQ, tenantSeq = OWNER_TENANT_SEQ)
    @Test
    @Disabled("dev DB 고정 데이터 의존(REQUEST_006) — DriveFixtures 로 옮길 때 다시 켠다")
    void updatePublicToOrg_2_2_file() throws InterruptedException {
        Long shareSeq = 1085L;

        var ts = List.of(orgFileTarget(EXTERNAL_USER_A, false));
        var request = orgLinkFileRequest(ts);

        updateOk(shareSeq, request);
        Thread.sleep(NOTIFY_WAIT_MILLIS);
    }

    /**
     * [2-2 변형] public → org (폴더, ts 미제공) : 대상 변경 없음 + 외부 sweep.
     * 전제: shareSeq 공유가 PUBLIC_LINK 폴더, 대상에 EXTERNAL_USER_A 존재.
     * 기대:
     * - TB_SHARE_TARGET 변경 없음 (ts 미제공 = 기존 대상 유지)
     * - 잔존 target 은 DB 조회로 보존 (resolveRemainingTargetUserSeqSet) → EXTERNAL_USER_A 메타 유지
     * - 그 외 외부 테넌트 접근자: 폴더 + 접근불가 하위 리소스의 최근/중요 + [핀폴더] 삭제
     * - 해제 알림 없음 (빠진 대상 없음)
     */
    @WithMockUserCustom(userSeq = OWNER_USER_SEQ, tenantSeq = OWNER_TENANT_SEQ)
    @Test
    @Disabled("dev DB 고정 데이터 의존(REQUEST_006) — DriveFixtures 로 옮길 때 다시 켠다")
    void updatePublicToOrg_2_2_folder_withoutTargets() throws InterruptedException {
        Long shareSeq = 1086L;

        var request = orgLinkFolderRequest(null);

        updateOk(shareSeq, request);
        Thread.sleep(NOTIFY_WAIT_MILLIS);
    }

    /**
     * [2-3] specific → org : 2-1과 동일 (외부 일괄 sweep 없음 — specific 의 외부 유저는 target 에만 있었음).
     * 전제: shareSeq 공유가 SPECIFIC_USERS 파일, 대상에 INTERNAL_USER_A·EXTERNAL_USER_A 존재.
     * 기대: 빠진 외부 대상(EXTERNAL_USER_A)만 메타 삭제, 빠진 내부 대상은 메타 유지(org 링크 접근).
     */
    @WithMockUserCustom(userSeq = OWNER_USER_SEQ, tenantSeq = OWNER_TENANT_SEQ)
    @Test
    @Disabled("dev DB 고정 데이터 의존(REQUEST_006) — DriveFixtures 로 옮길 때 다시 켠다")
    void updateSpecificToOrg_2_3() throws InterruptedException {
        Long shareSeq = 1087L;

        var ts = List.of(orgFileTarget(INTERNAL_USER_B, false));
        var request = orgLinkFileRequest(ts);

        updateOk(shareSeq, request);
        Thread.sleep(NOTIFY_WAIT_MILLIS);
    }

    // ============================================================
    // 3. 최종 SPECIFIC_USERS — 빠진 전원/링크 소멸 정리
    // ============================================================

    /**
     * [3-1] specific → specific (파일) : 대상 교체.
     * 전제: shareSeq 공유가 SPECIFIC_USERS 파일, 대상에 INTERNAL_USER_A·B 존재.
     * 기대:
     * - 빠진 대상(INTERNAL_USER_B) [전원] 메타 삭제 (상위에 본인 대상 공유 있으면 그 유저는 제외)
     * - 남는/추가 대상 메타는 유지, 추가 대상 공유 알림 + 빠진 대상 해제 알림
     */
    @WithMockUserCustom(userSeq = OWNER_USER_SEQ, tenantSeq = OWNER_TENANT_SEQ)
    @Test
    @Disabled("dev DB 고정 데이터 의존(REQUEST_006) — DriveFixtures 로 옮길 때 다시 켠다")
    void updateSpecificToSpecific_3_1_file() throws InterruptedException {
        Long shareSeq = 1088L;

        var ts = List.of(
                fileTarget(INTERNAL_USER_A, false),
                fileTarget(EXTERNAL_USER_A, true)       // 신규 추가 + 메일 발송
        );
        var request = specificFileRequest(ts);

        updateOk(shareSeq, request);
        Thread.sleep(NOTIFY_WAIT_MILLIS);
    }

    /**
     * [3-1 변형] specific → specific (폴더) : 유저별 접근불가 하위 정리.
     * 전제: shareSeq 공유가 SPECIFIC_USERS 폴더(하위에 파일/폴더 + 별도 공유 서브트리 존재 권장).
     * 기대:
     * - 빠진 대상의 메타가 [본인이 접근을 잃는 하위 리소스 범위]로 삭제 (cleanUpUserResourceGroups)
     * - 하위의 다른 공유 서브트리(빠진 유저가 별도 접근원 보유)는 정리에서 제외
     * - 핀폴더 포함 정리 (unpinFolderByUserResources)
     */
    @WithMockUserCustom(userSeq = OWNER_USER_SEQ, tenantSeq = OWNER_TENANT_SEQ)
    @Test
    @Disabled("dev DB 고정 데이터 의존(REQUEST_006) — DriveFixtures 로 옮길 때 다시 켠다")
    void updateSpecificToSpecific_3_1_folder() throws InterruptedException {
        Long shareSeq = 1089L;

        var ts = List.of(folderTarget(INTERNAL_USER_A, false));
        var request = specificFolderRequest(ts);

        updateOk(shareSeq, request);
        Thread.sleep(NOTIFY_WAIT_MILLIS);
    }

    /**
     * [3-2] public → specific : 링크 소멸.
     * 전제: shareSeq 공유가 PUBLIC_LINK 파일, 링크로 접근한 제3자(대상 아님)의 최근/중요 메타 존재 권장.
     * 기대:
     * - linkDelete 채널: 소유자·잔존 target(INTERNAL_USER_A) 제외 [전원] 메타 삭제 (USER_SEQ != 소유자 AND NOT IN 잔존)
     * - 링크로만 보던 유저는 해제 알림 없음 (알림은 명시공유 기준 — 빠진 target 만)
     */
    @WithMockUserCustom(userSeq = OWNER_USER_SEQ, tenantSeq = OWNER_TENANT_SEQ)
    @Test
    @Disabled("dev DB 고정 데이터 의존(REQUEST_006) — DriveFixtures 로 옮길 때 다시 켠다")
    void updatePublicToSpecific_3_2() throws InterruptedException {
        Long shareSeq = 1090L;

        var ts = List.of(fileTarget(INTERNAL_USER_A, false));
        var request = specificFileRequest(ts);

        updateOk(shareSeq, request);
        Thread.sleep(NOTIFY_WAIT_MILLIS);
    }

    /**
     * [3-3] org → specific : 3-2와 동일 (링크 소멸, 이전 유형=ORG_LINK).
     * 전제: shareSeq 공유가 ORG_LINK 파일, 조직 유저(대상 아님)의 메타 존재 권장.
     * 기대: 소유자·잔존 target 제외 전원 메타 삭제.
     */
    @WithMockUserCustom(userSeq = OWNER_USER_SEQ, tenantSeq = OWNER_TENANT_SEQ)
    @Test
    @Disabled("dev DB 고정 데이터 의존(REQUEST_006) — DriveFixtures 로 옮길 때 다시 켠다")
    void updateOrgToSpecific_3_3() throws InterruptedException {
        Long shareSeq = 1091L;

        var ts = List.of(fileTarget(INTERNAL_USER_A, false));
        var request = specificFileRequest(ts);

        updateOk(shareSeq, request);
        Thread.sleep(NOTIFY_WAIT_MILLIS);
    }

    // ============================================================
    // 4. ts 의미론 — 미제공(null) = 빈 목록 교체(기존 대상 전부 삭제)
    // ============================================================

    /**
     * 링크 유형 권한만 변경 (ts=null).
     * 전제: shareSeq 공유가 ORG_LINK (유형 유지), 기존 대상 없음.
     * 기대: CAN_* 만 갱신. (기존 대상이 있으면 전부 삭제되므로 권한만 바꾸려면 기존 ts 를 그대로 담아 보내야 함)
     */
    @WithMockUserCustom(userSeq = OWNER_USER_SEQ, tenantSeq = OWNER_TENANT_SEQ)
    @Test
    @Disabled("dev DB 고정 데이터 의존(REQUEST_006) — DriveFixtures 로 옮길 때 다시 켠다")
    void updateLinkPermissionsOnly_withoutTargets() throws InterruptedException {
        Long shareSeq = 1092L;

        var request = orgLinkFileRequest(null);

        updateOk(shareSeq, request);
        Thread.sleep(NOTIFY_WAIT_MILLIS);
    }

    /**
     * SPECIFIC_USERS 인데 ts 미제공 → 성공 (대상 없는 제한됨 공유 허용).
     * 전제: shareSeq 공유가 SPECIFIC_USERS 파일, 기존 대상 존재.
     * 기대:
     * - TB_SHARE_TARGET : 빈 목록으로 교체 → 기존 대상 전부 삭제
     * - 메타: 빠진 대상 전원 정리 (제한됨 — 링크 접근 경로 없음)
     * - 알림: 빠진 대상 전원 해제 알림. 공유 행은 유지(소유자만 접근 가능).
     */
    @WithMockUserCustom(userSeq = OWNER_USER_SEQ, tenantSeq = OWNER_TENANT_SEQ)
    @Test
    @Disabled("dev DB 고정 데이터 의존(REQUEST_006) — DriveFixtures 로 옮길 때 다시 켠다")
    void updateSpecificWithoutTargets_removesAllTargets() throws InterruptedException {
        Long shareSeq = 1088L;

        var request = specificFileRequest(null);

        updateOk(shareSeq, request);
        Thread.sleep(NOTIFY_WAIT_MILLIS);
    }

    // ============================================================
    // 5. 검증 실패 케이스 (ShareWriteValidator / 매트릭스)
    // ============================================================

    /**
     * 링크 유형(org/public)인데 공유 행 권한 플래그 null → UPDATE_FAIL(SHARE_005, 500).
     */
    @WithMockUserCustom(userSeq = OWNER_USER_SEQ, tenantSeq = OWNER_TENANT_SEQ)
    @Test
    @Disabled("dev DB 고정 데이터 의존(REQUEST_006) — DriveFixtures 로 옮길 때 다시 켠다")
    void updateFail_linkPermissionNull() {
        Long shareSeq = 1084L;

        var request = new ShareUpdateRequest(ShareType.ORG_LINK, true, null, true, null, false, null);

        updateFail(shareSeq, request, INTERNAL_SERVER_ERROR.value(), status().isInternalServerError());
    }

    /**
     * 공유 설정상 불허된 유형으로 변경 → SHARE_TYPE_NOT_ALLOWED(SHARE_013, 403).
     * 전제: 소유자 테넌트 공유 설정에서 전체공개(public) OFF.
     */
    @WithMockUserCustom(userSeq = OWNER_USER_SEQ, tenantSeq = OWNER_TENANT_SEQ)
    @Test
    void updateFail_shareTypeNotAllowed() {
        Long shareSeq = 1085L;

        var request = publicLinkFileRequest(null);

        updateFail(shareSeq, request, FORBIDDEN_VALUE, status().isForbidden());
    }

    /**
     * external 불허 테넌트에서 외부 테넌트 대상 포함 → EXTERNAL_SHARE_NOT_ALLOWED(SHARE_014, 403).
     * 전제: 소유자 테넌트 공유 설정에서 외부공유(external) OFF.
     */
    @WithMockUserCustom(userSeq = OWNER_USER_SEQ, tenantSeq = OWNER_TENANT_SEQ)
    @Test
    void updateFail_externalNotAllowed() {
        Long shareSeq = 1088L;

        var ts = List.of(fileTarget(EXTERNAL_USER_A, false));
        var request = specificFileRequest(ts);

        updateFail(shareSeq, request, FORBIDDEN_VALUE, status().isForbidden());
    }

    /**
     * 제한됨(specific) 불허 테넌트에서 대상 포함 → SPECIFIC_USERS_NOT_ALLOWED(SHARE_011, 403).
     * 전제: 소유자 테넌트 공유 설정에서 제한됨(specific) OFF. (링크 유형이라도 대상을 얹으려면 specific 허용 필요)
     */
    @WithMockUserCustom(userSeq = OWNER_USER_SEQ, tenantSeq = OWNER_TENANT_SEQ)
    @Test
    void updateFail_specificUsersNotAllowed() {
        Long shareSeq = 1084L;

        var ts = List.of(orgFileTarget(INTERNAL_USER_A, false));
        var request = orgLinkFileRequest(ts);

        updateFail(shareSeq, request, FORBIDDEN_VALUE, status().isForbidden());
    }

    /**
     * 공유 기능 OFF 테넌트 → SHARE_DISABLED(SHARE_012, 403).
     * 전제: 소유자 테넌트 share_status OFF.
     */
    @WithMockUserCustom(userSeq = OWNER_USER_SEQ, tenantSeq = OWNER_TENANT_SEQ)
    @Test
    void updateFail_shareDisabled() {
        Long shareSeq = 1088L;

        var ts = List.of(fileTarget(INTERNAL_USER_A, false));
        var request = specificFileRequest(ts);

        updateFail(shareSeq, request, FORBIDDEN_VALUE, status().isForbidden());
    }

    /**
     * 상속 공유(상위/하위에 공유 존재) 리소스에서 다른 유형으로 변경 → INVALID_SHARE_TYPE(SHARE_008, 400).
     * 전제: shareSeq 리소스의 상위(또는 하위)에 다른 공유 존재 + 그 유형과 다른 st 요청.
     */
    @WithMockUserCustom(userSeq = OWNER_USER_SEQ, tenantSeq = OWNER_TENANT_SEQ)
    @Test
    @Disabled("dev DB 고정 데이터 의존(REQUEST_006) — DriveFixtures 로 옮길 때 다시 켠다")
    void updateFail_inheritedShareTypeChange() {
        Long shareSeq = 1093L;

        var request = publicLinkFileRequest(null);

        updateFail(shareSeq, request, BAD_REQUEST.value(), status().isBadRequest());
    }

    /**
     * 대상에 소유자 본인 포함 → UPDATE_FAIL(SHARE_005, 500).
     */
    @WithMockUserCustom(userSeq = OWNER_USER_SEQ, tenantSeq = OWNER_TENANT_SEQ)
    @Test
    @Disabled("dev DB 고정 데이터 의존(REQUEST_006) — DriveFixtures 로 옮길 때 다시 켠다")
    void updateFail_selfTarget() {
        Long shareSeq = 1088L;

        var ts = List.of(fileTarget(OWNER_USER_SEQ, false));
        var request = specificFileRequest(ts);

        updateFail(shareSeq, request, INTERNAL_SERVER_ERROR.value(), status().isInternalServerError());
    }

    /**
     * 타겟 권한 위반 — 파일 대상에 UPLOAD(cu=true) → 400.
     * 타겟은 공유 유형 제약을 받지 않고 전체 권한 부여 가능(예: ORG_LINK 대상에 SHARE 허용)하나,
     * 리소스 유형 제약은 유지된다(UPLOAD 는 폴더 전용). — SharePermissionValidator.validateTarget
     */
    @WithMockUserCustom(userSeq = OWNER_USER_SEQ, tenantSeq = OWNER_TENANT_SEQ)
    @Test
    @Disabled("dev DB 고정 데이터 의존(REQUEST_006) — DriveFixtures 로 옮길 때 다시 켠다")
    void updateFail_targetUploadOnFile() {
        Long shareSeq = 1084L;

        var ts = List.of(new ShareTargetRequestItem(INTERNAL_USER_A, true, true, true, true, false, false));
        var request = orgLinkFileRequest(ts);

        updateFail(shareSeq, request, BAD_REQUEST.value(), status().isBadRequest());
    }

    // ============================================================
    // 헬퍼
    // ============================================================

    private static final int FORBIDDEN_VALUE = 403;

    /** 파일 SPECIFIC_USERS 대상 (cv/ce/cd, cu=폴더전용 false) */
    private ShareTargetRequestItem fileTarget(int userSeq, boolean sendEmail) {
        return new ShareTargetRequestItem(userSeq, true, true, true, false, false, sendEmail);
    }

    /** 폴더 SPECIFIC_USERS 대상 (cv/ce/cd/cu) */
    private ShareTargetRequestItem folderTarget(int userSeq, boolean sendEmail) {
        return new ShareTargetRequestItem(userSeq, true, true, true, true, false, sendEmail);
    }

    /** 파일 ORG_LINK 대상 (cv/ce/cd — SHARE 는 SPECIFIC 전용이라 false) */
    private ShareTargetRequestItem orgFileTarget(int userSeq, boolean sendEmail) {
        return new ShareTargetRequestItem(userSeq, true, true, true, false, false, sendEmail);
    }

    /** 파일 PUBLIC_LINK 요청 (매트릭스: VIEW/EDIT/DOWNLOAD) */
    private ShareUpdateRequest publicLinkFileRequest(List<ShareTargetRequestItem> ts) {
        return new ShareUpdateRequest(ShareType.PUBLIC_LINK, true, true, true, false, false, ts);
    }

    /** 파일 ORG_LINK 요청 (매트릭스: VIEW/EDIT/DOWNLOAD) */
    private ShareUpdateRequest orgLinkFileRequest(List<ShareTargetRequestItem> ts) {
        return new ShareUpdateRequest(ShareType.ORG_LINK, true, true, true, false, false, ts);
    }

    /** 폴더 ORG_LINK 요청 (매트릭스: VIEW/EDIT/UPLOAD/DOWNLOAD) */
    private ShareUpdateRequest orgLinkFolderRequest(List<ShareTargetRequestItem> ts) {
        return new ShareUpdateRequest(ShareType.ORG_LINK, true, true, true, true, false, ts);
    }

    /** 파일 SPECIFIC_USERS 요청 (공유 행 CAN_* 는 미사용 — 대상별 권한이 적용) */
    private ShareUpdateRequest specificFileRequest(List<ShareTargetRequestItem> ts) {
        return new ShareUpdateRequest(ShareType.SPECIFIC_USERS, true, true, true, false, false, ts);
    }

    /** 폴더 SPECIFIC_USERS 요청 */
    private ShareUpdateRequest specificFolderRequest(List<ShareTargetRequestItem> ts) {
        return new ShareUpdateRequest(ShareType.SPECIFIC_USERS, true, true, true, true, false, ts);
    }

    private void updateOk(Long shareSeq, ShareUpdateRequest request) {
        setTestHost(TEST_HOST);
        ApiResponse<Void> response = patchForObject(shareSeq.toString(), request, new TypeReference<>() {}, status().isOk());

        assertTrue(response.isResult());
        assertEquals(OK.value(), response.getCode());
        assertEquals(OK.getReasonPhrase(), response.getMessage());
    }

    private void updateFail(Long shareSeq, ShareUpdateRequest request, int expectedCode, org.springframework.test.web.servlet.ResultMatcher statusMatcher) {
        setTestHost(TEST_HOST);
        ApiResponse<Void> response = patchForObject(shareSeq.toString(), request, new TypeReference<>() {}, statusMatcher);

        assertFalse(response.isResult());
        assertEquals(expectedCode, response.getCode());
    }

}

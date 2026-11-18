package com.thinkfree.drive.auditlog.validator.impl;

import com.thinkfree.drive.auditlog.enums.AccessMethod;
import com.thinkfree.drive.auditlog.enums.EventName;
import com.thinkfree.drive.auditlog.payload.AuditLogPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// 이 테스트는 이벤트가 아니라 CollectionShape 기준으로 구성된다(하나의 shape가 여러 EventName을 대표).
// 특정 이벤트의 필수 항목 규칙이 바뀌면, 그 이벤트의 CollectionShape(EventName.java 매핑 참고)에 해당하는
// @Nested Shape 내 테스트(@DisplayName에 shape명 명시)를 수정할 것.
@DisplayName("DefaultAuditLogPayloadValidator 단위 테스트")
class DefaultAuditLogPayloadValidatorTest {

    private DefaultAuditLogPayloadValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DefaultAuditLogPayloadValidator();
    }

    // 공통 필수 필드(eventName 제외)를 모두 채운 builder. 각 테스트는 여기에 eventName + shape 필드만 얹는다.
    private AuditLogPayload.AuditLogPayloadBuilder commonFilled() {
        return AuditLogPayload.builder()
                .eventRegisterDate(Instant.now())
                .tenantSeq(1)
                .userSeq(10)
                .userId("actor@thinkfree.com")
                .userName("행위자")
                .accessMethod(AccessMethod.WEB);
        // eventCategory/eventName은 각 테스트가 eventName을 넣으면 payload 빌드시 함께 지정
    }

    @Nested
    @DisplayName("공통 필수 필드 검사")
    class Common {

        @Test
        @DisplayName("공통 필수 필드가 모두 있으면 누락 없음 (NONE shape)")
        void allCommonPresent_noMissing() {
            var payload = commonFilled()
                    .eventCategory(EventName.LOGIN_SUCCESS.getEventCategory())
                    .eventName(EventName.LOGIN_SUCCESS)
                    .build();

            assertThat(validator.collectMissingRequiredFields(payload)).isEmpty();
        }

        @Test
        @DisplayName("tenantSeq/userSeq/userId/accessMethod/eventRegisterDate 누락 시 각각 잡아낸다")
        void commonMissing_detected() {
            var payload = AuditLogPayload.builder()
                    .eventCategory(EventName.LOGIN_SUCCESS.getEventCategory())
                    .eventName(EventName.LOGIN_SUCCESS)
                    .build();

            assertThat(validator.collectMissingRequiredFields(payload))
                    .contains("eventRegisterDate", "tenantSeq", "userSeq", "userId", "accessMethod");
        }

        @Test
        @DisplayName("userName/ipAddress는 nullable이라 없어도 누락으로 잡지 않는다")
        void nullableCommon_notRequired() {
            var payload = commonFilled()
                    .userName(null)
                    .ipAddress(null)
                    .eventCategory(EventName.LOGIN_SUCCESS.getEventCategory())
                    .eventName(EventName.LOGIN_SUCCESS)
                    .build();

            assertThat(validator.collectMissingRequiredFields(payload)).isEmpty();
        }

        @Test
        @DisplayName("eventName이 null이면 eventName을 누락으로 잡고, shape 판단은 건너뛴다(NPE 없음)")
        void eventNameNull_flaggedAndNoNpe() {
            var payload = commonFilled().build(); // eventCategory/eventName 미지정

            var missing = validator.collectMissingRequiredFields(payload);
            assertThat(missing).contains("eventName", "eventCategory");
        }

        @Test
        @DisplayName("필수 필드가 빈 문자열이면 누락으로 잡는다")
        void blankString_treatedAsMissing() {
            var payload = commonFilled()
                    .userId("   ") // blank
                    .eventCategory(EventName.LOGIN_SUCCESS.getEventCategory())
                    .eventName(EventName.LOGIN_SUCCESS)
                    .build();

            assertThat(validator.collectMissingRequiredFields(payload)).contains("userId");
        }
    }

    @Nested
    @DisplayName("shape별 필수 항목 검사")
    class Shape {

        @Test
        @DisplayName("RESOURCE - objectSeq/objectName 필수, resourcePath는 필수 아님")
        void resource() {
            var missing = validator.collectMissingRequiredFields(
                    commonFilled()
                            .eventCategory(EventName.FILE_UPLOAD.getEventCategory())
                            .eventName(EventName.FILE_UPLOAD)
                            .build());

            assertThat(missing).contains("objectSeq", "objectName");
            assertThat(missing).doesNotContain("resourcePath");
        }

        @Test
        @DisplayName("RESOURCE_DELETE - objectSeq/objectName 필수, resourcePath는 필수 아님(완전삭제는 경로 없음)")
        void resourceDelete() {
            var missing = validator.collectMissingRequiredFields(
                    commonFilled()
                            .eventCategory(EventName.FILE_PERMANENT_DELETE.getEventCategory())
                            .eventName(EventName.FILE_PERMANENT_DELETE)
                            .build());

            assertThat(missing).contains("objectSeq", "objectName");
            assertThat(missing).doesNotContain("resourcePath");
        }

        @Test
        @DisplayName("RESOURCE_CHANGE - objectSeq/objectName/beforeValue/afterValue 필수")
        void resourceChange() {
            var missing = validator.collectMissingRequiredFields(
                    commonFilled()
                            .eventCategory(EventName.FILE_RENAME.getEventCategory())
                            .eventName(EventName.FILE_RENAME)
                            .build());

            assertThat(missing).contains("objectSeq", "objectName", "beforeValue", "afterValue");
        }

        @Test
        @DisplayName("USER - objectSeq/objectUserId/objectName 필수")
        void user() {
            var missing = validator.collectMissingRequiredFields(
                    commonFilled()
                            .eventCategory(EventName.USER_CREATE.getEventCategory())
                            .eventName(EventName.USER_CREATE)
                            .build());

            assertThat(missing).contains("objectSeq", "objectUserId", "objectName");
        }

        @Test
        @DisplayName("USER_CHANGE - USER + before/after 필수")
        void userChange() {
            var missing = validator.collectMissingRequiredFields(
                    commonFilled()
                            .eventCategory(EventName.USER_INFO_UPDATE.getEventCategory())
                            .eventName(EventName.USER_INFO_UPDATE)
                            .build());

            assertThat(missing).contains("objectSeq", "objectUserId", "objectName", "beforeValue", "afterValue");
        }

        @Test
        @DisplayName("VALUE_CHANGE - afterValue만 필수(beforeValue는 델타 방식이라 필수 아님)")
        void valueChange() {
            var missing = validator.collectMissingRequiredFields(
                    commonFilled()
                            .eventCategory(EventName.SETTING_ALLOWED_IP_CHANGE.getEventCategory())
                            .eventName(EventName.SETTING_ALLOWED_IP_CHANGE)
                            .build());

            assertThat(missing).contains("afterValue");
            assertThat(missing).doesNotContain("beforeValue");
        }

        @Test
        @DisplayName("SETTING_CHANGE - objectSeq/objectName/before/after 필수(objectUserId 아님)")
        void settingChange() {
            var missing = validator.collectMissingRequiredFields(
                    commonFilled()
                            .eventCategory(EventName.SETTING_UPDATE.getEventCategory())
                            .eventName(EventName.SETTING_UPDATE)
                            .build());

            assertThat(missing).contains("objectSeq", "objectName", "beforeValue", "afterValue");
            assertThat(missing).doesNotContain("objectUserId");
        }

        @Test
        @DisplayName("모든 필수 항목이 채워지면 누락 없음 (USER_CHANGE 예시)")
        void fullyFilled_noMissing() {
            var payload = commonFilled()
                    .eventCategory(EventName.USER_INFO_UPDATE.getEventCategory())
                    .eventName(EventName.USER_INFO_UPDATE)
                    .objectSeq(100L)
                    .objectUserId("target@thinkfree.com")
                    .objectName("대상자")
                    .beforeValue("{\"name\":\"a\"}")
                    .afterValue("{\"name\":\"b\"}")
                    .build();

            assertThat(validator.collectMissingRequiredFields(payload)).isEmpty();
        }
    }

    @Nested
    @DisplayName("SHARE 계열 target 조건부 검사")
    class ShareTarget {

        @Test
        @DisplayName("SHARE_CREATE 링크 레벨 row(target 전부 없음) - target을 요구하지 않는다")
        void shareCreateLinkLevel_targetNotRequired() {
            var payload = commonFilled()
                    .eventCategory(EventName.SHARE_CREATE.getEventCategory())
                    .eventName(EventName.SHARE_CREATE)
                    .objectSeq(100L)
                    .objectName("공유문서.docx")
                    .afterValue("{\"scope\":\"PUBLIC\"}")
                    .build();

            assertThat(validator.collectMissingRequiredFields(payload)).isEmpty();
        }

        @Test
        @DisplayName("SHARE_CREATE 대상자별 row - target이 일부만 있으면 나머지 target을 누락으로 잡는다")
        void shareCreatePartialTarget_detected() {
            var payload = commonFilled()
                    .eventCategory(EventName.SHARE_CREATE.getEventCategory())
                    .eventName(EventName.SHARE_CREATE)
                    .objectSeq(100L)
                    .objectName("공유문서.docx")
                    .afterValue("{\"permissions\":[\"VIEW\"]}")
                    .targetUserSeq(20) // targetUserId/targetUserName 누락
                    .build();

            assertThat(validator.collectMissingRequiredFields(payload))
                    .contains("targetUserId", "targetUserName");
        }

        @Test
        @DisplayName("SHARE_CREATE 대상자별 row - target 3개 다 있으면 누락 없음")
        void shareCreateFullTarget_noMissing() {
            var payload = commonFilled()
                    .eventCategory(EventName.SHARE_CREATE.getEventCategory())
                    .eventName(EventName.SHARE_CREATE)
                    .objectSeq(100L)
                    .objectName("공유문서.docx")
                    .afterValue("{\"permissions\":[\"VIEW\"]}")
                    .targetUserSeq(20)
                    .targetUserId("target@thinkfree.com")
                    .targetUserName("대상자")
                    .build();

            assertThat(validator.collectMissingRequiredFields(payload)).isEmpty();
        }

        @Test
        @DisplayName("SHARE_PERMISSION_CHANGE - objectSeq/objectName/beforeValue/afterValue 필수 + target 조건부")
        void sharePermissionChange() {
            // 값 누락 케이스: object/before/after 다 없음, target도 없음(scope 변경 row) → target은 요구 안 함
            var missing = validator.collectMissingRequiredFields(
                    commonFilled()
                            .eventCategory(EventName.SHARE_PERMISSION_CHANGE.getEventCategory())
                            .eventName(EventName.SHARE_PERMISSION_CHANGE)
                            .build());
            assertThat(missing).contains("objectSeq", "objectName", "beforeValue", "afterValue");
            assertThat(missing).doesNotContain("targetUserSeq", "targetUserId", "targetUserName");

            // 대상자별 권한변경 row: target 일부만 있으면 나머지 target 누락
            var partialTarget = validator.collectMissingRequiredFields(
                    commonFilled()
                            .eventCategory(EventName.SHARE_PERMISSION_CHANGE.getEventCategory())
                            .eventName(EventName.SHARE_PERMISSION_CHANGE)
                            .objectSeq(100L).objectName("문서")
                            .beforeValue("{\"permissions\":[\"VIEW\"]}")
                            .afterValue("{\"permissions\":[\"VIEW\",\"EDIT\"]}")
                            .targetUserSeq(20)
                            .build());
            assertThat(partialTarget).containsExactlyInAnyOrder("targetUserId", "targetUserName");
        }

        @Test
        @DisplayName("SHARE_CREATE는 afterValue 필수, SHARE_REMOVE는 beforeValue 필수")
        void shareCreateAfter_shareRemoveBefore() {
            var createMissing = validator.collectMissingRequiredFields(
                    commonFilled()
                            .eventCategory(EventName.SHARE_CREATE.getEventCategory())
                            .eventName(EventName.SHARE_CREATE)
                            .objectSeq(100L).objectName("문서")
                            .build());
            assertThat(createMissing).contains("afterValue");

            var removeMissing = validator.collectMissingRequiredFields(
                    commonFilled()
                            .eventCategory(EventName.SHARE_REMOVE.getEventCategory())
                            .eventName(EventName.SHARE_REMOVE)
                            .objectSeq(100L).objectName("문서")
                            .build());
            assertThat(removeMissing).contains("beforeValue");
        }
    }
}

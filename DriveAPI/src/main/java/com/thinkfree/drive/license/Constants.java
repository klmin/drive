package com.thinkfree.drive.license;

public class Constants {

    public static class LICENSE {
        public static final String PRODUCT_VALIDATOR_KEY = "tDrive";
        public static final String COMPANY = "company";
        public static final String STATE = "state";
        public static final String TYPE = "type";
        public static final String ISSUED_ON = "issuedOn";              // 발급일
        public static final String EXPIRES_ON = "expiresOn";            // 만료일
        public static final String SPENTS = "spents";                   // 시작일부터 현재까지 일수
        public static final String DAYS = "days";                       // 라이언스 일수 (expiresOn - issuedOn, 라이선스 만료면 0)
        public static final String PUBLISHER = "publisher";
        public static final String PUBLISHER_THINKFREE = "TF";
        public static final String LICENSE_CATEGORY_TRIAL = "TRIAL";
        public static final String LICENSE_TYPE_SEAT = "SEAT";
        public static final String RESULT_CODE = "resultCode";
        public static final String RESULT_MESSAGE = "resultMessage";
        public static final String UPDATED_AT = "updatedAt";
        public static final String GRACE_PERIOD = "gracePeriod";
        public static final int GRACE_PERIOD_DAYS = 8;
        public static final String PAID_WHITE_LABEL = "paidWhiteLabel";
        public static final int AUTH_MAX_FAIL_DAYS = 30;                // 온라인 인증 연속 실패 허용 일수
        public static final String ONLINE_AUTH = "onlineAuth";          // 온라인 인증 라이선스 여부
        public static final String AUTH_STATUS = "authStatus";          // 온라인 인증 상태
        public static final String LAST_AUTH_AT = "lastAuthAt";         // 마지막 온라인 인증 성공 시각 (yyyy-MM-dd HH:mm:ss)
        public static final String AUTH_FAIL_COUNT = "authFailCount";   // 온라인 인증 연속 실패 일수
    }

    public static class DB {
        public static class FIELD {
            public static final String TENANT_SEQ = "tenant_seq";
            public static final String DATA = "data";
            public static final String CREATED_AT = "created_at";
            public static final String UPDATED_AT = "updated_at";
        }
    }
}
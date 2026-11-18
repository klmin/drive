package com.thinkfree.drive.systemconfig.data;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OfficeData {
    @Builder.Default private String officeConnectionType = "onPremise";
    private String officeDomain;
    @Builder.Default private String officeAuthMode = "ticket";
    private String officeTicketServiceToken;
    private String officeHttpStorageAdapterName;
    private String officeHttpStorageRequestJwtSecret;

    /** 오피스 사용 여부. 연결 방식이 onPremise 일 때만 on. */
    public boolean isOfficeEnabled() {
        return "onPremise".equals(officeConnectionType);
    }

    /** 오피스 접속 주소가 https 인지 */
    public boolean isOfficeHttps() {
        return officeDomain != null && officeDomain.regionMatches(true, 0, "https://", 0, 8);
    }
}

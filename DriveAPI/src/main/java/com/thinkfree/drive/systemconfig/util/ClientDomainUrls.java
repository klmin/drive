package com.thinkfree.drive.systemconfig.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * 관리자가 입력한 클라이언트 도메인 문자열("https://drive.example.com" 또는 "drive.example.com")에서
 * 스킴/경로를 떼고 호스트만 추출하는 유틸. tb_tenant.domain 에는 스킴 없는 호스트만 저장한다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ClientDomainUrls {

    /** 스킴/경로/포트 제거 후 호스트만 반환(대소문자 보존). blank 면 "". 대문자 입력은 호출부 DomainValidator 에서 거부됨 */
    public static String host(String clientDomain) {
        if (!StringUtils.hasText(clientDomain)) {
            return "";
        }
        String v = clientDomain.trim();
        int schemeIdx = v.indexOf("://");
        if (schemeIdx >= 0) {
            v = v.substring(schemeIdx + 3);
        }
        int slash = v.indexOf('/');
        if (slash >= 0) {
            v = v.substring(0, slash);
        }
        
        int colon = v.indexOf(':');
        if (colon >= 0) {
            v = v.substring(0, colon);
        }
        return v;
    }
}

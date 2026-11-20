package com.thinkfree.drive.util;

/**
 * 로그에 사용자 입력(요청 경로·헤더·클라이언트 IP·본문 등)을 남길 때 줄바꿈을 지운다 — 로그 위조(log injection, CWE-117) 방지.
 * 값에 CR·LF 가 들어 있으면 다음 줄이 별개의 로그 항목처럼 보여 로그를 읽는 사람·수집기(Loki 등)를 속일 수 있다.
 * 디코딩된 경로(getServletPath·pathWithinApplication)는 {@code %0d%0a} 가 실제 CR·LF 로 풀리므로 특히 주의.
 * <p>
 * 로그 인자에만 쓴다(값 자체를 바꾸지 않는다). 탭 등 다른 제어 문자는 줄을 나누지 않아 그대로 둔다.
 */
public final class LogSafe {

    private LogSafe() {
    }

    /** CR·LF 를 '_' 로 바꾼 문자열. null 은 "null"(로그 포맷과 같게). */
    public static String of(Object value) {
        if (value == null) {
            return "null";
        }
        return String.valueOf(value).replace('\n', '_').replace('\r', '_');
    }
}

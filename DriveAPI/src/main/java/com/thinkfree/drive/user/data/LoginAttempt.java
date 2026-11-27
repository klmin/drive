package com.thinkfree.drive.user.data;

/**
 * 비밀번호 비교 전에 로그인 시도를 원자적으로 센 결과.
 *
 * @param outcome      COUNTED = 이번 시도를 셌다({@code failureCount} = 센 뒤 횟수),
 *                     LOCKED = 잠겨 있어 세지 않았다(비밀번호를 비교하지 않는다),
 *                     NOT_FOUND = 이 트랜잭션에서 행이 보이지 않는다(방금 비활성·삭제됐거나, 호출부 트랜잭션이 아직 커밋하지 않은 계정)
 * @param failureCount COUNTED 일 때만 의미가 있다
 */
public record LoginAttempt(Outcome outcome, int failureCount) {

    public enum Outcome { COUNTED, LOCKED, NOT_FOUND }

    public static LoginAttempt counted(int failureCount) {
        return new LoginAttempt(Outcome.COUNTED, failureCount);
    }

    public static LoginAttempt locked() {
        return new LoginAttempt(Outcome.LOCKED, 0);
    }

    public static LoginAttempt notFound() {
        return new LoginAttempt(Outcome.NOT_FOUND, 0);
    }
}

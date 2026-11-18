package com.thinkfree.drive.oauth2.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * OAuth2 인가 서버(AS) 프로토콜 상수 — 여러 클래스가 공유하는 경로·클레임명의 단일 출처.
 *
 * 클레임명은 발급(토큰 커스터마이저)과 검증(RS 필터 — 현재는 MCP)이 공유하는 계약이다.
 * 특정 클래스 하나만 쓰는 튜닝값(키 회전 유예, reload 쿨다운 등)은 소유 클래스의
 * private 상수로 둔다.
 *
 * 기존 Drive JWT(HS256)의 클레임명은 JwtProvider 소유 — 신뢰 도메인이 달라
 * 값이 같아도(user_seq/tid) 의도적으로 합치지 않는다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OAuth2Constants {

    /** RFC 7591 DCR 경로 — 컨트롤러 매핑 + AS 메타데이터 광고가 공유 */
    public static final String REGISTRATION_PATH = "/connect/register";

    /**
     * C안 — 미인증 authorize 진입 시 보낼 프론트 로그인 라우트 (DriveFront pages/oauth2-login).
     * 상대경로라 요청 Host 기준으로 조립됨 — 테넌트 도메인이 유지된다 (프론트·WAS가
     * 리버스 프록시로 같은 오리진인 배포 전제, MCP_OAUTH_BRIDGE_FLOW.md §5).
     * WAS의 /oauth2/*와 접두어가 충돌하지 않도록 잡은 경로 — 프론트 ROUTES.OAUTH2_LOGIN과 계약.
     */
    public static final String FRONT_LOGIN_PAGE = "/oauth2-login/";

    /** C안 브리지 — 기존 Drive JWT를 AS 세션으로 교환하는 엔드포인트 (MCP_OAUTH_BRIDGE_FLOW.md) */
    public static final String SESSION_BRIDGE_PATH = "/oauth2/session";

    /**
     * access 토큰 클레임: 동작 역할({@code RoleType#name()}) — 브리지가 정한 principal 역할을 토큰 커스터마이저가 넣고,
     * RS Bearer 필터가 읽어 보유를 확인한다. 사용자 식별은 표준 subject(Drive userSeq).
     */
    public static final String CLAIMS_ROLE = "role";

    /** access 토큰 클레임: 승인 scope — SAS가 자동으로 넣고 RS Bearer 필터가 권한으로 매핑한다 */
    public static final String CLAIMS_SCOPE = "scope";

    /**
     * 커스텀 동의 화면 프론트 라우트 (DriveFront pages/oauth2-consent) — SAS가 동의 필요
     * 판정 시 scope·client_id·state 쿼리를 붙여 여기로 302한다. FRONT_LOGIN_PAGE와 같은
     * 이유로 상대경로(테넌트 도메인 유지)이며 WAS의 /oauth2/*와 접두어가 충돌하지 않는다.
     */
    public static final String FRONT_CONSENT_PAGE = "/oauth2-consent/";

    /** 동의 화면 데이터 조회(클라이언트 표시명) — 프론트 동의 페이지가 사용 */
    public static final String CONSENT_INFO_PATH = "/oauth2/consent-info";

}

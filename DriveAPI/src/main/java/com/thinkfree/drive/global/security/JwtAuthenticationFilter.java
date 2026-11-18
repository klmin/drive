package com.thinkfree.drive.global.security;

import com.thinkfree.drive.auth.cookie.AuthCookieManager;
import com.thinkfree.drive.auth.model.LoginTokenClaims;
import com.thinkfree.drive.auth.token.blacklist.query.BlacklistFindQuery;
import com.thinkfree.drive.auth.token.blacklist.service.BlacklistTokenFindService;
import com.thinkfree.drive.auth.utils.JwtProvider;
import com.thinkfree.drive.auth.utils.TokenHasher;
import com.thinkfree.drive.user.enums.RoleType;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final BlacklistTokenFindService blacklistTokenFindService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SecurityPaths.AUTH_WHITELIST_MATCHER.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // 화면 역할(X-Drive-Role) 이름의 쿠키만 읽는다 — Authorization 헤더는 보지 않는다(API Key 용)
        // 재발급 경로는 refresh 쿠키, 그 외는 access 쿠키
        boolean reissue = SecurityPaths.TOKEN_REISSUE_MATCHER.matches(request);
        Optional<RoleType> cookieRoleOpt = AuthCookieManager.resolveCookieRole(request);
        Optional<String> tokenOpt = cookieRoleOpt.flatMap(cookieRole -> reissue
                ? AuthCookieManager.resolveRefreshToken(request, cookieRole)
                : AuthCookieManager.resolveAccessToken(request, cookieRole));

        // 역할 헤더 또는 그 역할 쿠키가 없으면 쿠키 인증 대상 아님(API Key 요청·미로그인) — 판단은 뒤 필터·entry point
        if (tokenOpt.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            RoleType cookieRole = cookieRoleOpt.get();
            String token = tokenOpt.get();

            // 서명·만료·형식 검증 + 클레임 파싱을 한 번에 — 로그인 토큰이 아니면(user_seq 등 없음·모르는 type) 같은 실패
            LoginTokenClaims claims;
            try {
                claims = jwtProvider.parseLoginTokenClaims(token);
            } catch (JwtException | IllegalArgumentException e) {
                log.info("{ JwtAuthenticationFilter } : 유효하지 않은 토큰");
                throw new AuthenticationException("Invalid Token"){};
            }

            // 재발급 경로는 refresh, 그 외는 access 만 — 그 외 타입(MFA 챌린지 등)은 인증 안 함
            boolean allowedType = reissue ? claims.isRefresh() : claims.isAccess();
            if (!allowedType) {
                log.info("JwtAuthenticationFilter - Invalid Token Type.");
                throw new AuthenticationException("Invalid Token Type"){};
            }

            // 동작 역할 = 토큰 role 클레임. 클레임 없는 토큰(다중 role 이전 발급)은 DB(블랙리스트·계정)를 보지 않고 401
            RoleType role = claims.role();
            if (role == null) {
                throw new BadCredentialsException("Role claim missing");
            }
            if (!matchesCookieRole(cookieRole, role)) {
                throw new BadCredentialsException("Cookie role mismatch");
            }

            var tokenHash = TokenHasher.hash(token);
            var blacklistFindQuery = new BlacklistFindQuery(tokenHash);
            var isBlacklistToken = blacklistTokenFindService.existsByTokenHash(blacklistFindQuery);

            if(isBlacklistToken){
                log.info("[Blacklist token] userSeq : {}", claims.userSeq());
                throw new BadCredentialsException("Blacklisted token");
            }

            // 계정이 없거나 비활성·삭제, 또는 역할이 회수됐으면 UsernameNotFoundException → 401 (활성·보유·로드 1회 조회, 회수는 다음 요청부터 반영)
            UserDetails userDetails = customUserDetailsService.loadUserByUserSeq(claims.userSeq(), role);

            UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
            usernamePasswordAuthenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);

        } catch (AuthenticationException ex) {
            log.error("{ JwtAuthenticationFilter } : 인증 오류 - {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /** 쿠키 이름의 역할과 토큰 role 클레임이 같아야 한다 — 관리자 쿠키는 슈퍼관리자의 테넌트 관리자 진입(SUPER_ADMIN 토큰)도 허용 */
    private static boolean matchesCookieRole(RoleType cookieRole, RoleType tokenRole) {
        return cookieRole == tokenRole || (cookieRole == RoleType.ADMIN && tokenRole == RoleType.SUPER_ADMIN);
    }
}

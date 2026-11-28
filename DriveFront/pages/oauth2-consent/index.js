import Image from 'next/image';
import Box from '@mui/material/Box';
import { CircularProgress, Container, Typography } from '@mui/material';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import StyledButton from '@/components/common/button/StyledButton';
import typography from '@/theme/typography';
import palette from '@/theme/palette';
import axios from 'axios';
import { getDomain } from '@/utils/api';
import { useRouter } from '@/hooks/useRouter';
import Head from 'next/head';
import useBrandingConfig from '@/hooks/useBrandingConfig';
import { useTranslations } from 'next-intl';
import { ROUTES } from '@/constants/routes';

/**
 * MCP OAuth 동의(consent) 화면 (MCP_OAUTH_FLOW.md — 커스텀 consentPage).
 *
 * 진입 경로: 로그인(브리지) 완료 후 백엔드 /oauth2/authorize가 동의 필요 판정
 * → 이 라우트로 302 (쿼리: client_id, scope(공백 구분), state).
 *
 * 이 페이지가 하는 일:
 * 1. GET /oauth2/consent-info?client_id=… 로 클라이언트 표시명과 redirect 목적지 조회
 *    (익명 DCR 이라 표시명은 사칭 가능 — 코드가 갈 목적지를 함께 보여준다)
 * 2. 허용 → 요청 scope 전부, 거부 → scope 없이 POST /oauth2/authorize
 *    (form POST여야 백엔드 302 → MCP 콜백을 브라우저가 따라간다 — XHR 금지.
 *    세션 쿠키 동반을 위해 같은 오리진 전제 — oauth2-login과 동일)
 *
 * scope는 화면에 표시하지 않고 사용자가 고르지도 않는다 — 허용은 전부 아니면
 * 거부(all-or-nothing)이며, 쿼리로 넘어온 요청 scope를 그대로 반사해 제출한다.
 * 동의 POST에 scope가 하나도 없으면 백엔드(SAS)가 거부(access_denied)로 해석한다.
 *
 * 레이아웃 제외는 _app.js의 isLoginPage 라우트 목록(ROUTES.OAUTH2_CONSENT)으로 처리된다.
 */
export default function OAuth2Consent() {
    const router = useRouter();
    const { tabTitle, serviceName } = useBrandingConfig();
    const t = useTranslations();

    const [clientName, setClientName] = useState('');
    // 인가 코드가 전달될 redirect 목적지 — 표시명은 등록자가 아무 값으로 정할 수 있어 목적지를 함께 보여준다
    const [redirectHosts, setRedirectHosts] = useState([]);
    const [bridging, setBridging] = useState(true);
    const [submitting, setSubmitting] = useState(false);

    const { client_id: clientId, scope, state } = router.query;

    const requestedScopes = useMemo(
        () =>
            String(scope || '')
                .split(' ')
                .filter(Boolean),
        [scope]
    );

    useEffect(() => {
        if (!router.isReady) return;

        // authorize를 거치지 않은 직접 진입 — 진행 중인 동의 요청이 없으므로 에러 페이지로
        if (!clientId || !state || !scope) {
            router.replace(ROUTES.ERROR_PAGE);
            return;
        }

        (async () => {
            try {
                const res = await axios.get(`${getDomain()}/oauth2/consent-info`, {
                    params: { client_id: clientId },
                });
                setClientName(res.data.clientName || String(clientId));
                setRedirectHosts(
                    Array.isArray(res.data.redirectHosts) ? res.data.redirectHosts : []
                );
            } catch {
                // 이름 조회 실패는 치명적이지 않음 — client_id로 대체 표기
                setClientName(String(clientId));
            } finally {
                setBridging(false);
            }
        })();
    }, [router.isReady]);

    /**
     * 동의 결과 제출 — 브라우저 form POST로 /oauth2/authorize에 제출해야
     * 백엔드가 인가 코드를 발급하며 응답하는 302(→ MCP 콜백)를 따라간다.
     * approvedScopes가 비어 있으면 백엔드(SAS)가 거부(access_denied)로 처리한다.
     */
    const submitConsent = useCallback(
        approvedScopes => {
            // state는 백엔드가 진행 중 인가 건을 찾는 1회용 키 — 없이 제출하면 400
            if (!clientId || !state) {
                router.replace(ROUTES.ERROR_PAGE);
                return;
            }
            setSubmitting(true);
            const form = document.createElement('form');
            form.method = 'POST';
            form.action = `${getDomain()}/oauth2/authorize`;

            const addField = (name, value) => {
                const input = document.createElement('input');
                input.type = 'hidden';
                input.name = name;
                input.value = value;
                form.appendChild(input);
            };
            addField('client_id', clientId);
            addField('state', state);
            approvedScopes.forEach(s => addField('scope', s));

            document.body.appendChild(form);
            form.submit();
        },
        [clientId, state, router]
    );

    // 허용 = 요청된 scope 전부 승인 (all-or-nothing), 거부 = 빈 scope 제출
    const approve = () => submitConsent(requestedScopes);
    const deny = () => submitConsent([]);

    return (
        <>
            <Head>
                <title>{t('meta.common', { tabTitle })}</title>
                <meta property="title" content={serviceName} />
            </Head>
            <Container
                maxWidth
                disableGutters
                sx={{
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'center',
                    alignItems: 'center',
                    minHeight: '100vh',
                    backgroundColor: palette.bg_gray,
                    gap: '32px',
                    pb: '80px',
                    '@media (max-width: 360px)': { pb: 0, px: '16px' },
                }}
            >
                {bridging ? (
                    <CircularProgress size={32} />
                ) : (
                    <Box
                        sx={{
                            width: 420,
                            maxWidth: '100%',
                            pt: '48px',
                            pb: '40px',
                            px: '32px',
                            backgroundColor: palette.white,
                            borderRadius: '16px',
                            outline: `1px solid ${palette.divider_12}`,
                            outlineOffset: '-1px',
                            display: 'flex',
                            flexDirection: 'column',
                            alignItems: 'flex-start',
                        }}
                    >
                        <Box
                            sx={{
                                alignSelf: 'stretch',
                                display: 'flex',
                                flexDirection: 'column',
                                gap: '32px',
                            }}
                        >
                            {/* 헤더 */}
                            <Box
                                sx={{
                                    alignSelf: 'stretch',
                                    display: 'flex',
                                    flexDirection: 'column',
                                    alignItems: 'center',
                                    gap: '16px',
                                }}
                            >
                                {/* 클라이언트 로고 — 클라이언트별 로고 URL 연동 전까지 임시 고정 이미지 */}
                                <Image
                                    src="/oauth2-consent/icon-ic-client-logo-default.svg"
                                    alt="client logo"
                                    width={48}
                                    height={48}
                                    style={{ borderRadius: '9.6px' }}
                                />
                                <Box
                                    sx={{
                                        alignSelf: 'stretch',
                                        display: 'flex',
                                        flexDirection: 'column',
                                        alignItems: 'center',
                                        gap: '4px',
                                    }}
                                >
                                    <Typography
                                        sx={{
                                            ...typography.body3,
                                            alignSelf: 'stretch',
                                            color: palette.text_100,
                                            fontFamily: typography.fontFamily,
                                            textAlign: 'center',
                                        }}
                                    >
                                        {t.rich('user.mcpOauth.title', {
                                            b: chunks => (
                                                <Box
                                                    component="span"
                                                    sx={{
                                                        ...typography.h5,
                                                        fontFamily: typography.fontFamily,
                                                    }}
                                                >
                                                    {chunks}
                                                </Box>
                                            ),
                                            appName: clientName,
                                        })}
                                    </Typography>
                                    <Typography
                                        sx={{
                                            ...typography.body3,
                                            color: palette.text_100,
                                            fontFamily: typography.fontFamily,
                                            textAlign: 'center',
                                        }}
                                    >
                                        {t('user.mcpOauth.desc', { serviceName })}
                                    </Typography>
                                </Box>
                            </Box>

                            {/* redirect 목적지 — 표시명 사칭 대비. 사용자가 이름과 목적지를 비교해 판단한다 */}
                            {redirectHosts.length > 0 && (
                                <Box
                                    sx={{
                                        alignSelf: 'stretch',
                                        display: 'flex',
                                        flexDirection: 'column',
                                        gap: '8px',
                                        p: '12px 16px',
                                        borderRadius: '8px',
                                        backgroundColor: palette.bg_gray,
                                    }}
                                >
                                    <Typography
                                        sx={{
                                            ...typography.body5,
                                            fontFamily: typography.fontFamily,
                                            color: palette.text_100,
                                        }}
                                    >
                                        {t('user.mcpOauth.redirectLabel')}
                                    </Typography>
                                    {redirectHosts.map(host => (
                                        <Typography
                                            key={host}
                                            data-testid="oauth2-consent-redirect-host"
                                            sx={{
                                                ...typography.h5,
                                                fontFamily: typography.fontFamily,
                                                color: palette.text_100,
                                                wordBreak: 'break-all',
                                            }}
                                        >
                                            {host}
                                        </Typography>
                                    ))}
                                    <Typography
                                        sx={{
                                            ...typography.body5,
                                            fontFamily: typography.fontFamily,
                                            color: palette.text_100,
                                            opacity: 0.7,
                                        }}
                                    >
                                        {t('user.mcpOauth.redirectNotice')}
                                    </Typography>
                                </Box>
                            )}

                            {/* 버튼 — 허용(위, Solid) / 거부(아래, Outlined) */}
                            <Box
                                sx={{
                                    alignSelf: 'stretch',
                                    display: 'flex',
                                    flexDirection: 'column',
                                    gap: '8px',
                                }}
                            >
                                <StyledButton
                                    disabled={submitting}
                                    sx={{
                                        width: '100%',
                                        height: 40,
                                        borderRadius: '4px',
                                        backgroundColor: palette.btn_point_bg,
                                        '&:hover': { backgroundColor: '#1e2640' },
                                        '&.Mui-disabled': { opacity: 0.4 },
                                    }}
                                    onClick={approve}
                                >
                                    <Typography
                                        sx={{
                                            ...typography.body5,
                                            fontFamily: typography.fontFamily,
                                            color: palette.white,
                                        }}
                                    >
                                        {t('user.mcpOauth.allow')}
                                    </Typography>
                                </StyledButton>
                                <StyledButton
                                    disabled={submitting}
                                    sx={{
                                        width: '100%',
                                        minWidth: 80,
                                        height: 40,
                                        backgroundColor: palette.white,
                                        borderRadius: '4px',
                                        outline: `1px solid ${palette.btn_border}`,
                                        outlineOffset: '-1px',
                                    }}
                                    onClick={deny}
                                >
                                    <Typography
                                        sx={{
                                            ...typography.body5,
                                            fontFamily: typography.fontFamily,
                                            color: palette.text_100,
                                        }}
                                    >
                                        {t('user.mcpOauth.deny')}
                                    </Typography>
                                </StyledButton>
                            </Box>
                        </Box>
                    </Box>
                )}
            </Container>
        </>
    );
}

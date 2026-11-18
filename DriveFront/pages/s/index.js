import { useEffect } from 'react';
import { CircularProgress } from '@mui/material';
import api from '@/utils/api';
import palette from '@/theme/palette';
import { useRouter } from '@/hooks/useRouter';
import { isLoggedIn, saveAuth, clearAuth, saveShareLinkToast } from '@/utils/authTokens';
import { ROUTES } from '@/constants/routes';
import { getResourceDetail } from '@/apis/resourceApi';
import { getItemExt, isOfficePreviewable, isIntellectFile } from '@/constants/FILES';
import { getOfficeEditUrl, getOfficeViewUrl } from '@/apis/webOfficeApi';
import { getIntellectUrl } from '@/apis/intellectApi';
import { getTenantPrefix } from '@/utils/tenantPrefix';

export default function SToken() {
    const router = useRouter();
    // 로그인 후 복귀 경로 — 접두 없는 앱 내부 경로(다른 테넌트 로그인 페이지가 자기 접두를 붙임)
    const redirect = router.asPath;
    const token = router.query.t;
    const isORG_LINK = router.query.sht === 'ORG_LINK';

    // 공유링크 첫 진입 페이지는 UserContext.Provider 없이 렌더링되므로(_app.js) useUser()로는
    // 로그인 유저 정보를 가져올 수 없어 직접 조회
    const getCurrentUser = async () => {
        if (!isLoggedIn('USER')) return null;

        const res = await api.Get('users', {}, null, { __roleType: 'USER' });
        return res?.status === 200 ? res?.data?.data : null;
    };

    // 오피스/인텔릭트 파일이면 문서 URL을, 아니면 null을 반환
    const getOfficeOrIntellectUrl = async (resourceSeq, ss, user) => {
        const data = await getResourceDetail(resourceSeq, { ss });
        if (!data) return null;

        const ext = getItemExt(data);
        if (!isOfficePreviewable(ext) && !isIntellectFile(ext)) return null;

        if (isIntellectFile(ext)) {
            return await getIntellectUrl(data, user, data.canEdit ? undefined : 'ro');
        }
        return data?.canEdit
            ? await getOfficeEditUrl(data, user)
            : await getOfficeViewUrl(data, user);
    };

    const loginAnonymous = async () => {
        const res = await api.Post('auth/anonymous/login', { linkToken: token });
        if (res?.code === 200) {
            const data = res?.data;
            saveAuth('ANONYMOUS', data);
            return true;
        }
        return false;
    };

    const getShareLink = async () => {
        try {
            // 이 테넌트에 사용자 로그인이 있으면 그 쿠키로 보는 사람을 식별한다
            const res = await api.Get(`shares/link/${token}`, {}, null, { __roleType: 'USER' });

            if (res?.status === 200) {
                const shareLinkResponse = res?.data?.data;
                if (!shareLinkResponse) return;

                const isPublicLink = shareLinkResponse?.isPublicLink;
                const isUserLoggedIn = isLoggedIn('USER');

                // ORG_LINK + 비로그인: 로그인 유도
                // - 현재 도메인 = 리소스 오너의 테넌트, redirectDomain = 뷰어의 테넌트
                // - 두 도메인이 다르면 외부 테넌트이므로 뷰어 도메인 로그인 페이지로 이동
                if (!isPublicLink && !isUserLoggedIn) {
                    saveShareLinkToast('NEED_LOGIN');
                    const redirectDomain = shareLinkResponse?.redirectUrl;
                    // PATH 테넌트 모드에서 백엔드 redirectUrl 은 `https://base/{slug}` 라 origin 만으로는 같은 테넌트인지 판별되지 않는다
                    const currentTenantOrigin = window.location.origin + getTenantPrefix();
                    if (redirectDomain && currentTenantOrigin !== redirectDomain) {
                        window.location.href = `${redirectDomain}/login?redirect=${encodeURIComponent(redirect)}`;
                    } else {
                        await router.replace(
                            `${ROUTES.LOGIN}?redirect=${encodeURIComponent(redirect)}`
                        );
                    }
                    return;
                }

                // PUBLIC_LINK + 비로그인: 익명 JWT 발급 후 계속 진행
                if (isPublicLink && !isUserLoggedIn) {
                    const ok = await loginAnonymous();
                    if (!ok) {
                        await router.push(ROUTES.ERROR_PAGE_SHARE);
                        return;
                    }
                }

                const currentUser = isUserLoggedIn ? await getCurrentUser() : null;

                const ss = shareLinkResponse?.userSeq;
                const isFolder = shareLinkResponse?.resourceType === 'FOLDER';
                // 리소스 오너와 현재 로그인 유저가 같으면 내 드라이브, 아니면 공유 문서함
                const isDriveResource = shareLinkResponse?.userSeq === currentUser?.userSeq;
                // redirectDomain: 뷰어의 테넌트 도메인 (리소스가 실제로 서비스되는 도메인)
                const redirectDomain = shareLinkResponse?.redirectUrl;
                const resourceSeq = shareLinkResponse?.resourceSeq;

                if (isFolder) {
                    if (!isUserLoggedIn && isPublicLink) {
                        // PUBLIC_LINK 비로그인 → 익명 폴더 탐색 페이지
                        window.location.href = `${redirectDomain}/s/folder?pfs=${resourceSeq}&ss=${ss}`;
                    } else {
                        // 로그인 유저 → 드라이브 또는 공유 문서함 폴더로 이동
                        const query = !isORG_LINK
                            ? `pfs=${resourceSeq}&ss=${ss}`
                            : `pfs=${resourceSeq}&ss=${ss}&sht=ORG_LINK`;
                        const root = isDriveResource ? 'drive' : 'shared';
                        window.location.href = `${redirectDomain}/${root}/?${query}`;
                    }
                    return;
                } else {
                    if (!isUserLoggedIn && isPublicLink) {
                        // PUBLIC_LINK 비로그인 → 익명 미리보기 페이지
                        window.location.href = `${redirectDomain}/s/preview?rs=${resourceSeq}&ss=${ss}`;
                    } else {
                        // 로그인 유저 → 오피스/인텔릭트 파일이면 뷰어로 바로 이동, 그 외에는 home에서 미리보기 모달로 열림.
                        const officeOrIntellectUrl = await getOfficeOrIntellectUrl(
                            resourceSeq,
                            ss,
                            currentUser
                        );
                        console.log('officeOrIntellectUrl', officeOrIntellectUrl);
                        window.location.href = officeOrIntellectUrl
                            ? officeOrIntellectUrl
                            : `${redirectDomain}/home?rs=${resourceSeq}&ss=${ss}`;
                    }
                    return;
                }
            } else if (res?.status === 401) {
                // 토큰 만료 또는 미인증
                saveShareLinkToast('NEED_LOGIN');
                await router.replace(`${ROUTES.LOGIN}?redirect=${encodeURIComponent(redirect)}`);
            } else {
                // 링크 만료, 권한 없음 등 그 외 오류
                saveShareLinkToast('ACCESS_FAIL');
                await router.push(ROUTES.ERROR_PAGE_SHARE);
            }
        } catch (err) {
            console.log(err);
        }
    };

    useEffect(() => {
        if (!router.isReady) return;

        if (!token) return;

        clearAuth('ANONYMOUS');
        getShareLink();
    }, [router.isReady, token]);

    return (
        <div
            style={{
                display: 'flex',
                justifyContent: 'center',
                alignItems: 'center',
                height: '100vh',
            }}
        >
            <CircularProgress sx={{ color: palette.text_80 }} />
        </div>
    );
}

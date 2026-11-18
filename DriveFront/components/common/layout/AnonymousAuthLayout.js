import React, { useCallback, useEffect, useLayoutEffect, useRef, useState, useMemo } from 'react';
import {
    AppBar,
    Box,
    CssBaseline,
    Drawer,
    IconButton,
    Stack,
    Toolbar,
    Typography,
} from '@mui/material';
import { styled } from '@mui/material/styles';
import Image from 'next/image';
import StyledButton from '@/components/common/button/StyledButton';
import { useRouter } from '@/hooks/useRouter';
import { useTranslations } from 'next-intl';
import palette from '@/theme/palette';
import api from '@/utils/api';
import { useUser } from '@/context/UserContext';
import useNavigate from '@/hooks/useNavigate';
import ProfileImage from '@/components/common/input/ProfileImage';
import { clearAuth } from '@/utils/authTokens';
import { BaseTooltip } from '../tooltip/BaseTooltip';
import useLoginConfig from '@/hooks/useLoginConfig';
import { ROUTES } from '@/constants/routes';
import LicenseWatermark from '@/components/common/LicenseWatermark';

// const Main = styled('main')(({ theme }) => ({
//     flexGrow: 1,
//     backgroundColor: '#FAFAFA',
//     minHeight: '100vh',
// }));
const Main = styled('main')(({ theme }) => ({
    flexGrow: 1,
    backgroundColor: '#FAFAFA',
    minHeight: 0,
}));

const MyAppBar = styled(AppBar)(({ theme }) => ({
    zIndex: theme.zIndex.drawer + 1,
    backgroundColor: '#fff',
    color: '#000',
    boxShadow: 'none',
    borderBottom: '1px solid #e0e0e0',
}));

const AnonymousAuthLayout = ({ children, loading }) => {
    const t = useTranslations();
    const router = useRouter();
    const user = useUser();
    const navigate = useNavigate();

    const { isJwt: isJwtLogin, errorRedirectUrl } = useLoginConfig();

    // 메뉴 스크롤 위치 저장을 위한 상태
    const [menusScrollPosition, setMenusScrollPosition] = useState(0);
    const menusScrollRef = useRef(null);

    const profileImageSrc = useMemo(() => {
        if (!user?.userSeq) return '';
        return api.getProfileImage(user.userSeq);
    }, [user?.userSeq]);

    const handleLogin = async () => {
        if (isJwtLogin) {
            clearAuth('ANONYMOUS');
            navigate(errorRedirectUrl || ROUTES.LOGIN);
        } else {
            clearAuth('ANONYMOUS');
            navigate(ROUTES.LOGIN);
        }
    };

    useLayoutEffect(() => {
        const savedScrollPosition = sessionStorage.getItem('menusScrollPosition');
        if (savedScrollPosition && menusScrollRef.current) {
            menusScrollRef.current.scrollTop = parseInt(savedScrollPosition);
        }
    }, [router.pathname]);

    useEffect(() => {
        const savedScrollPosition = sessionStorage.getItem('menusScrollPosition');
        if (savedScrollPosition && menusScrollRef.current) {
            const timer = setTimeout(() => {
                if (menusScrollRef.current) {
                    menusScrollRef.current.scrollTop = parseInt(savedScrollPosition);
                }
            }, 10);
            return () => clearTimeout(timer);
        }
    }, [router.pathname]);

    useEffect(() => {
        sessionStorage.setItem('menusScrollPosition', menusScrollPosition.toString());
    }, [menusScrollPosition]);

    return (
        <Box sx={{ display: 'flex' }}>
            <Box sx={{ width: '100%' }}>
                {/** 상단 헤더 */}
                <MyAppBar
                    position="fixed"
                    sx={{
                        left: 0,
                        width: '100%',
                        zIndex: theme => theme.zIndex.drawer + 1,
                        borderBottom: { xs: 'none', md: `1px solid ${palette.divider_8}` },
                    }}
                >
                    <Toolbar
                        sx={{
                            justifyContent: 'space-between',
                            alignItems: 'center',
                            display: 'flex',
                            paddingLeft: `48px !important`,
                            paddingRight: `48px !important`,
                            maxWidth: '100%',
                            height: { xs: 56, md: 64 },
                            minHeight: 0,
                            gap: 4,
                        }}
                    >
                        <Box sx={{ width: { md: 200 } }}>
                            <Image
                                src={api.getBasicLogoImage()}
                                alt={'logo'}
                                width={75}
                                height={20}
                            />
                        </Box>

                        <Box sx={{ display: 'flex', alignItems: 'center' }}>
                            <Stack
                                direction="row"
                                alignItems="center"
                                justifyContent="center"
                                onClick={() => handleLogin()}
                                gap={2}
                                sx={{
                                    cursor: 'pointer',
                                    boxShadow: '0 0 4px 0 #3a3d492e',
                                    padding: {
                                        xs: 1,
                                        md: '8px 14px 8px 14px',
                                    },
                                    borderRadius: '21px',
                                }}
                            >
                                <Typography
                                    sx={{ display: { xs: 'none', md: 'flex' } }}
                                    variant={'body8'}
                                    color={'text_100'}
                                >
                                    {t('common.action.login')}
                                </Typography>
                            </Stack>
                        </Box>
                    </Toolbar>
                </MyAppBar>

                <Main
                    sx={{
                        p: 0,
                        background: '#fff',
                        minHeight: 0,
                        flexGrow: 1,
                        pt: { xs: '56px', md: '64px' },
                        maxWidth: '100%',
                        display: 'flex',
                        flexDirection: 'column',
                    }}
                >
                    {loading ? null : children}
                </Main>
            </Box>
            <LicenseWatermark />
        </Box>
    );
};

export default AnonymousAuthLayout;

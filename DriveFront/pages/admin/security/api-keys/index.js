/** API Key 관리 페이지 — 헤더(제목 + 생성) + 목록. */
import React, { useState } from 'react';
import Head from 'next/head';
import { Box, Stack, Typography } from '@mui/material';
import { useTranslations } from 'next-intl';
import palette from '@/theme/palette';
import useBrandingConfig from '@/hooks/useBrandingConfig';
import { useVvh } from '@/hooks/useVvh';
import { useUser } from '@/context/UserContext';
import { useDialog } from '@/provider/DialogProvider';
import ApiKeyCreateButton from '@/components/common/button/ApiKeyCreateButton';
import ApiKeys from '@/components/views/admin/security/api-keys/ApiKeys';
import useClientConfig from '@/hooks/useClientConfig';

export default function AdminApiKeys() {
    const t = useTranslations();
    const { tabTitle, serviceName } = useBrandingConfig();
    const user = useUser();
    const { openDialog } = useDialog();
    // 발급 다이얼로그는 목록 상태를 모르므로, 발급이 끝나면 이 값만 올려 목록에 재조회를 알린다
    const [reloadToken, setReloadToken] = useState(0);

    useVvh();

    // 슈퍼 관리자 계정으로 관리자 페이지에 접속한 경우 API Key 발급 불가
    const isSuperAdmin = (user?.roles ?? []).includes('SUPER_ADMIN');
    // SINGLE 은 API Key 발급이 막혀 있다(TENANT_011)
    const { isSingleUser } = useClientConfig();

    const handleAddApiKey = () =>
        openDialog('addAdminApiKey', { reload: () => setReloadToken(prev => prev + 1) });

    return (
        <>
            <Head>
                <title>{t('meta.admin', { tabTitle })}</title>
                <meta property="title" content={serviceName} />
            </Head>
            <Box
                sx={{
                    width: '100%',
                    // main이 앱바 높이만큼 밀려 있어 그만큼 빼야 목록 바닥이 뷰포트 안에 들어온다.
                    // 페이지는 스크롤하지 않고 목록 안에서만 스크롤한다(무한 스크롤).
                    height: 'calc(var(--vvh, 1vh) * 100 - var(--admin-appbar-h, 0px))',
                    display: 'flex',
                    flexDirection: 'column',
                    pt: 8,
                    px: {
                        md: 12,
                        xs: 4,
                    },
                    overflow: 'hidden',
                    backgroundColor: palette.white,
                }}
            >
                <Stack
                    direction="row"
                    alignItems="center"
                    justifyContent="space-between"
                    sx={{ flexShrink: 0, mb: 8 }}
                >
                    <Typography noWrap variant="h2" color="text_100">
                        {t('AdminSidebar.ApiKeys')}
                    </Typography>
                    <ApiKeyCreateButton
                        onClick={handleAddApiKey}
                        disabled={isSuperAdmin || isSingleUser}
                    />
                </Stack>

                <ApiKeys reloadToken={reloadToken} />
                {/* 사용자 목록과 같은 하단 여백 */}
                <Box sx={{ height: '64px', flexShrink: 0 }} />
            </Box>
        </>
    );
}

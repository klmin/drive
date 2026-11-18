/** 슈퍼관리자 API Key 관리 — 전체 테넌트 키 목록(테넌트 필터) + 테넌트 계정 대상 발급. */
import React, { useEffect, useLayoutEffect, useState } from 'react';
import { Box, MenuItem, Stack, Typography } from '@mui/material';
import { useTranslations } from 'next-intl';
import palette from '@/theme/palette';
import { fetchAllSuperAdminTenants } from '@/utils/superAdminTenants';
import { useRouter } from '@/hooks/useRouter';
import { useDialog } from '@/provider/DialogProvider';
import AdminFilterSelect from '@/components/common/input/AdminFilterSelect';
import ApiKeyCreateButton from '@/components/common/button/ApiKeyCreateButton';
import ApiKeys from '@/components/views/admin/security/api-keys/ApiKeys';
import { SUPER_ADMIN_API_KEY_COLUMNS } from '@/components/views/admin/security/api-keys/ApiKeyTable';

export default function SuperAdminApiKeys() {
    const t = useTranslations();
    const router = useRouter();
    const { openDialog } = useDialog();
    const [tenants, setTenants] = useState([]);
    // 발급 다이얼로그는 목록 상태를 모르므로, 발급이 끝나면 이 값만 올려 목록에 재조회를 알린다
    const [reloadToken, setReloadToken] = useState(0);

    useEffect(() => {
        const fetchTenants = async () => {
            try {
                // 테넌트가 한 페이지(1,000개)를 넘어도 필터에 모두 나오도록 마지막 페이지까지 읽는다
                setTenants(await fetchAllSuperAdminTenants());
            } catch (err) {
                console.error('Tenant list fetch failed:', err);
            }
        };
        fetchTenants();
    }, []);

    // 태블릿 키보드 표시/해제 등으로 visual viewport 높이가 변해도 목록 높이가 맞도록 --vvh 갱신
    useLayoutEffect(() => {
        const setVvh = () => {
            const h = window.visualViewport?.height ?? window.innerHeight;
            document.documentElement.style.setProperty('--vvh', `${h * 0.01}px`);
        };
        setVvh();
        window.visualViewport?.addEventListener('resize', setVvh);
        window.addEventListener('resize', setVvh);
        return () => {
            window.visualViewport?.removeEventListener('resize', setVvh);
            window.removeEventListener('resize', setVvh);
        };
    }, []);

    /** 테넌트 필터 — URL 쿼리(ts)를 바꾸면 목록이 첫 페이지부터 다시 읽는다 */
    const handleTenantChange = e => {
        const query = { ...router.query };
        delete query.ci;
        if (e.target.value) query.ts = e.target.value;
        else delete query.ts;
        router.push({ pathname: router.pathname, query });
    };

    const handleAddApiKey = () =>
        openDialog('addTenantApiKey', {
            defaultTenantSeq: router.query.ts ? Number(router.query.ts) : undefined,
            reload: () => setReloadToken(prev => prev + 1),
            onCreated: apiKey => openDialog('reissueApiKeyComplete', { apiKey, mode: 'create' }),
        });

    return (
        <Box
            sx={{
                width: '100%',
                height: '100%',
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
                    flexDirection: 'row',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                }}
            >
                <Typography variant="h2" sx={{ color: palette.text_100 }}>
                    {t('superAdmin.apiKey.title')}
                </Typography>
                <Stack direction="row" alignItems="center" gap="8px">
                    <AdminFilterSelect
                        value={router.query.ts ?? ''}
                        onChange={handleTenantChange}
                        slotProps={{ select: { displayEmpty: true } }}
                        aria-label={t('superAdmin.apiKey.filter.tenant')}
                    >
                        <MenuItem value="">
                            {t('superAdmin.apiKey.filter.tenant')}: {t('common.label.all')}
                        </MenuItem>
                        {tenants.map(tenant => (
                            <MenuItem key={tenant.tenantSeq} value={String(tenant.tenantSeq)}>
                                {tenant.tenantName}
                            </MenuItem>
                        ))}
                    </AdminFilterSelect>
                    <ApiKeyCreateButton onClick={handleAddApiKey} />
                </Stack>
            </Box>

            {/* 본문 — 전체 테넌트 키(슈퍼관리자 범위라 보호 키도 조작할 수 있다) */}
            <ApiKeys
                reloadToken={reloadToken}
                listUrl="super-admin/api-keys"
                basePath="super-admin/api-keys"
                columns={SUPER_ADMIN_API_KEY_COLUMNS}
            />
        </Box>
    );
}

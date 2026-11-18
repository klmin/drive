import { MenuItem, Stack, Typography } from '@mui/material';
import Image from 'next/image';
import Box from '@mui/material/Box';
import React, { useEffect, useLayoutEffect, useMemo, useState } from 'react';
import api from '@/utils/api';
import { useTranslations } from 'next-intl';
import { useUser } from '@/context/UserContext';
import palette from '@/theme/palette';
import StyledButton from '@/components/common/button/StyledButton';
import { useDialog } from '@/provider/DialogProvider';
import { parseApiKeysFiltersFromParams } from '@/utils/queryString';
import { useRouter } from '@/hooks/useRouter';
import AdminFilterSelect from '@/components/common/input/AdminFilterSelect';
import TenantTable from './TenantTable';

/** 목록 유형 필터 값 — 서버 쿼리 tt(생략 = 전체) */
const TENANT_TYPES = ['SELF_MANAGED', 'SYSTEM_MANAGED'];

export default function SuperAdminTenants() {
    const t = useTranslations();
    const router = useRouter();
    const user = useUser();
    const { openDialog } = useDialog();

    const [cursorId, setCursorId] = useState(null);
    const [hasNext, setHasNext] = useState(false);
    const [tenantList, setTenantList] = useState([]);

    const [loading, setLoading] = useState(true);

    const [cursorSearchCount, setCursorSearchCount] = useState(0);
    const [hasBaseDomain, setHasBaseDomain] = useState(false);

    useEffect(() => {
        if (!user) return;
    }, [user]);

    /** 베이스 도메인 미설정 시 어드민 바로가기 비활성화 */
    useEffect(() => {
        const fetchBaseDomain = async () => {
            try {
                const res = await api.Get('super-admin/base-domain', {});
                if (res?.status === 200) setHasBaseDomain(!!res?.data?.data?.baseDomain);
            } catch (err) {
                console.error('Base domain fetch failed:', err);
            }
        };
        fetchBaseDomain();
    }, []);

    // 목록을 다시 읽지 않고 바뀐 행만 갱신한다
    const listPatch = useMemo(
        () => ({
            /** 수정된 행의 변경 필드만 교체 */
            patch: (tenantSeq, changed) => {
                setTenantList(prevList =>
                    prevList.map(it => (it.tenantSeq === tenantSeq ? { ...it, ...changed } : it))
                );
            },
        }),
        []
    );

    const getListItems = async (isNewSearch = false) => {
        let params = parseApiKeysFiltersFromParams(router.query);
        if (!isNewSearch && cursorId) {
            params = {
                ...params,
                ci: cursorId,
                ps: 20,
            };
        }

        try {
            const res = await api.Get('super-admin/tenants', { params });

            if (res?.status === 200) {
                setHasNext(res?.data?.hasNext);
                setCursorId(res?.data?.nextCursor);

                if (isNewSearch) {
                    setTenantList(res?.data?.data);
                } else {
                    setTenantList(prevList => [...prevList, ...res?.data?.data]);
                }
            }
        } catch (err) {
            console.error('Api Key list fetch failed:', err);
        }
    };

    /** 유형 필터 — URL 쿼리(tt)를 바꾸면 아래 router.query 효과가 첫 페이지부터 다시 읽는다 */
    const handleTenantTypeChange = e => {
        const query = { ...router.query };
        delete query.ci;
        if (e.target.value) query.tt = e.target.value;
        else delete query.tt;
        router.push({ pathname: router.pathname, query });
    };

    const handleAddTenant = async () => {
        openDialog('addTenant', {
            reload: () => getListItems(true),
        });
    };

    useEffect(() => {
        setTimeout(() => setLoading(false), 250);
    }, []);

    useEffect(() => {
        if (!cursorId) return;
        if (cursorSearchCount <= 0) return; // 다음 목룍 요청인지

        getListItems(false);
    }, [cursorSearchCount]);

    useEffect(() => {
        if (!router.isReady) return;
        setCursorId(null);

        getListItems(true);
    }, [router.query]);

    // 태블릿 키보드 표시/해제 등으로 visual viewport 높이가 변할 때 레이아웃이 깨지지 않도록
    // --vvh CSS 변수를 실제 화면 높이에 맞춰 갱신
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
                    {t('superAdmin.tenantManagement.title')}
                </Typography>
                <Stack direction="row" alignItems="center" gap="8px">
                    <AdminFilterSelect
                        value={router.query.tt ?? ''}
                        onChange={handleTenantTypeChange}
                        slotProps={{ select: { displayEmpty: true } }}
                        aria-label={t('superAdmin.tenantManagement.filter.tenantType')}
                    >
                        <MenuItem value="">
                            {t('superAdmin.tenantManagement.filter.tenantType')}:{' '}
                            {t('common.label.all')}
                        </MenuItem>
                        {TENANT_TYPES.map(type => (
                            <MenuItem key={type} value={type}>
                                {t(`superAdmin.tenantManagement.tenantType.${type}`)}
                            </MenuItem>
                        ))}
                    </AdminFilterSelect>
                    <StyledButton
                        onClick={handleAddTenant}
                        sx={{
                            height: 28,
                            pl: '6px',
                            pr: '8px',
                            gap: '4px',
                            bgcolor: palette.btn_point_bg,
                            borderRadius: '4px',
                            outline: `1px solid ${palette.btn_border}`,
                            '&:hover': { bgcolor: '#1e2436' }, // palette 미등록 색상
                        }}
                    >
                        <Image
                            src="/common/icon-ic-plus-white.svg"
                            alt="plus"
                            width={20}
                            height={20}
                        />
                        <Typography variant="body9" sx={{ color: palette.white }}>
                            {t('superAdmin.tenantManagement.addTenant')}
                        </Typography>
                    </StyledButton>
                </Stack>
            </Box>

            {/** 본문 : Tenant List */}
            <Box sx={{ width: '100%', flex: 1, minHeight: 0 }}>
                {loading ? (
                    <Stack
                        sx={{ width: '100%', height: '100%' }}
                        alignItems="center"
                        justifyContent="center"
                    >
                        <Typography variant="body1" color="text_60">
                            Loading...
                        </Typography>
                    </Stack>
                ) : (
                    <TenantTable
                        data={tenantList}
                        reload={() => getListItems(true)}
                        listPatch={listPatch}
                        hasBaseDomain={hasBaseDomain}
                        hasNext={hasNext}
                        setCursorSearchCount={setCursorSearchCount}
                    />
                )}
            </Box>
        </Box>
    );
}

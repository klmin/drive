'use client';

import Image from 'next/image';
import Box from '@mui/material/Box';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Grid, IconButton, Stack, Typography } from '@mui/material';
import palette from '@/theme/palette';
import { useRouter } from '@/hooks/useRouter';
import FilterDropdown from '@/components/common/input/FilterSelect';
import { useTranslations } from 'next-intl';
import { useDialog } from '@/provider/DialogProvider';
import api from '@/utils/api';
import UserTable from '@/components/views/admin/users/UserTable';
import Head from 'next/head';
import { RoleFilter, StatusFilter } from '@/constants/FILTER';
import { buildClearQuery, parseUsersFiltersFromParams } from '@/utils/queryString';
import StyledButton from '@/components/common/button/StyledButton';
import { getRoleNameByValue, getStatusNameByValue } from '@/utils/filter';
import { BaseTooltip } from '@/components/common/tooltip/BaseTooltip';
import useBrandingConfig from '@/hooks/useBrandingConfig';
import { useVvh } from '@/hooks/useVvh';
import useClientConfig from '@/hooks/useClientConfig';

export default function Users() {
    const t = useTranslations();
    const router = useRouter();
    const { openDialog } = useDialog();
    const { tabTitle, serviceName } = useBrandingConfig();
    // SINGLE 은 계정 추가·일괄 등록이 막혀 있어(TENANT_011) 버튼을 비활성으로 둔다
    const { isSingleUser } = useClientConfig();

    const [cursorId, setCursorId] = useState(null);
    const [hasNext, setHasNext] = useState(false);

    const [checkItems, setCheckItems] = useState([]);
    const [loading, setLoading] = useState(true);
    const [userList, setUserList] = useState([]);

    // 테이블 바깥(여백/필터/제목 등) 클릭 시 선택 해제 — DriveTable과 동일 동작
    const tableAreaRef = useRef(null);
    const handleContainerMouseDown = e => {
        if (tableAreaRef.current?.contains(e.target)) return;
        setCheckItems(prev => (prev.length ? [] : prev));
    };

    const [statusFilterOpen, setStatusFilterOpen] = useState(false);
    const [status, setStatus] = useState(router.query.s || undefined);
    const [roleFilterOpen, setRoleFilterOpen] = useState(false);
    const [role, setRole] = useState(router.query.rt || undefined);

    const [cursorSearchCount, setCursorSearchCount] = useState(0);

    const getListItems = async (isNewSearch = false) => {
        let params = parseUsersFiltersFromParams(router.query);
        if (!isNewSearch && cursorId) {
            params = {
                ...params,
                ci: cursorId,
                ps: 10,
            };
        }

        try {
            const res = await api.Get('admins/users', { params });

            if (res?.status === 200) {
                setHasNext(res?.data?.hasNext);
                setCursorId(res?.data?.nextCursor);
                const newData = res?.data?.data;

                if (isNewSearch) {
                    setUserList(newData);
                } else {
                    setUserList(prevList => {
                        return [...prevList, ...newData];
                    });
                }
            }
        } catch (err) {
            console.error('User list fetch failed:', err);
        }
    };

    // 목록을 1페이지부터 다시 읽는 대신 바뀐 행만 갱신한다 — 스크롤 위치가 유지된다
    const listPatch = useMemo(
        () => ({
            /** 상태 변경된 행들의 status만 교체 */
            patchStatus: (userSeqs, status) => {
                const targets = new Set(userSeqs);
                setUserList(prevList =>
                    prevList.map(it => (targets.has(it.userSeq) ? { ...it, status } : it))
                );
            },
            /** 삭제된 행들을 목록에서 제거 */
            remove: userSeqs => {
                const targets = new Set(userSeqs);
                setUserList(prevList => prevList.filter(it => !targets.has(it.userSeq)));
            },
            /** 수정된 행 하나를 다시 읽어 교체 */
            refresh: async userSeq => {
                const res = await api.Get(`admins/users/${userSeq}`, {});
                if (res?.status !== 200) return;

                const data = res?.data?.data;
                setUserList(prevList =>
                    prevList.map(it => (it.userSeq === userSeq ? { ...it, ...data } : it))
                );
            },
        }),
        []
    );

    // 계정 추가는 하나 — 역할(사용자·관리자)은 다이얼로그에서 고른다
    const handleRegisterUserDialog = async () => {
        openDialog('registerUser');
    };

    const handleBulkUserImportDialog = async () => {
        openDialog('bulkUserImport', {
            reload: getListItems,
        });
    };

    const handleResetFilters = async () => {
        setStatus(undefined);
        setRole(undefined);
        const root = router.pathname;
        const queryParams = buildClearQuery(router.query);
        await router.push({ pathname: root, query: queryParams }, undefined, { shallow: true });

        setCursorId(null);
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

    useEffect(() => {
        if (!router.isReady) return;

        // 쿼리 변화 감지 시 필터 상태 갱신
        setStatus(router.query.s || undefined);
        setRole(router.query.rt || undefined);
    }, [router.query, router.isReady]);

    useVvh();

    return (
        <>
            <Head>
                <title>{t('meta.admin', { tabTitle })}</title>
                <meta property="title" content={serviceName} />
            </Head>
            <Box
                id={'container'}
                onMouseDown={handleContainerMouseDown}
                sx={{
                    width: '100%',
                    height: 'calc(var(--vvh, 1vh) * 100)',
                    display: 'flex',
                    flexDirection: 'row',
                    position: 'relative',
                }}
            >
                <Box
                    sx={{
                        width: { xs: '100%', md: '100%' },
                        display: 'flex',
                        flexDirection: 'column',
                        position: 'relative',
                        pt: {
                            xs: 7,
                            md: 8,
                        },
                        pb: 16,
                        px: {
                            md: 12,
                            xs: 4,
                        },
                    }}
                >
                    <Stack
                        direction={'row'}
                        gap={1}
                        sx={{ width: '100%' }}
                        justifyContent={'flex-start'}
                        alignItems={'center'}
                    >
                        <Typography variant="h2" component={'span'}>
                            {t('AdminSidebar.AccountList')}
                        </Typography>
                    </Stack>
                    <Stack
                        justifyContent={'flex-end'}
                        alignItems={{ xs: 'flex-start', md: 'flex-end' }}
                    >
                        <Stack
                            direction={'row'}
                            gap={1}
                            justifyContent={'flex-end'}
                            alignItems={'center'}
                        >
                            <StyledButton
                                color={'btn_point_bg'}
                                variant="contained"
                                onClick={handleRegisterUserDialog}
                                disabled={isSingleUser}
                                sx={{ px: { xs: '29.5px', md: 4 }, height: { xs: 32, md: 24 } }}
                            >
                                <Typography variant={'body9'} color={'white'}>
                                    {t('common.action.addAccount')}
                                </Typography>
                            </StyledButton>
                            <StyledButton
                                color={'btn_point_bg'}
                                variant="contained"
                                onClick={handleBulkUserImportDialog}
                                disabled={isSingleUser}
                                sx={{ px: { xs: '29.5px', md: 4 }, height: { xs: 32, md: 24 } }}
                            >
                                <Typography variant={'body9'} color={'white'}>
                                    {t('AdminUserManage.bulkUserImport')}
                                </Typography>
                            </StyledButton>
                        </Stack>
                    </Stack>

                    {checkItems.length > 0 ? (
                        <Stack
                            direction="row"
                            gap={2}
                            alignItems="center"
                            mt={{
                                // 사용자 추가, 사용자 일괄등록 버튼이 있어 xs:6, md:8 -> xs:3, md:4로 변경
                                xs: 3,
                                md: 4,
                            }}
                            mb={4}
                        >
                            <BaseTooltip title={t('tooltip.deselect')}>
                                <IconButton sx={{ p: '6px' }} onClick={() => setCheckItems([])}>
                                    <Image
                                        src="/common/icon-ic-table-close.svg"
                                        alt="close"
                                        width={20}
                                        height={20}
                                    />
                                </IconButton>
                            </BaseTooltip>
                            <Typography variant={'body5'} color={'text_100'}>
                                {t.rich('EnhancedTableToolbar.selected', {
                                    numSelected: checkItems.length,
                                    b: chunks => (
                                        <Typography
                                            variant="body4"
                                            component={'span'}
                                            color={'btn_point_bg'}
                                        >
                                            {chunks}
                                        </Typography>
                                    ),
                                })}
                            </Typography>
                        </Stack>
                    ) : (
                        <Grid
                            container
                            alignItems={'center'}
                            spacing={2}
                            sx={{ minHeight: 32 }}
                            mt={{
                                // 사용자 추가, 사용자 일괄등록 버튼이 있어 xs:6, md:8 -> xs:3, md:4로 변경
                                xs: 3,
                                md: 4,
                            }}
                            gap={{
                                md: 0.5,
                                xs: 3,
                            }}
                            mb={4}
                        >
                            <FilterDropdown
                                open={roleFilterOpen}
                                setOpen={setRoleFilterOpen}
                                placeholder={
                                    role ? t(getRoleNameByValue(role)) : t('common.label.role')
                                }
                                filterList={RoleFilter}
                                value={role}
                                setValue={setRole}
                                type={'role'}
                            />
                            <FilterDropdown
                                open={statusFilterOpen}
                                setOpen={setStatusFilterOpen}
                                placeholder={
                                    status
                                        ? t(`${getStatusNameByValue(status)}`)
                                        : t('common.label.status')
                                }
                                filterList={StatusFilter}
                                value={status}
                                setValue={setStatus}
                                type={'status'}
                            />
                            {(status || role) && (
                                <IconButton
                                    sx={{ border: `1px solid ${palette.btn_border}`, p: '5px' }}
                                    onClick={handleResetFilters}
                                >
                                    <Image
                                        src={'/common/icon-ic-refresh.svg'}
                                        alt={'refresh'}
                                        width={16}
                                        height={16}
                                    />
                                </IconButton>
                            )}
                        </Grid>
                    )}

                    {/* 스크롤 되는 메인 테이블 영역 */}
                    <Box ref={tableAreaRef} sx={{ flex: 1, overflow: 'auto' }}>
                        {loading ? (
                            <Stack
                                sx={{ width: '100%', height: '88%' }}
                                alignItems="center"
                                justifyContent="center"
                            >
                                <Typography variant="body1" color="text_60">
                                    Loading...
                                </Typography>
                                {/* 필요시 CircularProgress 같은 스피너 */}
                            </Stack>
                        ) : userList.length > 0 ? (
                            <UserTable
                                data={userList}
                                getData={getListItems}
                                checkItems={checkItems}
                                setCheckItems={setCheckItems}
                                hasNext={hasNext}
                                cursorSearchCount={cursorSearchCount}
                                setCursorSearchCount={setCursorSearchCount}
                                listPatch={listPatch}
                                showRole={true}
                                showLastLogin={true}
                                showStatus={true}
                                showRequestedDate={false}
                                showScheduledDate={false}
                                showDeletedBy={false}
                            />
                        ) : (
                            <Stack
                                sx={{ width: '100%', mt: '143px' }}
                                justifyContent={'center'}
                                alignItems={'center'}
                            >
                                <Image
                                    src={'/drive/img-img-empty.svg'}
                                    alt={'img-img-empty'}
                                    width={120}
                                    height={120}
                                />
                                <Typography variant={'h3'} color={'text_80'} mt={4}>
                                    {t('adminUsers.empty')}
                                </Typography>
                            </Stack>
                        )}
                    </Box>
                    <Box sx={{ height: '64px', flexShrink: 0 }} />
                </Box>
            </Box>
        </>
    );
}

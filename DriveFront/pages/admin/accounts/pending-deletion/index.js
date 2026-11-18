'use client';

import Image from 'next/image';
import Box from '@mui/material/Box';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Grid, IconButton, Stack, Typography } from '@mui/material';
import palette from '@/theme/palette';
import { useRouter } from '@/hooks/useRouter';
import FilterDropdown from '@/components/common/input/FilterSelect';
import { useTranslations } from 'next-intl';
import api from '@/utils/api';
import UserTable from '@/components/views/admin/users/UserTable';
import Head from 'next/head';
import { buildClearQuery, parseUsersDeletionFiltersFromParams } from '@/utils/queryString';
import { getRangeNameByModifyDate } from '@/utils/filter';
import { RangeDateFilter, WithinDateFilter } from '@/constants/FILTER';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { BaseTooltip } from '@/components/common/tooltip/BaseTooltip';
import useBrandingConfig from '@/hooks/useBrandingConfig';
import { useVvh } from '@/hooks/useVvh';

export default function Users() {
    const t = useTranslations();
    const router = useRouter();
    const { showSnackbar } = useSnackbarContext();
    const { tabTitle, serviceName } = useBrandingConfig();

    const [cursorId, setCursorId] = useState(null);
    const [cursorValue, setCursorValue] = useState(null);
    const [hasNext, setHasNext] = useState(false);

    const [checkItems, setCheckItems] = useState([]);

    // 테이블 바깥(여백/필터/제목 등) 클릭 시 선택 해제 — DriveTable과 동일 동작
    const tableAreaRef = useRef(null);
    const handleContainerMouseDown = e => {
        if (tableAreaRef.current?.contains(e.target)) return;
        setCheckItems(prev => (prev.length ? [] : prev));
    };
    const [loading, setLoading] = useState(true);
    const [userList, setUserList] = useState([]);

    const [requestedDateFilterOpen, setRequestedDateFilterOpen] = useState(false);
    const [scheduledDateFilterOpen, setScheduledDateFilterOpen] = useState(false);
    const [requestedDate, setRequestedDate] = useState(
        router.query.pdSd && router.query.pdEd ? [router.query.pdSd, router.query.pdEd] : undefined
    );
    const [scheduledDate, setScheduledDate] = useState(
        router.query.dsSd && router.query.dsEd ? [router.query.dsSd, router.query.dsEd] : undefined
    );

    const [cursorSearchCount, setCursorSearchCount] = useState(0);

    const getListItems = async (isNewSearch = false) => {
        let params = parseUsersDeletionFiltersFromParams(router.query);
        if (!isNewSearch && cursorId) {
            params = {
                ...params,
                ci: cursorId,
                cv: cursorValue,
                ps: 10,
            };
        }

        try {
            const res = await api.Get('admins/users/pending-delete', { params });

            if (res?.status === 200) {
                setHasNext(res?.data?.hasNext);
                setCursorId(res?.data?.nextCursor);
                setCursorValue(res?.data?.nextCursorValue);
                const newData = res?.data?.data;

                if (isNewSearch) {
                    setUserList(newData);
                } else {
                    setUserList(prevList => {
                        return [...prevList, ...newData];
                    });
                }
            } else {
                setUserList([]);
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

    const handleResetFilters = async () => {
        setRequestedDate(undefined);
        setScheduledDate(undefined);
        const root = router.pathname;
        const queryParams = buildClearQuery(router.query);
        router.push({ pathname: root, query: queryParams }, undefined, { shallow: true });

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
        setCursorValue(null);

        getListItems(true);
    }, [router.query]);

    useEffect(() => {
        if (!router.isReady) return;

        // 쿼리 변화 감지 시 필터 상태 갱신
        setRequestedDate(
            router.query.pdSd && router.query.pdEd
                ? [router.query.pdSd, router.query.pdEd]
                : undefined
        );
        setScheduledDate(
            router.query.dsSd && router.query.dsEd
                ? [router.query.dsSd, router.query.dsEd]
                : undefined
        );
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
                            {t('AdminSidebar.pendingDeletion')}
                        </Typography>
                    </Stack>

                    <Stack
                        direction={'row'}
                        gap={1}
                        sx={{ width: '100%' }}
                        justifyContent={'flex-start'}
                        alignItems={'center'}
                        pt={5}
                    >
                        <Typography
                            variant="body5"
                            color={'text_80'}
                            sx={{ whiteSpace: 'pre-line' }}
                        >
                            {t('pendingDeletion.description')}
                        </Typography>
                    </Stack>

                    {checkItems.length > 0 ? (
                        <Stack
                            direction="row"
                            gap={2}
                            alignItems="center"
                            mt={{
                                xs: 6,
                                md: 8,
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
                                xs: 6,
                                md: 8,
                            }}
                            gap={{
                                md: 0.5,
                                xs: 3,
                            }}
                            mb={4}
                        >
                            <FilterDropdown
                                open={requestedDateFilterOpen}
                                setOpen={setRequestedDateFilterOpen}
                                placeholder={
                                    requestedDate
                                        ? t(`${getRangeNameByModifyDate(requestedDate)}`)
                                        : t('dateFilter.requestedDateFilterType')
                                }
                                type={'requestedDate'}
                                value={requestedDate}
                                setValue={setRequestedDate}
                                filterList={RangeDateFilter}
                            />
                            <FilterDropdown
                                open={scheduledDateFilterOpen}
                                setOpen={setScheduledDateFilterOpen}
                                placeholder={
                                    scheduledDate
                                        ? t(`${getRangeNameByModifyDate(scheduledDate, true)}`)
                                        : t('dateFilter.scheduledDateFilterType')
                                }
                                type={'scheduledDate'}
                                value={scheduledDate}
                                setValue={setScheduledDate}
                                filterList={WithinDateFilter}
                            />
                            {(requestedDate || scheduledDate) && (
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
                                showLastLogin={false}
                                showStatus={false}
                                showRequestedDate={true}
                                showScheduledDate={true}
                                showDeletedBy={true}
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
                                    {t('pendingDeletion.empty')}
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

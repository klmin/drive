'use client';

import Image from 'next/image';
import Box from '@mui/material/Box';
import React, { useEffect, useRef, useState } from 'react';
import { Grid, IconButton, Stack, Typography } from '@mui/material';
import palette from '@/theme/palette';
import { useRouter } from '@/hooks/useRouter';
import { useUser } from '@/context/UserContext';
import FilterDropdown from '@/components/common/input/FilterSelect';
import DriveDrawer from '@/components/views/drive/DriveDrawer';
import DriveTable from '@/components/views/drive/DriveTable';
import { useTranslations } from 'next-intl';
import { useDialog } from '@/provider/DialogProvider';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import api from '@/utils/api';
import { RangeDateFilter, TypeFilter } from '@/constants/FILTER';
import dayjs from 'dayjs';
import Head from 'next/head';
import useWindowMessage from '@/hooks/useWindowMessage';
import { useResources } from '@/provider/ResourceProvider';
import { buildClearQuery, parseRecentFiltersFromParams } from '@/utils/queryString';
import { getFileTypeNameByValue, getRangeNameByModifyDate } from '@/utils/filter';
import { useDriveDownload } from '@/hooks/useDriveDownload';
import { BaseTooltip } from '@/components/common/tooltip/BaseTooltip';
import DriveActionIconMenuBar from '@/components/common/TopActionIconMenuBar';
import useBrandingConfig from '@/hooks/useBrandingConfig';
import { useVvh } from '@/hooks/useVvh';
import useDevice from '@/hooks/useDevice';
import useResourceListPatch from '@/hooks/useResourceListPatch';
import useSyncedSelection from '@/hooks/useSyncedSelection';
import useStarredToggle from '@/hooks/useStarredToggle';

export default function Recent() {
    const t = useTranslations();
    const { isTabletOrMobile } = useDevice();
    const { openDialog } = useDialog();
    const { showSnackbar } = useSnackbarContext();
    const { refreshResources } = useResources();
    const { tabTitle, serviceName } = useBrandingConfig();

    const user = useUser();
    const router = useRouter();

    const tableAreaRef = useRef(null);
    const actionMenuAreaRef = useRef(null);
    const drawerRef = useRef(null);
    const titleAreaRef = useRef(null);
    const [recentSeeMore, setRecentSeeMore] = useState(null);
    const [subMenuOpen, setSubMenuOpen] = useState(false);
    const [drawerOpen, setDrawerOpen] = useState(false);
    const [checkItems, setCheckItems] = useState([]);
    const [selectedDrive, setSelectedDrive] = useState(null);
    const [loading, setLoading] = useState(true);
    const [recentList, setRecentList] = useState([]);

    // 목록 갱신 시 checkItems를 새 목록의 최신 객체로 재동기화(상태 불일치 방지)
    useSyncedSelection(recentList, setCheckItems);
    // 중요문서함 토글처럼 한 행만 바뀌는 경우 전체 재조회 없이 그 행만 갱신
    const { patchItem, removeItem, listPatch } = useResourceListPatch(setRecentList);

    const [filterOption, setFilterOption] = useState(false);

    const [cursorId, setCursorId] = useState(null);
    // 1페이지 재조회 시 증가시켜 테이블 스크롤을 맨 위로 되돌린다
    const [scrollResetKey, setScrollResetKey] = useState(0);
    const [cursorValue, setCursorValue] = useState(null);
    const [cursorType, setCursorType] = useState(null);
    const [hasNext, setHasNext] = useState(false);

    const [modifyDateFilterOpen, setModifyDateFilterOpen] = useState(false);
    const [typeFilterOpen, setTypeFilterOpen] = useState(false);
    const [userFilterOpen, setUserFilterOpen] = useState(false);

    const [fileType, setFileType] = useState(router.query.ft || undefined);
    const [userId, setUserId] = useState(router.query.oi || undefined);
    const [userName, setUserName] = useState(router.query.oin || undefined);
    const [modifyDate, setModifyDate] = useState(
        router.query.sd && router.query.ed ? [router.query.sd, router.query.ed] : undefined
    );
    const [resource, setResource] = useState({});

    const [cursorSearchCount, setCursorSearchCount] = useState(0);
    // 마지막으로 처리한 cursorSearchCount를 기록.
    // cursorId dependency 추가로 인해 cursorId 변경 시마다 effect가 재실행되는데,
    // 동일한 count에 대한 불필요한 API 중복 호출을 방지하기 위한 가드.
    const lastHandledCountRef = useRef(0);

    const areAllItemsStarred = checkItems.every(item => item?.isStarred);
    const rangeName = modifyDate ? getRangeNameByModifyDate(modifyDate) : null;
    const dateFilterLabel = !modifyDate
        ? t('dateFilter.filterType')
        : rangeName?.startsWith('common.dateFilter.')
          ? t(rangeName)
          : rangeName;

    const getListItems = async (isNewSearch = false) => {
        let params = parseRecentFiltersFromParams(router.query);
        if (!isNewSearch && cursorId) {
            params = {
                ...params,
                ci: cursorId,
                cv: cursorValue,
                ct: cursorType,
                ps: 10,
            };
        }

        try {
            const res = await api.Get('recent', { params });

            if (res?.status === 200) {
                setHasNext(res?.data?.hasNext);
                setCursorValue(res?.data?.nextCursorValue);
                setCursorType(res?.data?.nextCursorType);
                setCursorId(res?.data?.nextCursor);

                const groupOrder = [
                    t('common.dateFilter.today'),
                    t('common.dateFilter.yesterday'),
                    t('common.dateFilter.last7Days'),
                    t('common.dateFilter.last30Days'),
                    t('common.dateFilter.thisYear'),
                    t('data.older'),
                ];
                const groups = groupOrder.reduce((acc, key) => ({ ...acc, [key]: [] }), {});

                const today = dayjs();
                const todayFormatted = today.format('YYYY-MM-DD');
                const yesterdayFormatted = today.subtract(1, 'day').format('YYYY-MM-DD');

                res?.data?.data?.forEach(item => {
                    const itemDate = dayjs(item?.recentUpdateDate || item?.recentRegisterDate);
                    const itemDateFormatted = itemDate.format('YYYY-MM-DD');

                    let groupKey = '이전 활동'; // 기본값

                    if (itemDateFormatted === todayFormatted) {
                        groupKey = t('common.dateFilter.today');
                    } else if (itemDateFormatted === yesterdayFormatted) {
                        groupKey = t('common.dateFilter.yesterday');
                    } else if (today.diff(itemDate, 'day') < 7) {
                        groupKey = t('common.dateFilter.last7Days');
                    } else if (today.diff(itemDate, 'day') < 30) {
                        groupKey = t('common.dateFilter.last30Days');
                    } else if (itemDate.isSame(today, 'year')) {
                        groupKey = t('common.dateFilter.thisYear');
                    } else {
                        groupKey = t('data.older');
                    }

                    groups[groupKey].push(item);
                });

                Object.keys(groups).forEach(key => {
                    if (groups[key].length === 0) {
                        delete groups[key];
                    }
                });

                const orderedGroups = {};
                groupOrder.forEach(key => {
                    if (groups[key]) {
                        orderedGroups[key] = groups[key];
                    }
                });

                if (isNewSearch) {
                    setRecentList(orderedGroups || {});
                    setScrollResetKey(k => k + 1);
                } else {
                    setRecentList(prevList => {
                        const newList = { ...prevList }; // 이전 목록을 복사

                        // 새로 가져온 그룹(orderedGroups)을 순회합니다.
                        Object.entries(orderedGroups).forEach(([key, newItems]) => {
                            // 기존에 해당 그룹이 있었으면 기존 항목들을 가져오고, 없었으면 빈 배열을 사용합니다.
                            const existingItems = newList[key] || [];

                            // 기존 항목과 새 항목을 합쳐서 다시 할당합니다.
                            newList[key] = [...existingItems, ...newItems];
                        });

                        return newList;
                    });
                }
            }
        } catch (err) {
            console.error('리스트 가져오기 실패:', err);
        }
    };

    const handleDeleteDrive = async () => {
        // 성공한 항목만 목록에서 제거하기 위해 따로 모은다
        const deletedSeqs = [];

        try {
            for (let item of checkItems) {
                const res =
                    item.resourceType === 'FILE'
                        ? await api.Patch(`files/${item.resourceSeq}/delete`, {
                              ss: item?.sharedByUserSeq || item?.userSeq || undefined,
                          })
                        : await api.Patch(`folders/${item.resourceSeq}/delete`, {
                              ss: item?.sharedByUserSeq || item?.userSeq || undefined,
                          });
                if (res?.status === 200) {
                    deletedSeqs.push(item.resourceSeq);
                    showSnackbar(t('moveToTrash.success'), 'success');
                } else if (res?.code === 423) {
                    showSnackbar(t('lock.locked'), 'error');
                } else {
                    showSnackbar(t('moveToTrash.fail'), 'error');
                }
            }
        } catch (e) {
            showSnackbar(t('moveToTrash.fail'), 'error');
        } finally {
            setCheckItems([]);
            // 목록 전체 재조회 대신 휴지통으로 옮겨진 행만 제거한다(스크롤 위치 유지)
            removeItem(deletedSeqs);
            refreshResources();
        }
    };

    // 다른 상단 액션 아이콘과 동일하게 선택 상태를 유지한다.
    // 목록은 해당 행만 갱신되고, checkItems는 useSyncedSelection이 최신 객체로 다시 맞춘다.
    const { toggleStarred } = useStarredToggle({
        patchItem,
        removeItem,
        reload: isNewSearch => getListItems(isNewSearch),
    });

    const handleMultiStarred = type =>
        toggleStarred(checkItems, {
            forceRemove: type === 'unactive',
            ss: router.query.ss,
        });

    const { handleDownload } = useDriveDownload({ t });

    const handleResetFilters = async () => {
        setFileType(undefined);
        setUserId(undefined);
        setUserName(undefined);
        setModifyDate(undefined);
        const root = router.pathname;
        const queryParams = buildClearQuery(router.query);
        router.push({ pathname: root, query: queryParams }, undefined, { shallow: true });

        setCursorId(null);
    };

    const handleContainerMouseDown = e => {
        const clickedInsideTable = tableAreaRef.current?.contains(e.target);
        const clickedInsideMenu = actionMenuAreaRef.current?.contains(e.target);
        const clickedInsideDrawer = drawerRef.current?.contains(e.target);
        const clickedInsideTitle = titleAreaRef.current?.contains(e.target);

        // 테이블 바깥 영역 & checkItems가 있다면
        if (
            !clickedInsideTable &&
            !clickedInsideMenu &&
            !clickedInsideDrawer &&
            !clickedInsideTitle &&
            checkItems.length > 0
        ) {
            setCheckItems([]);
        }
    };

    useEffect(() => {
        if (!cursorId) return;
        if (cursorSearchCount <= 0) return; // 다음 목록 요청인지
        if (lastHandledCountRef.current >= cursorSearchCount) return; // 불필요한 API 중복 호출 방지

        lastHandledCountRef.current = cursorSearchCount;
        getListItems(false);
    }, [cursorSearchCount, cursorId]);

    useEffect(() => {
        if (!router.isReady) return;
        setCursorId(null);
        setCursorSearchCount(0); // 경로 변경 시 누적된 count 초기화 (이전 네비게이션 잔여값 제거)
        lastHandledCountRef.current = 0;

        getListItems(true);
    }, [router.query]);

    useEffect(() => {
        if (!router.isReady) return;

        // 쿼리 변화 감지 시 필터 상태 갱신
        setFileType(router.query.ft || undefined);
        setUserId(router.query.oi || undefined);
        setUserName(router.query.oin || undefined);
        setModifyDate(
            router.query.sd && router.query.ed ? [router.query.sd, router.query.ed] : undefined
        );
    }, [router.query, router.isReady]);

    useEffect(() => {
        setTimeout(() => setLoading(false), 200);
    }, []);

    useVvh();

    useWindowMessage(() => getListItems(true), 'drive_reload');

    return (
        <>
            <Head>
                <title>{t('meta.recent', { tabTitle })}</title>
                <meta property="title" content={serviceName} />
            </Head>
            <Box
                id={'container'}
                onMouseDown={handleContainerMouseDown}
                sx={{
                    width: '100%',
                    height: 'calc(var(--vvh, 1vh) * 100 - var(--app-banner-height, 0px))',
                    display: 'flex',
                    flexDirection: 'row',
                    position: 'relative',
                }}
            >
                <Box
                    sx={{
                        width: {
                            xs: '100%',
                            md: '100%',
                            midLg: drawerOpen ? 'calc(100% - 320px)' : '100%',
                        },
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
                    {/* 위쪽 Path / 필터 / 버튼 영역 */}
                    <Stack
                        sx={{ width: '100%' }}
                        justifyContent={'flex-end'}
                        alignItems={'flex-end'}
                    >
                        {!drawerOpen && (
                            <BaseTooltip title={t('tooltip.viewDetails')}>
                                <IconButton
                                    ref={drawerRef}
                                    onClick={() => setDrawerOpen(!drawerOpen)}
                                    sx={{
                                        position: 'absolute',
                                        top: 16,
                                        right: 20,
                                        zIndex: 999,
                                        display: {
                                            xs: 'none',
                                            md: !drawerOpen ? 'flex' : 'none',
                                        },
                                    }}
                                >
                                    <Image
                                        src={'/common/ic-expander-left.svg'}
                                        alt={'left'}
                                        width={24}
                                        height={24}
                                    />
                                </IconButton>
                            </BaseTooltip>
                        )}
                    </Stack>
                    <Stack
                        direction={'row'}
                        gap={1}
                        sx={{ width: '100%' }}
                        justifyContent={'flex-start'}
                        alignItems={'center'}
                    >
                        <Box
                            ref={titleAreaRef}
                            sx={{
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: 1,
                            }}
                        >
                            <Box
                                sx={{
                                    width: { xs: 20, md: 24 },
                                    height: { xs: 20, md: 24 },
                                    position: 'relative',
                                }}
                            >
                                <Image src={'/menu/icon-ic-recent.svg'} alt="recent" fill />
                            </Box>
                            <Typography variant="h2_h5" component={'span'}>
                                {t('common.label.recent')}
                            </Typography>
                        </Box>
                    </Stack>
                    {checkItems.length > 0 ? (
                        <DriveActionIconMenuBar
                            page="recent"
                            t={t}
                            router={router}
                            actionMenuAreaRef={actionMenuAreaRef}
                            checkItems={checkItems}
                            setCheckItems={setCheckItems}
                            openDialog={openDialog}
                            handleDownload={handleDownload}
                            handleMultiStarred={handleMultiStarred}
                            areAllItemsStarred={areAllItemsStarred}
                            handleDeleteDrive={handleDeleteDrive}
                            listPatch={listPatch}
                            reload={() => getListItems(true)}
                        />
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
                                open={typeFilterOpen}
                                setOpen={setTypeFilterOpen}
                                placeholder={
                                    fileType
                                        ? t(`${getFileTypeNameByValue(fileType)}`)
                                        : t('fileTypeFilter.filterType')
                                }
                                filterList={TypeFilter}
                                value={fileType}
                                type={'fileType'}
                                setValue={setFileType}
                            />
                            <FilterDropdown
                                open={userFilterOpen}
                                setOpen={setUserFilterOpen}
                                placeholder={userName ? userName : t('common.label.owner')}
                                filterList={[]}
                                value={userName ? userId : undefined}
                                type={'owner'}
                                setValue={setUserId}
                            />
                            <FilterDropdown
                                open={modifyDateFilterOpen}
                                setOpen={setModifyDateFilterOpen}
                                placeholder={dateFilterLabel}
                                type={'date'}
                                value={modifyDate}
                                setValue={setModifyDate}
                                filterList={RangeDateFilter}
                            />
                            {(userId || fileType || modifyDate) && (
                                <IconButton
                                    sx={{
                                        border: `1px solid ${palette.btn_border}`,
                                        width: 24,
                                        height: 24,
                                    }}
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
                    {/** 날짜 라벨의 고정 px과 하단 여백 64px이 레이아웃 계산에 확실히 반영되도록 minHeight 추가 */}
                    <Box
                        sx={{
                            flex: 1,
                            minHeight: 0,
                            overflow: 'auto',
                            outline: 'none',
                            pb: isTabletOrMobile ? '32px' : 0,
                        }}
                    >
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
                        ) : Object.keys(recentList).length > 0 ? (
                            /*
                                테이블 바깥 클릭 시 선택 해제를 위해 ref를 사용.
                                중간 래퍼에 height가 없으면 스크롤 기준이 깨져 커서 페이징이 연속 호출됨.
                                이를 방지하기 위해 height: '100%' 추가함
                             */
                            <Box ref={tableAreaRef} sx={{ height: '100%' }}>
                                <DriveTable
                                    data={recentList}
                                    drawerOpen={drawerOpen}
                                    setDrawerOpen={setDrawerOpen}
                                    checkItems={checkItems}
                                    setCheckItems={setCheckItems}
                                    selectedDrive={selectedDrive}
                                    setSelectedDrive={setSelectedDrive}
                                    showLocation={true}
                                    showDate={true}
                                    showLike={true}
                                    showShare={true}
                                    getData={getListItems}
                                    hasNext={hasNext}
                                    cursorSearchCount={cursorSearchCount}
                                    setCursorSearchCount={setCursorSearchCount}
                                    scrollResetKey={scrollResetKey}
                                    patchItem={patchItem}
                                    removeItem={removeItem}
                                    listPatch={listPatch}
                                />
                            </Box>
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
                                    {t('Recent.text1')}
                                </Typography>
                                <Typography
                                    variant={'body5'}
                                    color={'text_60'}
                                    textAlign={'center'}
                                    mt={1}
                                >
                                    {t('Recent.text2')}
                                </Typography>
                            </Stack>
                        )}
                    </Box>
                    <Box sx={{ height: isTabletOrMobile ? '16px' : '64px', flexShrink: 0 }} />
                </Box>

                {/** Details */}
                {drawerOpen && (
                    <Box
                        sx={{
                            position: 'fixed', // 항상 화면 기준으로 고정
                            top: 'calc(64px + var(--app-banner-height, 0px))', // 상단 AppBar·배너 아래부터 시작
                            right: 0, // 오른쪽 끝에 붙이기
                            zIndex: 1200,
                        }}
                    >
                        <DriveDrawer
                            drawerOpen={drawerOpen}
                            setDrawerOpen={setDrawerOpen}
                            checkItems={checkItems}
                            user={user}
                        />
                    </Box>
                )}
            </Box>
        </>
    );
}

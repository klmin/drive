'use client';

import Image from 'next/image';
import Box from '@mui/material/Box';
import React, { useEffect, useRef } from 'react';
import { Grid, IconButton, Stack, Typography } from '@mui/material';
import palette from '@/theme/palette';
import { useState } from 'react';
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
import Head from 'next/head';
import { useResources } from '@/provider/ResourceProvider';
import { buildClearQuery, parseStarredFiltersFromParams } from '@/utils/queryString';
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

export default function Stared() {
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
    const [cursorId, setCursorId] = useState(null);
    // 1페이지 재조회 시 증가시켜 테이블 스크롤을 맨 위로 되돌린다
    const [scrollResetKey, setScrollResetKey] = useState(0);
    const [cursorValue, setCursorValue] = useState(null);
    const [cursorType, setCursorType] = useState(null);
    const [hasNext, setHasNext] = useState(false);

    const [recentSeeMore, setRecentSeeMore] = useState(null);
    const [starredSeeMore, setstarredSeeMore] = useState(1);
    const [subMenuOpen, setSubMenuOpen] = useState(false);
    const [drawerOpen, setDrawerOpen] = useState(false);
    const [checkItems, setCheckItems] = useState([]);
    const [selectedDrive, setSelectedDrive] = useState(null);

    const [loading, setLoading] = useState(true);
    const [starredList, setStarredList] = useState([]);

    // 목록 갱신 시 checkItems를 새 목록의 최신 객체로 재동기화(상태 불일치 방지)
    useSyncedSelection(starredList, setCheckItems);
    // 중요문서함 해제 시 전체 재조회 없이 해당 행만 목록에서 제거
    const { patchItem, removeItem, listPatch } = useResourceListPatch(setStarredList);

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

    const rangeName = modifyDate ? getRangeNameByModifyDate(modifyDate) : null;
    const dateFilterLabel = !modifyDate
        ? t('dateFilter.filterType')
        : rangeName?.startsWith('common.dateFilter.')
          ? t(rangeName)
          : rangeName;

    const getListItems = async (isNewSearch = false) => {
        let params = parseStarredFiltersFromParams(router.query);
        if (!isNewSearch) {
            params = {
                ...params,
                ci: cursorId,
                cv: cursorValue,
                ct: cursorType,
                ps: 10,
            };
        }

        const res = await api.Get('starred', { params });

        if (res?.status === 200) {
            setHasNext(res?.data?.hasNext);
            setCursorValue(
                !res?.data?.nextCursorValue && router.query.sb === 'size'
                    ? 0
                    : res?.data?.nextCursorValue
            ); // 폴더인 경우 size 0
            setCursorType(res?.data?.nextCursorType);
            setCursorId(res?.data?.nextCursor);
            const newData = res?.data?.data;
            if (isNewSearch) {
                setStarredList(newData || []);
                setScrollResetKey(k => k + 1);
            } else {
                setStarredList(prevList => [...prevList, ...newData]);
            }
        }
    };

    // 다른 상단 액션 아이콘과 동일하게 선택 상태를 유지한다.
    // 중요문서함에서 해제하면 해당 행이 목록에서 빠지고, 선택도 useSyncedSelection이 함께 정리한다.
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

    return (
        <>
            <Head>
                <title>{t('meta.starred', { tabTitle })}</title>
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
                                <Image src={'/menu/icon-ic-star.svg'} alt="starred" fill />
                            </Box>
                            <Typography variant="h2_h5" component={'span'}>
                                {t('common.label.starred')}
                            </Typography>
                        </Box>
                    </Stack>
                    {checkItems.length > 0 ? (
                        <DriveActionIconMenuBar
                            page="starred"
                            t={t}
                            router={router}
                            actionMenuAreaRef={actionMenuAreaRef}
                            checkItems={checkItems}
                            setCheckItems={setCheckItems}
                            openDialog={openDialog}
                            handleDownload={handleDownload}
                            handleMultiStarred={handleMultiStarred}
                            areAllItemsStarred={true} // starred에서는 항상 true
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
                        ) : starredList.length > 0 ? (
                            /*
                                테이블 바깥 클릭 시 선택 해제를 위해 ref를 사용.
                                중간 래퍼에 height가 없으면 스크롤 기준이 깨져 커서 페이징이 연속 호출됨.
                                이를 방지하기 위해 height: '100%' 추가함
                            */
                            <Box ref={tableAreaRef} sx={{ height: '100%' }}>
                                <DriveTable
                                    data={starredList}
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
                                    shouldShowStar={true}
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
                                    {t('Starred.text1')}
                                </Typography>
                                <Typography
                                    variant={'body5'}
                                    color={'text_60'}
                                    textAlign={'center'}
                                    mt={1}
                                >
                                    {t('Starred.text2')}
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

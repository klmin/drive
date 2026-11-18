'use client';

import Image from 'next/image';
import StyledTextField from '@/components/common/input/StyledTextField';
import Box from '@mui/material/Box';
import StyledButton from '@/components/common/button/StyledButton';
import React, { useEffect, useRef } from 'react';
import { Grid, IconButton, Stack, Typography } from '@mui/material';
import palette from '@/theme/palette';
import { useState } from 'react';
import { useRouter } from '@/hooks/useRouter';
import { useUser } from '@/context/UserContext';
import FilterDropdown from '@/components/common/input/FilterSelect';
import DriveDrawer from '@/components/views/drive/DriveDrawer';
import DriveTable from '@/components/views/drive/DriveTable';
import useResourceListPatch from '@/hooks/useResourceListPatch';
import { useTranslations } from 'next-intl';
import api from '@/utils/api';
import { useDialog } from '@/provider/DialogProvider';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import Head from 'next/head';
import { RangeDateFilter, TypeFilter } from '@/constants/FILTER';
import { CloseFilled } from '@/icon/Close';
import { buildClearQuery, parseTrashFiltersFromParams } from '@/utils/queryString';
import { getFileTypeNameByValue, getRangeNameByModifyDate } from '@/utils/filter';
import { BaseTooltip } from '@/components/common/tooltip/BaseTooltip';
import DriveActionIconMenuBar from '@/components/common/TopActionIconMenuBar';
import useBrandingConfig from '@/hooks/useBrandingConfig';
import { useVvh } from '@/hooks/useVvh';
import useDevice from '@/hooks/useDevice';
import useSyncedSelection from '@/hooks/useSyncedSelection';
import useTrashRestore from '@/hooks/useTrashRestore';

export default function Trash() {
    const t = useTranslations();
    const { isTabletOrMobile } = useDevice();
    const user = useUser();
    const router = useRouter();
    const { showSnackbar } = useSnackbarContext();
    const { openDialog } = useDialog();
    const { tabTitle, serviceName } = useBrandingConfig();

    const tableAreaRef = useRef(null);
    const actionMenuAreaRef = useRef(null);
    const drawerRef = useRef(null);
    const titleAreaRef = useRef(null);
    const emptyTrashAreaRef = useRef(null);
    const [recentSeeMore, setRecentSeeMore] = useState(null);
    const [subMenuOpen, setSubMenuOpen] = useState(false);
    const [drawerOpen, setDrawerOpen] = useState(false);
    const [checkItems, setCheckItems] = useState([]);
    const [selectedDrive, setSelectedDrive] = useState(null);
    const [loading, setLoading] = useState(true);

    const [filterOption, setFilterOptdion] = useState(false);

    const [cursorId, setCursorId] = useState(null);
    // 1페이지 재조회 시 증가시켜 테이블 스크롤을 맨 위로 되돌린다
    const [scrollResetKey, setScrollResetKey] = useState(0);
    const [cusorValue, setCursorValue] = useState(null);
    const [cursorType, setCursorType] = useState(null);
    const [hasNext, setHasNext] = useState(false);

    const [modifyDateFilterOpen, setModifyDateFilterOpen] = useState(false);
    const [typeFilterOpen, setTypeFilterOpen] = useState(false);
    const [userFilterOpen, setUserFilterOpen] = useState(false);

    const [fileType, setFileType] = useState(router.query.ft || undefined);
    const [userId, setUserId] = useState(router.query.di || undefined);
    const [userName, setUserName] = useState(router.query.din || undefined);
    const [modifyDate, setModifyDate] = useState(
        router.query.sd && router.query.ed ? [router.query.sd, router.query.ed] : undefined
    );
    const [resource, setResource] = useState({});

    const [trashList, setTrashList] = useState([]);

    // 목록 갱신 시 checkItems를 새 목록의 최신 객체로 재동기화(상태 불일치 방지).
    useSyncedSelection(trashList, setCheckItems);
    // 복원·영구삭제된 행만 목록에서 제거한다(전체 재조회 시 스크롤 위치가 초기화됨)
    const { removeItem, listPatch } = useResourceListPatch(setTrashList, { folderScoped: true });
    const [cursorSearchCount, setCursorSearchCount] = useState(0);
    // 마지막으로 처리한 cursorSearchCount를 기록.
    // cursorId dependency 추가로 인해 cursorId 변경 시마다 effect가 재실행되는데,
    // 동일한 count에 대한 불필요한 API 중복 호출을 방지하기 위한 가드.
    const lastHandledCountRef = useRef(0);

    const [fileMoveDialogOpen, setFileMoveDialogOpen] = useState(false);

    const rangeName = modifyDate ? getRangeNameByModifyDate(modifyDate) : null;
    const dateFilterLabel = !modifyDate
        ? t('dateFilter.filterType')
        : rangeName?.startsWith('common.dateFilter.')
          ? t(rangeName)
          : rangeName;

    const getListItems = async (isNewSearch = false) => {
        let params = parseTrashFiltersFromParams(router.query);
        if (!isNewSearch) {
            params = {
                ...params,
                ci: cursorId,
                cv: cusorValue,
                ct: cursorType,
                ps: 10,
            };
        }

        try {
            const res = await api.Get('trash', { params });

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
                    setTrashList(newData || []);
                    setScrollResetKey(k => k + 1);
                } else {
                    setTrashList(prevList => [...prevList, ...newData]);
                }
            }
        } catch (err) {
            console.error('리스트 가져오기 실패:', err);
        }
    };

    const handleCompletelyDeleteDrive = async () => {
        let successCount = 0;
        let failCount = 0;
        const items = checkItems;

        openDialog('alert', {
            title: t('hardDeleteDialog.text1'),
            description:
                items.length === 1
                    ? t('hardDeleteDialog.text2Single', {
                          resourceName: items[0]?.resourceName,
                      })
                    : items.length > 1 && t('hardDeleteDialog.text2', { count: items.length }),
            buttonName: t('common.action.hardDelete'),
            cancelText: t('common.button.cancel'),

            onClick: async () => {
                // 성공한 항목만 목록에서 제거하기 위해 따로 모은다
                const deletedSeqs = [];

                for (let item of items) {
                    const res = await (item?.resourceType === 'FOLDER'
                        ? api.Delete(`trash/folders/${item?.resourceSeq}`, {})
                        : api.Delete(`trash/files/${item?.resourceSeq}`, {}));

                    if (res?.status === 200) {
                        successCount++;
                        deletedSeqs.push(item?.resourceSeq);
                    } else {
                        failCount++;
                    }
                }

                if (successCount > 0 && failCount === 0) {
                    showSnackbar(t('hardDelete.success'), 'success');
                } else if (successCount > 0 && failCount > 0) {
                    showSnackbar(
                        t('hardDelete.partial', {
                            success: successCount,
                            fail: failCount,
                        }),
                        'warning'
                    ); // 일부 성공/실패
                } else {
                    showSnackbar(t('hardDelete.fail'), 'error');
                }

                setCheckItems([]);
                removeItem(deletedSeqs);
                window.postMessage('quota_reload', '*');
            },
        });
    };

    const handleEmptyTrash = async () => {
        openDialog('alert', {
            title: t('hardDeleteDialog.text1'),
            description: t('hardDeleteDialog.text2'),
            buttonName: t('common.action.hardDelete'),
            cancelText: t('common.button.cancel'),
            onClick: async () => {
                const res = await api.Delete('trash/empty', {});

                if (res?.status === 200) {
                    showSnackbar(t('hardDelete.success'), 'success');
                    await getListItems(true);
                    window.postMessage('quota_reload', '*');
                    setCheckItems([]);
                } else {
                    showSnackbar(t('hardDelete.fail'), '');
                }
            },
        });
    };

    const { restoreItems } = useTrashRestore({
        onRestored: async () => {
            setCheckItems([]);
            await getListItems(true);
        },
    });

    const handleRestore = () => restoreItems(checkItems);

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
        const clickedInsideEmptyTrash = emptyTrashAreaRef.current?.contains(e.target);

        // 테이블 바깥 영역 & checkItems가 있다면
        if (
            !clickedInsideTable &&
            !clickedInsideMenu &&
            !clickedInsideDrawer &&
            !clickedInsideTitle &&
            !clickedInsideEmptyTrash &&
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
        setUserId(router.query.di || undefined);
        setUserName(router.query.din || undefined);
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
                <title>{t('meta.trash', { tabTitle })}</title>
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
                                <Image src={'/menu/icon-ic-trash.svg'} alt="trash" fill />
                            </Box>
                            <Typography variant="h2_h5" component={'span'}>
                                {t('common.label.trash')}
                            </Typography>
                        </Box>
                    </Stack>
                    <Stack
                        direction={{
                            xs: 'column',
                            md: 'row',
                        }}
                        mt={{
                            xs: 6,
                            md: 9,
                        }}
                        alignItems={{
                            xs: 'flex-start',
                            md: 'center',
                        }}
                        gap={{
                            xs: 4,
                            md: 2,
                        }}
                        mb={3}
                    >
                        <Box ref={emptyTrashAreaRef} sx={{ display: 'contents' }}>
                            <StyledButton
                                variant="outlined"
                                color="btn_border"
                                sx={{
                                    gap: 0.5,
                                    pl: 1.5,
                                    pr: 2,
                                    py: { md: 1.5, xs: 2 },
                                    color: palette.text_80,
                                }}
                                onClick={handleEmptyTrash}
                                disabled={trashList.length === 0}
                            >
                                <CloseFilled width={16} height={16} />
                                <Typography variant={'body9'} color={'text_80'}>
                                    {t('data.emptyTrash')}
                                </Typography>
                            </StyledButton>
                            <Typography variant={'body9'} color={'text_80'}>
                                {t('Trash.text')}
                            </Typography>
                        </Box>
                    </Stack>
                    {checkItems.length === 0 ? (
                        <Grid
                            container
                            alignItems={'center'}
                            spacing={2}
                            sx={{ minHeight: 32 }}
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
                                placeholder={userName ? userName : t('ownerFilter.trashFilterType')}
                                filterList={[]}
                                value={userName ? userId : undefined}
                                type={'deleteUser'}
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
                    ) : (
                        <DriveActionIconMenuBar
                            page="trash"
                            t={t}
                            router={router}
                            actionMenuAreaRef={actionMenuAreaRef}
                            checkItems={checkItems}
                            setCheckItems={setCheckItems}
                            handleRestore={handleRestore}
                            handleCompletelyDeleteDrive={handleCompletelyDeleteDrive}
                            listPatch={listPatch}
                            mb={4}
                        />
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
                        ) : trashList.length > 0 ? (
                            /*
                                테이블 바깥 클릭 시 선택 해제를 위해 ref를 사용.
                                중간 래퍼에 height가 없으면 스크롤 기준이 깨져 커서 페이징이 연속 호출됨.
                                이를 방지하기 위해 height: '100%' 추가함
                            */
                            <Box ref={tableAreaRef} sx={{ height: '100%' }}>
                                <DriveTable
                                    data={trashList}
                                    drawerOpen={drawerOpen}
                                    setDrawerOpen={setDrawerOpen}
                                    checkItems={checkItems}
                                    setCheckItems={setCheckItems}
                                    selectedDrive={selectedDrive}
                                    setSelectedDrive={setSelectedDrive}
                                    showLocation={true}
                                    showDate={true}
                                    showLike={false}
                                    showDeleteUser={true}
                                    getData={getListItems}
                                    hasNext={hasNext}
                                    cursorSearchCount={cursorSearchCount}
                                    setCursorSearchCount={setCursorSearchCount}
                                    scrollResetKey={scrollResetKey}
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
                                    {t('Trash.text1')}
                                </Typography>
                                <Typography
                                    variant={'body5'}
                                    color={'text_60'}
                                    textAlign={'center'}
                                    mt={1}
                                >
                                    {t('Trash.text')}
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

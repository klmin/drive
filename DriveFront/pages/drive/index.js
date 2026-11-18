'use client';

import Image from 'next/image';
import Box from '@mui/material/Box';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Grid, IconButton, Stack, Typography } from '@mui/material';
import palette from '@/theme/palette';
import { useRouter } from '@/hooks/useRouter';
import { useUser } from '@/context/UserContext';
import FilterDropdown from '@/components/common/input/FilterSelect';
import DriveDrawer from '@/components/views/drive/DriveDrawer';
import DriveTable from '@/components/views/drive/DriveTable';
import { useTranslations } from 'next-intl';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { useDialog } from '@/provider/DialogProvider';
import api from '@/utils/api';
import { getResourceDetail } from '@/apis/resourceApi';
import { RangeDateFilter, TypeFilter } from '@/constants/FILTER';
import useWindowMessage from '@/hooks/useWindowMessage';
import StyledDriveContextMenu from '@/components/common/DriveContextMenu';
import Head from 'next/head';
import Path from '@/components/views/Path';
import FileDropZone from '@/components/views/FileDropZone';
import useDevice from '@/hooks/useDevice';
import { useResources } from '@/provider/ResourceProvider';
import UploadBox from '@/components/views/UploadBox';
import { buildClearQuery, parseDriveFiltersFromParams } from '@/utils/queryString';
import { getFileTypeNameByValue, getRangeNameByModifyDate } from '@/utils/filter';
import { useDrivePaste } from '@/hooks/useDrivePaste';
import { useClipboard } from '@/provider/ClipboardProvider';
import { useDriveDownload } from '@/hooks/useDriveDownload';
import { BaseTooltip } from '@/components/common/tooltip/BaseTooltip';
import DriveActionIconMenuBar from '@/components/common/TopActionIconMenuBar';
import useBrandingConfig from '@/hooks/useBrandingConfig';
import { useVvh } from '@/hooks/useVvh';
import useResourceListPatch from '@/hooks/useResourceListPatch';
import useSyncedSelection from '@/hooks/useSyncedSelection';
import useStarredToggle from '@/hooks/useStarredToggle';

function isTextEditingTarget(el) {
    const tag = (el?.tagName || '').toLowerCase();
    if (tag === 'textarea') return true;
    if (el?.isContentEditable) return true;
    if (tag !== 'input') return false;
    const type = (el.type || '').toLowerCase();
    return [
        'text',
        'search',
        'email',
        'url',
        'tel',
        'password',
        'number',
        'date',
        'datetime-local',
        'month',
        'time',
        'week',
    ].includes(type);
}

export default function Drive() {
    const t = useTranslations();
    const { openDialog } = useDialog();
    const { showSnackbar } = useSnackbarContext();
    const { isMobile, isTabletOrMobile } = useDevice();
    const { refreshResources } = useResources();
    const { tabTitle, serviceName } = useBrandingConfig();

    const user = useUser();
    const router = useRouter();

    const tableAreaRef = useRef(null);
    const actionMenuAreaRef = useRef(null);
    const drawerRef = useRef(null);
    const titleAreaRef = useRef(null);
    const [isDragging, setIsDragging] = useState(false);
    const [cursorId, setCursorId] = useState(null);
    // 1페이지 재조회 시 증가시켜 테이블 스크롤을 맨 위로 되돌린다
    const [scrollResetKey, setScrollResetKey] = useState(0);
    const [cursorValue, setCursorValue] = useState(null);
    const [cursorType, setCursorType] = useState(null);
    const [hasNext, setHasNext] = useState(false);

    const [drawerOpen, setDrawerOpen] = useState(false);
    const [checkItems, setCheckItems] = useState([]);
    const [selectedDrive, setSelectedDrive] = useState(null);

    const [resourceList, setResourceList] = useState([]);
    const [loading, setLoading] = useState(true);
    const [filterOption, setFilterOptdion] = useState(false);

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
    const [isDragActive, setIsDragActive] = useState(false);

    const [anchorEl, setAnchorEl] = useState(null);
    const handleMenuOpen = e => setAnchorEl(e.currentTarget);
    const [menuState, setMenuState] = useState({
        visible: false,
        top: 0,
        left: 0,
        selectedRow: null,
    });

    const [cursorSearchCount, setCursorSearchCount] = useState(0);
    // 마지막으로 처리한 cursorSearchCount를 기록.
    // cursorId dependency 추가로 인해 cursorId 변경 시마다 effect가 재실행되는데,
    // 동일한 count에 대한 불필요한 API 중복 호출을 방지하기 위한 가드.
    const lastHandledCountRef = useRef(0);

    // 목록이 갱신되면 checkItems를 새 목록의 최신 객체로 다시 맞춘다
    // (낡은 스냅샷 때문에 상단 액션바와 행 아이콘 상태가 어긋나던 문제 방지)
    useSyncedSelection(resourceList, setCheckItems);
    // 중요문서함 토글처럼 한 행만 바뀌는 경우 전체 재조회 없이 그 행만 갱신
    // 현재 폴더 기준 목록이므로 이동된 항목은 목록에서 빠진다
    const { patchItem, removeItem, listPatch } = useResourceListPatch(setResourceList, {
        folderScoped: true,
    });

    const areAllItemsStarred = checkItems.every(item => item?.isStarred);
    const rangeName = modifyDate ? getRangeNameByModifyDate(modifyDate) : null;
    const dateFilterLabel = !modifyDate
        ? t('dateFilter.filterType')
        : rangeName?.startsWith('common.dateFilter.')
          ? t(rangeName)
          : rangeName;

    // 클립보드
    const { getClipboard } = useClipboard();
    const rootRef = useRef(null);

    const handleEmptyAreaContextMenu = e => {
        e.preventDefault();
        setMenuState({
            visible: true,
            top: e.clientY,
            left: e.clientX,
            selectedRow: null,
        });
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
                } else if (res?.status === 423) {
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
    // reload는 getResources가 아래에 선언되어 있어 화살표로 감싸 호출 시점에 참조한다.
    const { toggleStarred } = useStarredToggle({
        patchItem,
        removeItem,
        reload: isNewSearch => getResources(isNewSearch),
    });

    const handleMultiStarred = type =>
        toggleStarred(checkItems, {
            forceRemove: type === 'unactive',
            ss: router.query.ss,
        });

    const { handleDownload } = useDriveDownload({ t });

    const getResources = async (isNewSearch = false) => {
        let params = parseDriveFiltersFromParams(router.query);
        if (!isNewSearch && cursorId) {
            params = {
                ...params,
                ci: cursorId,
                cv: cursorValue,
                ct: cursorType,
                ps: 10,
            };
        }

        const res = await api.Get('resources', { params });

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
                setResourceList(newData || []);
                setScrollResetKey(k => k + 1);
            } else {
                setResourceList(prevList => [...prevList, ...newData]);
            }
        }
    };

    const getPfsResources = async () => {
        if (!router.query.pfs) return;
        const data = await getResourceDetail(router.query.pfs);

        if (data) {
            setResource(data);
        }
    };

    const handleFilesDropped = ({ files, folders: droppedFolders }) => {
        const allFileObjects = [...files];
        const allFolderPaths = droppedFolders.map(f => f.path);

        const rootNodes = [];
        const nodeMap = new Map();

        const findOrCreateNode = (part, parentNode) => {
            const children = parentNode ? parentNode.children : rootNodes;
            let node = children.find(child => child.name === part);

            if (!node) {
                node = {
                    name: part,
                    type: 'folder',
                    children: [],
                    file: null,
                };
                children.push(node);
            }
            return node;
        };

        allFileObjects.forEach(file => {
            const path = file.webkitRelativePath || file.name;
            const parts = path.split('/');
            let currentNode = null;

            parts.forEach((part, index) => {
                currentNode = findOrCreateNode(part, currentNode);
                if (index === parts.length - 1) {
                    currentNode.type = 'file';
                    currentNode.file = file;
                    delete currentNode.children;
                }
            });
        });

        allFolderPaths.forEach(path => {
            const parts = path.split('/');
            let currentNode = null;
            parts.forEach(part => {
                currentNode = findOrCreateNode(part, currentNode);
            });
        });

        if (rootNodes.find(u => u?.type === 'folder') && isMobile) {
            return;
        }

        openDialog('uploadFiles', {
            uploadItems: rootNodes,
            reload: () => getResources(true),
        });
    };

    const { onPasteFromClipboard } = useDrivePaste({
        router,
        getClipboard,
        getData: getResources,
        setCheckItems,
        showSnackbar,
        t,
        user,
        openDialog,
    });

    const handleKeyDownGlobal = useCallback(
        e => {
            if (isTextEditingTarget(e.target)) return;
            const withCtrlOrCmd = e.metaKey || e.ctrlKey;
            const key = e.key.length === 1 ? e.key.toLowerCase() : e.key;
            if (withCtrlOrCmd && key === 'v') {
                e.preventDefault();
                onPasteFromClipboard();
            }
        },
        [onPasteFromClipboard]
    );

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
        getResources(false);
    }, [cursorSearchCount, cursorId]);

    useEffect(() => {
        if (!router.isReady || !user?.userSeq) return;

        setResource({});
        setCursorId(null);
        setCursorSearchCount(0); // 경로 변경 시 누적된 count 초기화 (이전 네비게이션 잔여값 제거)
        lastHandledCountRef.current = 0;
        getResources(true);
        getPfsResources();
    }, [router.isReady, router.query, user?.userSeq]);

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
        rootRef.current?.focus?.();
    }, [router.asPath]);

    useEffect(() => {
        setTimeout(() => setLoading(false), 200);
    }, []);

    useVvh();

    useWindowMessage(() => getResources(true), 'drive_reload');

    return (
        <>
            <Head>
                <title>{t('meta.mydrive', { tabTitle })}</title>
                <meta property="title" content={serviceName} />
            </Head>
            <FileDropZone
                onFilesDropped={handleFilesDropped}
                isDragActive={isDragActive}
                setIsDragActive={setIsDragActive}
            >
                <Box
                    id={'container'}
                    onContextMenu={e => {
                        e.preventDefault();
                        handleEmptyAreaContextMenu(e);
                    }}
                    onMouseDown={handleContainerMouseDown}
                    sx={{
                        width: '100%',
                        height: 'calc(var(--vvh, 1vh) * 100 - var(--app-banner-height, 0px))',
                        display: 'flex',
                        flexDirection: 'row',
                        position: 'relative',
                        justifyContent: 'space-between',
                        overflow: 'hidden',
                    }}
                >
                    <StyledDriveContextMenu
                        menuState={menuState}
                        onClose={() => setMenuState({ ...menuState, visible: false })}
                        reload={getResources}
                    />
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
                            <Path
                                titleAreaRef={titleAreaRef}
                                title={t('common.label.myDrive')}
                                pathName={'/drive'}
                                img={<Image src={'/menu/icon-ic-drive.svg'} alt="drive" fill />}
                                resource={resource}
                            />
                        </Stack>

                        {checkItems.length > 0 ? (
                            <DriveActionIconMenuBar
                                page="drive"
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
                                reload={() => getResources(true)}
                                listPatch={listPatch}
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
                        <Box
                            ref={rootRef}
                            tabIndex={0}
                            onKeyDown={handleKeyDownGlobal}
                            onMouseDown={e => {
                                if (!isTextEditingTarget(e.target)) rootRef.current?.focus?.();
                            }}
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
                            ) : resourceList.length > 0 ? (
                                /*
                                    테이블 바깥 클릭 시 선택 해제를 위해 ref를 사용.
                                    중간 래퍼에 height가 없으면 스크롤 기준이 깨져 커서 페이징이 연속 호출됨.
                                    이를 방지하기 위해 height: '100%' 추가함
                                 */
                                <Box ref={tableAreaRef} sx={{ height: '100%' }}>
                                    <DriveTable
                                        data={resourceList}
                                        drawerOpen={drawerOpen}
                                        setDrawerOpen={setDrawerOpen}
                                        checkItems={checkItems}
                                        setCheckItems={setCheckItems}
                                        selectedDrive={selectedDrive}
                                        setSelectedDrive={setSelectedDrive}
                                        showLocation={false}
                                        showDate={false}
                                        showLike={true}
                                        showShare={true}
                                        getData={getResources}
                                        hasNext={hasNext}
                                        isDragActive={isDragActive}
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
                                    sx={{ width: '100%', height: '88%' }}
                                    alignItems={'center'}
                                    position={'relative'}
                                >
                                    <UploadBox isDragActive={isDragActive} />
                                    <Stack
                                        flexDirection={'column'}
                                        alignItems={'center'}
                                        pt={'143px'}
                                    >
                                        <Image
                                            src={'/drive/img-img-empty.svg'}
                                            alt={'img-img-empty'}
                                            width={120}
                                            height={120}
                                        />
                                        <Typography variant={'h3'} color={'text_80'} mt={4}>
                                            {t('MyDrive.text1')}
                                        </Typography>
                                        <Typography
                                            variant={'body5'}
                                            color={'text_60'}
                                            textAlign={'center'}
                                            mt={1}
                                        >
                                            {t('MyDrive.text2')}
                                        </Typography>
                                    </Stack>
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
            </FileDropZone>
        </>
    );
}

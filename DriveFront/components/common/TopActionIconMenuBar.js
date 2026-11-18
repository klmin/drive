import React, { useMemo } from 'react';
import { IconButton, Stack, Typography } from '@mui/material';
import Image from 'next/image';
import { hasLockedFile, hasMyDriveResource, hasSharedRootResource } from '@/utils/resourceUtils';
import { BaseTooltip } from './tooltip/BaseTooltip';
import useClientConfig from '@/hooks/useClientConfig';

const DriveActionIconMenuBar = ({
    page, // 'drive' | 'recent' | 'search' | 'starred' | 'shared' | 'trash'
    t,
    router,
    actionMenuAreaRef,

    checkItems,
    setCheckItems,

    openDialog,

    handleDownload,
    handleMultiStarred,

    areAllItemsStarred,

    // 삭제/복원/영구삭제
    handleDeleteDrive,
    handleRestore,
    handleCompletelyDeleteDrive,

    reload, // ex) () => getResources(true) or () => getListItems(true)
    // 목록 전체 재조회 대신 변경된 행만 갱신하기 위한 헬퍼(있으면 다이얼로그가 우선 사용)
    listPatch,

    // 스타일 유지용(각 페이지의 mt/mb 차이를 그대로 반영) trash일때는 mt x
    mt = { xs: 6, md: 8 },
    mb = 4,
}) => {
    const { shareEnabled } = useClientConfig();
    const items = useMemo(() => checkItems ?? [], [checkItems]);

    // 페이지별 분기
    const isRecentLike = page === 'recent' || page === 'search' || page === 'starred';
    const isShared = page === 'shared';
    const isTrash = page === 'trash';

    // 공통 disabled 계산
    const hasCannotView = items.some(c => !c?.canView);
    const hasCannotShare = items.some(c => !c?.canShare);
    const hasCannotDownload = items.some(c => !c?.canDownload);
    const hasCannotEdit = items.some(c => !c?.canEdit);
    const hasCannotDelete = items.some(c => !c?.canDelete);
    const hasShareSeq = items.some(i => i?.shareSeq); // shareSeq가 있다(= 공유된 리소스). 공유문서함 루트가 아니더라도, shareSeq가 있는 경우는 '공유' 메뉴 활성화 필요

    const isMulti = items.length > 1;
    const isLocked = hasLockedFile(items);
    const isSharedRoot =
        (isShared && !router.query.pfs) || (!isShared && hasSharedRootResource(items));
    const isMyDrive = hasMyDriveResource(items);

    // disabled 변수들(페이지별 차이만 반영)
    const disableShare_drive = isMulti || hasCannotShare;
    // recent/search/starred: "내 드라이브가 아니거나, "공유문서함 루트"이면서 canShare false인 경우 disabled 조건 추가
    const disableShare_recentLike =
        isMulti || hasCannotShare || !(isMyDrive || isSharedRoot || (!isSharedRoot && hasShareSeq));
    // shared: canShare 조건 추가
    const disableShare_shared =
        isMulti || hasCannotShare || !(isMyDrive || isSharedRoot || (!isSharedRoot && hasShareSeq));

    const disableDownload = hasCannotDownload;

    // canEdit false이거나, 잠긴 파일이 포함되었거나, 공유 문서함 루트에 있는 리소스가 포함된 경우 이동 불가
    const disableMove = hasCannotEdit || isLocked || isSharedRoot;
    // canDelete false이거나, 잠긴 파일이 포함된 경우 휴지통 이동 불가
    const disableTrash = hasCannotDelete || isLocked;

    const handleOpenShare = () =>
        openDialog('fileShare', {
            item: items?.[0],
            setCheckItems,
            reload,
            listPatch,
        });

    const handleOpenMove = () =>
        openDialog('fileMove', {
            items: items,
            reload,
            setCheckItems,
            listPatch,
        });

    const handleOpenDeleteConfirm = () =>
        openDialog('alert', {
            title: t('common.action.moveToTrash'),
            description: t('Trash.text'),
            onClick: handleDeleteDrive,
        });

    return (
        <Stack direction="row" gap={2} alignItems="center" mt={isTrash ? undefined : mt} mb={mb}>
            <Stack ref={actionMenuAreaRef} direction="row" gap={1} alignItems="center">
                {/* deselect */}
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

                {/* selected text */}
                <Typography variant={'body5'} color={'text_100'}>
                    {t.rich('EnhancedTableToolbar.selected', {
                        numSelected: items.length,
                        b: chunks => (
                            <Typography variant="body4" component={'span'} color={'btn_point_bg'}>
                                {chunks}
                            </Typography>
                        ),
                    })}
                </Typography>

                {/* trash */}
                {isTrash ? (
                    <>
                        <BaseTooltip title={t('common.action.restore')}>
                            <IconButton sx={{ p: '6px' }} onClick={handleRestore}>
                                <Image
                                    src="/common/icon-ic-update.svg"
                                    alt="update"
                                    width={20}
                                    height={20}
                                />
                            </IconButton>
                        </BaseTooltip>

                        <BaseTooltip title={t('common.action.hardDelete')}>
                            <IconButton sx={{ p: '6px' }} onClick={handleCompletelyDeleteDrive}>
                                <Image
                                    src="/common/icon-ic-trash-outline.svg"
                                    alt="trash"
                                    width={20}
                                    height={20}
                                />
                            </IconButton>
                        </BaseTooltip>
                    </>
                ) : (
                    <>
                        {/* share */}
                        {shareEnabled && !isMulti && (
                            <BaseTooltip title={t('common.action.share')}>
                                <IconButton
                                    sx={{ p: '6px' }}
                                    disabled={
                                        // "공유" 메뉴 disabled 처리 조건
                                        // 1. 공통 조건: 여러 개 선택하거나, canView false인 항목이 하나라도 있으면 disabled
                                        // 2. recent/search/starred: "내 드라이브 or 공유문서함 루트"가 아니라면 disabled
                                        // 3. shared: canShare false인 항목이 하나라도 있으면 disabled
                                        isShared // shared
                                            ? disableShare_shared
                                            : isRecentLike // recent/search/starred
                                              ? disableShare_recentLike
                                              : disableShare_drive // drive
                                    }
                                    onClick={handleOpenShare}
                                >
                                    <Image
                                        src="/common/icon-ic-share-outline.svg"
                                        alt="download"
                                        width={20}
                                        height={20}
                                    />
                                </IconButton>
                            </BaseTooltip>
                        )}

                        {/* download */}
                        <BaseTooltip title={t('common.action.download')}>
                            <IconButton
                                sx={{ p: '6px' }}
                                disabled={disableDownload}
                                onClick={() => handleDownload(items)}
                            >
                                <Image
                                    src="/common/icon-ic-download.svg"
                                    alt="download"
                                    width={20}
                                    height={20}
                                />
                            </IconButton>
                        </BaseTooltip>

                        {/* move */}
                        <BaseTooltip title={t('common.action.move')}>
                            <IconButton
                                sx={{ p: '6px' }}
                                disabled={disableMove}
                                onClick={handleOpenMove}
                            >
                                <Image
                                    src="/common/icon-ic-folder-move.svg"
                                    alt="move"
                                    width={20}
                                    height={20}
                                />
                            </IconButton>
                        </BaseTooltip>

                        {/* starred */}
                        {!areAllItemsStarred && (
                            <BaseTooltip title={t('common.action.addStarred')}>
                                <IconButton
                                    sx={{ p: '6px' }}
                                    onClick={() => handleMultiStarred('active')}
                                >
                                    <Image
                                        src="/common/icon-ic-star-outline.svg"
                                        alt="star-outline"
                                        width={20}
                                        height={20}
                                    />
                                </IconButton>
                            </BaseTooltip>
                        )}
                        {areAllItemsStarred && (
                            <BaseTooltip title={t('tooltip.removeStarred')}>
                                <IconButton
                                    sx={{ p: '6px' }}
                                    onClick={() => handleMultiStarred('unactive')}
                                >
                                    <Image
                                        src="/common/icon-ic-star.svg"
                                        alt="star"
                                        width={20}
                                        height={20}
                                    />
                                </IconButton>
                            </BaseTooltip>
                        )}

                        {/* move to trash */}
                        <BaseTooltip title={t('common.action.moveToTrash')}>
                            <IconButton
                                sx={{ p: '6px' }}
                                disabled={disableTrash}
                                onClick={handleOpenDeleteConfirm}
                            >
                                <Image
                                    src="/common/icon-ic-trash-outline.svg"
                                    alt="trash"
                                    width={20}
                                    height={20}
                                />
                            </IconButton>
                        </BaseTooltip>
                    </>
                )}
            </Stack>
        </Stack>
    );
};

export default DriveActionIconMenuBar;

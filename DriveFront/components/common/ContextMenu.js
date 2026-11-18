import React, { useEffect, useMemo, useRef, useState, useLayoutEffect } from 'react';
import { Divider, MenuItem, Stack, Typography } from '@mui/material';
import { useTranslations } from 'next-intl';
import { useDialog } from '@/provider/DialogProvider';
import palette from '@/theme/palette';
import { useRouter } from '@/hooks/useRouter';
import api from '@/utils/api';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { getItemExt, isIntellectFile } from '@/constants/FILES';
import { getOfficeEditUrl } from '@/apis/webOfficeApi';
import { getResourceDetail } from '@/apis/resourceApi';
import { registerRecent } from '@/apis/recentApi';
import { useResources } from '@/provider/ResourceProvider';
import checkDevice from '@/utils/etc';
import ContextSubMenu from '@/components/common/ContextSubMenu';
import { formatShortcut } from '@/utils/platform';
import {
    getResourceActionDisabled,
    hasLockedFile,
    hasSharedRootResource,
} from '@/utils/resourceUtils';
import { useDriveDownload } from '@/hooks/useDriveDownload';
import { useDrivePreview } from '@/hooks/useDrivePreview';
import { getIntellectUrl } from '@/apis/intellectApi';
import { closePendingPopup, openDocumentWindow, openPendingPopup } from '@/utils/documentWindow';
import useClientConfig from '@/hooks/useClientConfig';
import useOfficeFeature from '@/hooks/useOfficeFeature';
import useStarredToggle from '@/hooks/useStarredToggle';
import useTrashRestore from '@/hooks/useTrashRestore';

const menuStyles = {
    position: 'fixed',
    zIndex: 1199,
    minWidth: 220,
    borderRadius: '6px',
    boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
    border: `1px solid ${palette.control_border_normal}`,
    backgroundColor: palette.btn_white_bg_normal,
    padding: '8px 0',
    listStyle: 'none',
    margin: 0,
};

/** 파일 전체에서 사용되는 번역 키 상수 */
const T = {
    // resourceMenuBar 메뉴 항목
    PREVIEW: 'common.action.previewOpen',
    EDIT: 'common.action.edit',
    DOWNLOAD: 'common.action.download',
    SHARE: 'common.action.share',
    RENAME: 'resourceMenuBar.rename',
    MOVE: 'common.action.move',
    COPY: 'resourceMenuBar.copy',
    ADD_STARRED: 'common.action.addStarred',
    REMOVE_STARRED: 'resourceMenuBar.removeStarred',
    DELETE: 'common.action.moveToTrash',
    VERSION: 'resourceMenuBar.version',
    FOLDER_DETAIL: 'resourceMenuBar.folderDetail',
    FILE_DETAIL: 'resourceMenuBar.fileDetail',
    // trashMenuBar 메뉴 항목
    TRASH_RESTORE: 'common.action.restore',
    TRASH_HARD_DELETE: 'common.action.hardDelete',
    // 즐겨찾기 스낵바
    ADD_STARRED_SUCCESS: 'addToStarred.success',
    REMOVE_STARRED_SUCCESS: 'removeToStarred.success',
    // 휴지통 이동 다이얼로그 / 스낵바
    TRASH_DESCRIPTION: 'Trash.text',
    MOVE_TO_TRASH_SUCCESS: 'moveToTrash.success',
    MOVE_TO_TRASH_PARTIAL: 'moveToTrash.partial',
    MOVE_TO_TRASH_FAIL: 'moveToTrash.fail',
    // 완전 삭제 다이얼로그 / 스낵바
    HARD_DELETE_TITLE: 'hardDeleteDialog.text1',
    HARD_DELETE_DESC_SINGLE: 'hardDeleteDialog.text2Single',
    HARD_DELETE_DESC_MULTI: 'hardDeleteDialog.text2',
    HARD_DELETE_SUCCESS: 'hardDelete.success',
    HARD_DELETE_PARTIAL: 'hardDelete.partial',
    HARD_DELETE_FAIL: 'hardDelete.fail',
    // 잠금 / 편집 스낵바
    LOCKED: 'lock.locked',
    EDIT_FAIL: 'user.filePreview.toast.openDocumentFail',
    EDIT_SUCCESS: 'user.filePreview.toast.openDocumentSuccess',
    RECENT_REGISTER_FAIL: 'recent.register.fail',
};

function CustomMenuItem({ text, keyText, img, isSubMenu, ...props }) {
    const timeoutRef = useRef(null);

    const [menuState, setMenuState] = useState({
        visible: false,
        top: 0,
        left: 0,
        selectedRow: null,
    });

    const handleMenuOpen = e => {
        if (!isSubMenu) return;
        // 혹시라도 남아있는 닫기 예약을 취소합니다.
        clearTimeout(timeoutRef.current);

        const rect = e.currentTarget.getBoundingClientRect();
        setMenuState({
            visible: true,
            top: rect.top,
            left: rect.right,
        });
    };

    const handleClose = () => {
        setMenuState({ ...menuState, visible: false });
    };

    const handleContextMenu = e => {
        if (!isSubMenu) return;
        e.preventDefault();
        setMenuState({
            visible: true,
            top: e.clientY,
            left: e.clientX,
        });
    };

    const handleMenuClose = () => {
        // 200ms 뒤에 메뉴를 닫도록 예약을 겁니다.
        timeoutRef.current = setTimeout(() => {
            setMenuState(prev => ({ ...prev, visible: false }));
        }, 200);
    };

    // 메뉴 닫기 '예약'을 '취소'하는 함수 (서브 메뉴에 마우스 진입 시)
    const cancelMenuClose = () => {
        // handleMenuClose에 의해 예약된 닫기 동작을 취소합니다.
        clearTimeout(timeoutRef.current);
    };

    // 즉시 메뉴를 닫는 함수
    const closeNow = () => {
        setMenuState(prev => ({ ...prev, visible: false }));
    };

    return (
        <>
            <MenuItem
                {...props}
                sx={{ width: '100%', px: 3 }}
                onClick={e => {
                    props.onClick && props.onClick(e);
                }}
                onMouseEnter={handleMenuOpen}
                // 2. 마우스가 나가면 메뉴 닫기를 '예약'합니다.
                onMouseLeave={handleMenuClose}
            >
                <Stack
                    sx={{ width: '100%' }}
                    direction={'row'}
                    justifyContent={'space-between'}
                    alignItems={'center'}
                >
                    <Stack
                        direction={'row'}
                        justifyContent={'center'}
                        alignItems={'center'}
                        gap={2.5}
                    >
                        {/* {img ? img : <Box sx={{ width: 20 }} />} */}
                        <Typography variant={'body5'} color={'text_80'}>
                            {text}
                        </Typography>
                    </Stack>
                    <Typography variant={'body9'} color={'text_60'}>
                        {keyText}
                    </Typography>
                </Stack>
            </MenuItem>
            {menuState.visible && (
                <ContextSubMenu
                    menuState={menuState}
                    onClose={closeNow}
                    // 3. 마우스가 서브 메뉴로 들어오면 닫기 '예약'을 '취소'합니다.
                    onMouseEnter={cancelMenuClose}
                    // 4. 마우스가 서브 메뉴에서도 나가면 다시 닫기를 '예약'합니다.
                    onMouseLeave={handleMenuClose}
                />
            )}
        </>
    );
}

const renderItems = ({
    menuState,
    onClose,
    reload,
    t,
    items,
    setCheckItems,
    setDrawerOpen,
    user,
    openDialog,
    showSnackbar,
    refreshResources,
    router,
    device,
    intellectEnabled,
    officeFeature,
    shareEnabled,
    handleRestore,
    handlePreview,
    handleDownload,
    toggleStarred,
    listPatch,
}) => {
    const canEditAll = items.length > 0 && items.every(item => item.canEdit);
    const isFolder = items.findIndex(i => i.resourceType === 'FOLDER') > -1;
    const isNotOffice = items.some(i => !officeFeature.canEdit(getItemExt(i)));
    const isNotIntellect = items.findIndex(i => !isIntellectFile(getItemExt(i))) > -1;
    const areAllItemsStarred = items.every(item => item.isStarred === true);

    /** 컨텍스트 메뉴 및 아이콘 메뉴에서 '공유' 버튼 활성화 여부 판단
     * 내 드라이브거나 공유 문서함 루트에 있는 경우 -> 활성화
     */
    // // 공유 문서함 루트에 있는 경우
    // const isShared = router.asPath.includes('shared');
    // const isSharedRoot =
    //     (isShared && !router.query.pfs) || (!isShared && hasSharedRootResource(items));
    // // 내드라이브에 있는 경우
    // const isMyDrive = items[0]?.resourceLocation === 'MY';
    const isIntellect = item => isIntellectFile(getItemExt(item));

    // 공통 disabled 계산
    const hasCannotShare = items.some(i => !i?.canShare);
    const hasCannotEdit = items.some(i => !i?.canEdit);
    const hasShareSeq = items.some(i => i?.shareSeq); // shareSeq가 있다(= 공유된 리소스). 공유문서함 루트가 아니더라도, shareSeq가 있는 경우는 '공유' 메뉴 활성화 필요

    const isMulti = items.length > 1;
    const isLocked = hasLockedFile(items);

    /** 컨텍스트 메뉴 및 아이콘 메뉴에서 '공유' 버튼 활성화 여부 판단
     * 내 드라이브거나 공유 문서함 루트에 있는 경우 -> 활성화
     */
    // 공유 문서함 루트에 있는 경우
    const isShared = router.asPath.includes('shared');
    const isSharedRoot =
        (isShared && !router.query.pfs) || (!isShared && hasSharedRootResource(items));
    // 내드라이브에 있는 경우
    const isMyDrive = items[0]?.resourceLocation === 'MY';

    const { disablePreview, disableDownload, disableRename, disableDelete, disableCopy } =
        getResourceActionDisabled(items);
    const disableMove = hasCannotEdit || isLocked || isSharedRoot;

    // 공유
    // home/drive/recent/starred/search: "내 드라이브 OR 공유문서함 루트"에서만 가능
    // + 공유문서함 루트는 아닌데, shareSeq가 있는 경우(= 공유된 리소스)
    const disableShare_driveLike =
        hasCannotShare || !(isMyDrive || isSharedRoot || (!isSharedRoot && hasShareSeq));
    // shared: 루트에서만 가능 + canShare 하나라도 없으면 불가
    const disableShare_shared =
        hasCannotShare || !(isMyDrive || isSharedRoot || (!isSharedRoot && hasShareSeq));

    // 스낵바/목록 갱신은 useStarredToggle이 항목 전체를 처리한 뒤 한 번만 수행한다.
    // (기존에는 루프 안에 있어 다중 선택 시 항목 수만큼 반복됐음)
    // 선택은 해제하지 않는다 — 행 액션 아이콘 경로와 동일하게 유지하고,
    // 낡은 checkItems는 페이지의 useSyncedSelection이 새 목록 기준으로 다시 맞춘다.
    const handleStarred = async () => {
        await toggleStarred(items);
        onClose();
    };

    const handleDeleteDrive = async () => {
        let successCount = 0;
        let failCount = 0;
        let lockedCount = 0;

        openDialog('alert', {
            title: t(T.DELETE),
            description: t(T.TRASH_DESCRIPTION),
            onClick: async () => {
                // 성공한 항목만 목록에서 제거하기 위해 따로 모은다
                const deletedSeqs = [];

                for (let item of items) {
                    const res = await (item?.resourceType === 'FOLDER'
                        ? api.Patch(`folders/${item?.resourceSeq}/delete`, {
                              ss: item?.sharedByUserSeq || item?.userSeq || undefined,
                          })
                        : api.Patch(`files/${item?.resourceSeq}/delete`, {
                              ss: item?.sharedByUserSeq || item?.userSeq || undefined,
                          }));

                    if (res?.status === 200) {
                        successCount++;
                        deletedSeqs.push(item?.resourceSeq);
                    } else if (res?.status === 423) {
                        lockedCount++;
                        failCount++;
                    } else {
                        failCount++;
                    }
                }

                if (successCount > 0 && failCount === 0) {
                    showSnackbar(t(T.MOVE_TO_TRASH_SUCCESS), 'success');
                } else if (successCount > 0 && failCount > 0) {
                    showSnackbar(
                        t(T.MOVE_TO_TRASH_PARTIAL, {
                            success: successCount,
                            fail: failCount,
                            locked: lockedCount,
                        }),
                        'warning'
                    ); // 일부 성공/실패
                } else {
                    if (lockedCount > 0) {
                        showSnackbar(t(T.LOCKED), 'error');
                    } else {
                        showSnackbar(t(T.MOVE_TO_TRASH_FAIL), 'error');
                    }
                }

                setCheckItems([]);
                // 휴지통으로 옮겨진 행만 목록에서 제거한다(스크롤 위치 유지)
                if (listPatch?.remove) listPatch.remove(deletedSeqs);
                else await reload(true);
                await refreshResources();
            },
        });
        onClose();
    };

    const handleCompletelyDeleteDrive = async () => {
        let successCount = 0;
        let failCount = 0;

        openDialog('alert', {
            title: t(T.HARD_DELETE_TITLE),
            description:
                items.length === 1
                    ? t(T.HARD_DELETE_DESC_SINGLE, { resourceName: items[0]?.resourceName })
                    : items.length > 1 && t(T.HARD_DELETE_DESC_MULTI, { count: items.length }),
            onClick: async () => {
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
                    showSnackbar(t(T.HARD_DELETE_SUCCESS), 'success');
                } else if (successCount > 0 && failCount > 0) {
                    showSnackbar(
                        t(T.HARD_DELETE_PARTIAL, {
                            success: successCount,
                            fail: failCount,
                        }),
                        'warning'
                    ); // 일부 성공/실패
                } else {
                    showSnackbar(t(T.HARD_DELETE_FAIL), 'error');
                }

                setCheckItems([]);
                // 영구삭제된 행만 목록에서 제거한다
                if (listPatch?.remove) listPatch.remove(deletedSeqs);
                else await reload(true);
            },
        });
        onClose();
    };

    const fetchPreviewResource = async previewItem => {
        try {
            return await getResourceDetail(previewItem?.resourceSeq, {
                ss: previewItem?.sharedByUserSeq || previewItem?.userSeq,
            });
        } catch (e) {
            console.error('Failed to fetch resource detail', e);
        }
    };

    const handleEdit = async () => {
        const item = items?.[0];
        const popup = openPendingPopup();

        const previewResource = await fetchPreviewResource(item);
        if (!previewResource) {
            closePendingPopup(popup);
            return;
        }

        // tfi 파일이면서 locked인 경우 '편집' 불가능
        if (isIntellect(previewResource) && previewResource.isLocked) {
            closePendingPopup(popup);
            showSnackbar(t(T.LOCKED), 'error');
            reload(true);
            onClose();
            return;
        }
        let editUrl = isIntellect(previewResource)
            ? await getIntellectUrl(previewResource, user)
            : await getOfficeEditUrl(previewResource, user);
        if (!editUrl) {
            closePendingPopup(popup);
            showSnackbar(t(T.EDIT_FAIL), 'error');
            return;
        }
        openDocumentWindow(editUrl, popup);

        const registered = await registerRecent(
            previewResource.resourceSeq,
            previewResource.sharedByUserSeq || previewResource.userSeq
        );
        if (!registered) {
            showSnackbar(t(T.RECENT_REGISTER_FAIL), 'error');
        }
        showSnackbar(t(T.EDIT_SUCCESS), 'success');

        onClose();
    };

    const handleFileCopy = async () => {
        openDialog('fileCopy', {
            item: items,
            setCheckItems,
            reload,
        });
        onClose();
    };

    const handleShare = async () => {
        openDialog('fileShare', {
            item: items?.[0],
            setCheckItems,
            reload,
            listPatch,
        });
        onClose();
    };

    const handleRename = async () => {
        openDialog('fileName', {
            item: items?.[0],
            setCheckItems,
            reload,
            listPatch,
        });
        onClose();
    };

    const handleFileMove = async () => {
        openDialog('fileMove', {
            items: items,
            setCheckItems,
            reload,
            listPatch,
        });
        onClose();
    };

    const handleShowDetail = async () => {
        setDrawerOpen(true);
        onClose();
    };

    const handleOpenVersionDialog = async () => {
        openDialog('version', {
            item: items[0],
            reload,
            listPatch,
        });
        onClose();
    };

    // const { handlePreview } = useDrivePreview({ t, handleRestore, onClose });
    // const { handleDownload } = useDriveDownload({ onClose, t });

    switch (router.pathname) {
        case '/home':
        case '/drive':
        case '/recent':
        case '/starred':
        case '/search':
            return (
                <>
                    {items.length === 1 ? (
                        <>
                            <CustomMenuItem
                                // img={
                                //     <svg width="20" height="20" viewBox="0 0 20 20" fill="none"
                                //          xmlns="http://www.w3.org/2000/svg">
                                //         <path
                                //             d="M4.167 16.042h1.05l8.531-8.53-1.051-1.052-8.53 8.53v1.052zm-.497 1.25a.729.729 0 0 1-.537-.217.728.728 0 0 1-.216-.537v-1.443a1.5 1.5 0 0 1 .44-1.063L13.908 3.484c.126-.114.265-.203.417-.265a1.25 1.25 0 0 1 .48-.094c.166 0 .328.03.485.089.156.06.295.154.416.283l1.017 1.03c.13.121.222.26.277.417.055.157.082.314.082.471 0 .168-.028.328-.085.48a1.181 1.181 0 0 1-.274.418L6.176 16.852a1.5 1.5 0 0 1-1.062.439H3.67zm9.543-10.297-.516-.535 1.051 1.051-.534-.516z"
                                //             fill="#3A3D49" opacity=".8"/>
                                //     </svg>
                                // }
                                text={t(T.PREVIEW)}
                                keyText={`${formatShortcut('Enter')}`}
                                onClick={() => handlePreview(items[0])}
                                disabled={disablePreview}
                            />
                            {!isFolder &&
                                canEditAll &&
                                (!isNotOffice || !isNotIntellect) &&
                                !device.isPhone && (
                                    <CustomMenuItem
                                        text={t(T.EDIT)}
                                        // keyText={'⌘X'}
                                        onClick={handleEdit}
                                        disabled={
                                            !canEditAll ||
                                            (isIntellect(items[0]) && hasLockedFile(items)) ||
                                            (isIntellect(items[0]) && !intellectEnabled)
                                        }
                                    />
                                )}
                            <CustomMenuItem
                                text={t(T.DOWNLOAD)}
                                // keyText={'⌘X'}
                                onClick={() => handleDownload(items)}
                                disabled={disableDownload}
                            />
                            {shareEnabled && (
                                <CustomMenuItem
                                    text={t(T.SHARE)}
                                    onClick={handleShare}
                                    // recent/search/starred: "내 드라이브 or 공유문서함 루트" 조건, "공유문서함 루트"이면서 canShare false인 경우 추가
                                    disabled={disableShare_driveLike}
                                />
                            )}
                            <Divider
                                style={{
                                    margin: '4px 0',
                                    borderColor: palette.divider_8,
                                    borderBottomWidth: 0.5,
                                }}
                            />
                            <CustomMenuItem
                                text={t(T.RENAME)}
                                keyText={`${formatShortcut('F2')}`}
                                onClick={handleRename}
                                disabled={disableRename}
                            />
                            <CustomMenuItem
                                text={t(T.MOVE)}
                                onClick={handleFileMove}
                                disabled={disableMove}
                            />
                            {!isFolder && (
                                <CustomMenuItem
                                    text={t(T.COPY)}
                                    keyText={`${formatShortcut('mod+c')} ${formatShortcut('mod+v')}`}
                                    onClick={handleFileCopy}
                                    disabled={disableCopy}
                                />
                            )}
                            {/* <CustomMenuItem
                                    img={
                                        <svg width="20" height="20" viewBox="0 0 20 20" fill="none"
                                             xmlns="http://www.w3.org/2000/svg">
                                            <path
                                                d="M4.167 16.042h1.05l8.531-8.53-1.051-1.052-8.53 8.53v1.052zm-.497 1.25a.729.729 0 0 1-.537-.217.728.728 0 0 1-.216-.537v-1.443a1.5 1.5 0 0 1 .44-1.063L13.908 3.484c.126-.114.265-.203.417-.265a1.25 1.25 0 0 1 .48-.094c.166 0 .328.03.485.089.156.06.295.154.416.283l1.017 1.03c.13.121.222.26.277.417.055.157.082.314.082.471 0 .168-.028.328-.085.48a1.181 1.181 0 0 1-.274.418L6.176 16.852a1.5 1.5 0 0 1-1.062.439H3.67zm9.543-10.297-.516-.535 1.051 1.051-.534-.516z"
                                                fill="#3A3D49" opacity=".8"/>
                                        </svg>
                                    }
                                    text={'test'}
                                    isSubMenu={true}
                                    keyText={<ArrowFiledRight opacity={'.8'}/>}
                                /> */}
                            <CustomMenuItem
                                text={
                                    items[0]?.isStarred === true ||
                                    router.pathname.includes('/starred')
                                        ? t(T.REMOVE_STARRED)
                                        : t(T.ADD_STARRED)
                                }
                                onClick={handleStarred}
                            />
                            <CustomMenuItem
                                text={t(T.DELETE)}
                                keyText={`${formatShortcut('Delete')}`}
                                onClick={handleDeleteDrive}
                                disabled={disableDelete}
                            />
                            <Divider
                                style={{
                                    margin: '4px 0',
                                    borderColor: palette.divider_8,
                                    borderBottomWidth: 0.5,
                                }}
                            />
                            <CustomMenuItem
                                text={t(isFolder ? T.FOLDER_DETAIL : T.FILE_DETAIL)}
                                onClick={handleShowDetail}
                                disabled={disablePreview}
                            />
                            {!isFolder && (
                                <CustomMenuItem
                                    text={t(T.VERSION)}
                                    onClick={handleOpenVersionDialog}
                                    disabled={disablePreview}
                                />
                            )}
                        </>
                    ) : (
                        <>
                            <CustomMenuItem
                                text={t(T.DOWNLOAD)}
                                onClick={() => handleDownload(items)}
                                disabled={disableDownload}
                            />
                            <CustomMenuItem
                                text={t(T.MOVE)}
                                onClick={handleFileMove}
                                disabled={disableMove}
                            />
                            <CustomMenuItem
                                text={
                                    areAllItemsStarred || router.pathname.includes('/starred')
                                        ? t(T.REMOVE_STARRED)
                                        : t(T.ADD_STARRED)
                                }
                                onClick={handleStarred}
                            />
                            <CustomMenuItem
                                text={t(T.DELETE)}
                                keyText={`${formatShortcut('Delete')}`}
                                onClick={handleDeleteDrive}
                                disabled={disableDelete}
                            />
                        </>
                    )}
                </>
            );
        case '/shared':
            return items.length === 1 ? (
                <>
                    <CustomMenuItem
                        img={
                            <svg
                                width="20"
                                height="20"
                                viewBox="0 0 20 20"
                                fill="none"
                                xmlns="http://www.w3.org/2000/svg"
                            >
                                <path
                                    d="M4.167 16.042h1.05l8.531-8.53-1.051-1.052-8.53 8.53v1.052zm-.497 1.25a.729.729 0 0 1-.537-.217.728.728 0 0 1-.216-.537v-1.443a1.5 1.5 0 0 1 .44-1.063L13.908 3.484c.126-.114.265-.203.417-.265a1.25 1.25 0 0 1 .48-.094c.166 0 .328.03.485.089.156.06.295.154.416.283l1.017 1.03c.13.121.222.26.277.417.055.157.082.314.082.471 0 .168-.028.328-.085.48a1.181 1.181 0 0 1-.274.418L6.176 16.852a1.5 1.5 0 0 1-1.062.439H3.67zm9.543-10.297-.516-.535 1.051 1.051-.534-.516z"
                                    fill="#3A3D49"
                                    opacity=".8"
                                />
                            </svg>
                        }
                        text={t(T.PREVIEW)}
                        keyText={`${formatShortcut('Enter')}`}
                        onClick={() => handlePreview(items[0])}
                        disabled={disablePreview}
                    />
                    {!isFolder &&
                        canEditAll &&
                        (!isNotOffice || !isNotIntellect) &&
                        !device.isPhone && (
                            <CustomMenuItem
                                text={t(T.EDIT)}
                                // keyText={'⌘X'}
                                onClick={handleEdit}
                                disabled={
                                    !canEditAll ||
                                    (isIntellect(items[0]) && hasLockedFile(items)) ||
                                    (isIntellect(items[0]) && !intellectEnabled)
                                }
                            />
                        )}
                    <CustomMenuItem
                        text={t(T.DOWNLOAD)}
                        // keyText={'⌘X'}
                        onClick={() => handleDownload(items)}
                        disabled={disableDownload}
                    />
                    {shareEnabled && (
                        <CustomMenuItem
                            text={t(T.SHARE)}
                            onClick={handleShare}
                            disabled={disableShare_shared}
                        />
                    )}
                    <Divider
                        style={{
                            margin: '4px 0',
                            borderColor: palette.divider_8,
                            borderBottomWidth: 0.5,
                        }}
                    />
                    <CustomMenuItem
                        text={t(T.RENAME)}
                        keyText={`${formatShortcut('F2')}`}
                        onClick={handleRename}
                        disabled={disableRename}
                    />
                    <CustomMenuItem
                        text={t(T.MOVE)}
                        onClick={handleFileMove}
                        disabled={disableMove || items.some(i => i.fullPath === null)}
                    />
                    {!isFolder && (
                        <CustomMenuItem
                            text={t(T.COPY)}
                            keyText={`${formatShortcut('mod+c')} ${formatShortcut('mod+v')}`}
                            onClick={handleFileCopy}
                            disabled={disableCopy || items.some(i => i.resourceType === 'FOLDER')}
                        />
                    )}
                    <CustomMenuItem
                        text={
                            items[0]?.isStarred || router.pathname.includes('/starred')
                                ? t(T.REMOVE_STARRED)
                                : t(T.ADD_STARRED)
                        }
                        onClick={handleStarred}
                    />
                    <CustomMenuItem
                        text={t(T.DELETE)}
                        keyText={`${formatShortcut('Delete')}`}
                        onClick={handleDeleteDrive}
                        disabled={disableDelete}
                    />
                    <Divider
                        style={{
                            margin: '4px 0',
                            borderColor: palette.divider_8,
                            borderBottomWidth: 0.5,
                        }}
                    />
                    <CustomMenuItem
                        text={t(isFolder ? T.FOLDER_DETAIL : T.FILE_DETAIL)}
                        onClick={handleShowDetail}
                        disabled={disablePreview}
                    />
                    {!isFolder && (
                        <CustomMenuItem
                            text={t(T.VERSION)}
                            onClick={handleOpenVersionDialog}
                            disabled={disablePreview}
                        />
                    )}
                </>
            ) : (
                <>
                    <CustomMenuItem
                        text={t(T.DOWNLOAD)}
                        // keyText={'⌘X'}
                        onClick={() => handleDownload(items)}
                        disabled={disableDownload}
                    />
                    <CustomMenuItem
                        text={t(T.MOVE)}
                        onClick={handleFileMove}
                        disabled={disableMove || items.some(i => i.fullPath === null)}
                    />
                    <CustomMenuItem
                        text={
                            areAllItemsStarred || router.pathname.includes('/starred')
                                ? t(T.REMOVE_STARRED)
                                : t(T.ADD_STARRED)
                        }
                        onClick={handleStarred}
                    />
                    <CustomMenuItem
                        text={t(T.DELETE)}
                        keyText={`${formatShortcut('Delete')}`}
                        onClick={handleDeleteDrive}
                        disabled={disableDelete}
                    />
                </>
            );
        case '/trash':
            return items.length === 1 ? (
                <>
                    <CustomMenuItem
                        img={
                            <svg
                                width="20"
                                height="20"
                                viewBox="0 0 20 20"
                                fill="none"
                                xmlns="http://www.w3.org/2000/svg"
                            >
                                <path
                                    d="M4.167 16.042h1.05l8.531-8.53-1.051-1.052-8.53 8.53v1.052zm-.497 1.25a.729.729 0 0 1-.537-.217.728.728 0 0 1-.216-.537v-1.443a1.5 1.5 0 0 1 .44-1.063L13.908 3.484c.126-.114.265-.203.417-.265a1.25 1.25 0 0 1 .48-.094c.166 0 .328.03.485.089.156.06.295.154.416.283l1.017 1.03c.13.121.222.26.277.417.055.157.082.314.082.471 0 .168-.028.328-.085.48a1.181 1.181 0 0 1-.274.418L6.176 16.852a1.5 1.5 0 0 1-1.062.439H3.67zm9.543-10.297-.516-.535 1.051 1.051-.534-.516z"
                                    fill="#3A3D49"
                                    opacity=".8"
                                />
                            </svg>
                        }
                        text={t(T.PREVIEW)}
                        keyText={`${formatShortcut('Enter')}`}
                        onClick={() => handlePreview(items[0])}
                    />
                    <CustomMenuItem
                        text={t(T.DOWNLOAD)}
                        // keyText={'⌘X'}
                        onClick={() => handleDownload(items)}
                    />

                    <CustomMenuItem text={t(T.TRASH_RESTORE)} onClick={handleRestore} />
                    <CustomMenuItem
                        text={t(T.TRASH_HARD_DELETE)}
                        keyText={`${formatShortcut('Delete')}`}
                        onClick={handleCompletelyDeleteDrive}
                    />
                    <Divider
                        style={{
                            margin: '4px 0',
                            borderColor: palette.divider_8,
                            borderBottomWidth: 0.5,
                        }}
                    />
                    <CustomMenuItem
                        text={t(isFolder ? T.FOLDER_DETAIL : T.FILE_DETAIL)}
                        onClick={handleShowDetail}
                    />
                </>
            ) : (
                <>
                    <CustomMenuItem text={t(T.TRASH_RESTORE)} onClick={handleRestore} />
                    <CustomMenuItem
                        text={t(T.TRASH_HARD_DELETE)}
                        onClick={handleCompletelyDeleteDrive}
                    />
                </>
            );
    }
};

const StyledContextMenu = ({
    menuState,
    onClose,
    reload,
    checkItems,
    setCheckItems,
    setDrawerOpen,
    user,
    patchItem,
    removeItem,
    listPatch,
}) => {
    const menuRef = useRef(null);
    const t = useTranslations();
    const [position, setPosition] = useState({ top: menuState.top, left: menuState.left });

    const { openDialog } = useDialog();
    const { showSnackbar } = useSnackbarContext();
    const { refreshResources } = useResources();
    const router = useRouter();
    const device = checkDevice();
    const { intellectEnabled } = useClientConfig();
    const officeFeature = useOfficeFeature();
    const { shareEnabled } = useClientConfig();

    const { restoreItems } = useTrashRestore({
        onRestored: async seqs => {
            // 복원된 행만 목록에서 제거한다(전체 재조회 시 스크롤 위치가 초기화됨)
            if (listPatch?.remove) listPatch.remove(seqs);
            else await reload(true);
            setCheckItems([]);
            refreshResources();
        },
    });

    const handleRestore = async () => {
        await restoreItems(items);
        onClose();
    };

    const { handlePreview } = useDrivePreview({ t, handleRestore, onClose });
    const { handleDownload } = useDriveDownload({ onClose, t });
    const { toggleStarred } = useStarredToggle({ patchItem, removeItem, reload });

    const items = useMemo(() => {
        return checkItems?.length > 1 ? checkItems : [menuState.selectedRow];
    }, [menuState.selectedRow, checkItems]);

    // 메뉴 위치 보정
    useLayoutEffect(() => {
        if (!menuRef.current) return;

        const { offsetHeight, offsetWidth } = menuRef.current;
        const { innerHeight, innerWidth, e } = window;

        let top = menuState.top;
        let left = menuState.left;

        if (window.event.target.id === 'dots') {
            console.log('window', window, window.event.target);
            const rect = window.event.target.getBoundingClientRect();
            top = rect.bottom + 12;
        } else if (window.event.target.id === 'dots-button') {
            const rect = window.event.target.getBoundingClientRect();
            top = rect.bottom + 6;
        }

        if (top + offsetHeight > innerHeight) {
            top = innerHeight - offsetHeight - 10;
        }

        if (left + offsetWidth > innerWidth) {
            left = innerWidth - offsetWidth - 100;
        }

        setPosition({ top, left });
    }, [menuState.top, menuState.left, menuState.visible]);

    useEffect(() => {
        const handleClickOutside = event => {
            if (menuRef.current && !menuRef.current.contains(event.target)) {
                onClose();
            }
        };

        if (menuState.visible) {
            document.addEventListener('mousedown', handleClickOutside);
            document.addEventListener('touchstart', handleClickOutside);
        }

        return () => {
            document.removeEventListener('mousedown', handleClickOutside);
            document.removeEventListener('touchstart', handleClickOutside);
        };
    }, [menuState.visible, onClose]);

    if (!menuState.visible) {
        return null;
    }

    return (
        <ul
            ref={menuRef}
            style={{
                ...menuStyles,
                zIndex: 1202,
                top: position.top,
                left: position.left,
            }}
        >
            {renderItems({
                menuState,
                onClose,
                reload,
                t,
                items,
                setCheckItems,
                setDrawerOpen,
                user,
                openDialog,
                showSnackbar,
                refreshResources,
                router,
                device,
                intellectEnabled,
                officeFeature,
                shareEnabled,
                handleRestore,
                handlePreview,
                handleDownload,
                toggleStarred,
                listPatch,
            })}
        </ul>
    );
};

export default StyledContextMenu;

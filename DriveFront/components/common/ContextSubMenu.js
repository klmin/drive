import React, { useEffect, useMemo, useRef, useState, useLayoutEffect } from 'react';
import { Box, Divider, ListItemText, Menu, MenuItem, Stack, Typography } from '@mui/material';
import { useTranslations } from 'next-intl';
import { useDialog } from '@/provider/DialogProvider';
import palette from '@/theme/palette';
import { useRouter } from '@/hooks/useRouter';
import api from '@/utils/api';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { getItemExt, isIntellectFile } from '@/constants/FILES';
import { getOfficeEditUrl } from '@/apis/webOfficeApi';
import { closePendingPopup, openDocumentWindow, openPendingPopup } from '@/utils/documentWindow';
import { registerRecent } from '@/apis/recentApi';
import { ArrowFiledRight } from '@/icon/Arrow';
import { useResources } from '@/provider/ResourceProvider';
import checkDevice from '@/utils/etc';
import { useDriveDownload } from '@/hooks/useDriveDownload';
import { useDrivePreview } from '@/hooks/useDrivePreview';
import useClientConfig from '@/hooks/useClientConfig';
import useOfficeFeature from '@/hooks/useOfficeFeature';
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
    MOVE_TO_TRASH_FAIL: 'moveToTrash.fail',
    // 완전 삭제 다이얼로그 / 스낵바
    HARD_DELETE_TITLE: 'hardDeleteDialog.text1',
    HARD_DELETE_DESC_SINGLE: 'hardDeleteDialog.text2Single',
    HARD_DELETE_DESC_MULTI: 'hardDeleteDialog.text2Multiple',
    DELETE_SUCCESS: 'delete.success',
    // 잠금 / 편집 스낵바
    LOCKED: 'lock.locked',
    EDIT_FAIL: 'user.filePreview.toast.openDocumentFail',
    RECENT_REGISTER_SUCCESS: 'recent.register.success',
    RECENT_REGISTER_FAIL: 'recent.register.fail',
};

function CustomMenuItem({ text, keyText, img, isSubMenu, ...props }) {
    const [anchorEl, setAnchorEl] = useState(null);
    const timeoutRef = useRef(null);

    const handleMenuOpen = event => {
        clearTimeout(timeoutRef.current);
        if (isSubMenu) {
            setAnchorEl(event.currentTarget);
        }
    };

    const handleMenuClose = () => {
        timeoutRef.current = setTimeout(() => {
            setAnchorEl(null);
        }, 100);
    };

    const handleSubMenuEnter = () => {
        clearTimeout(timeoutRef.current);
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
                        {img ? img : <Box sx={{ width: 20 }} />}
                        <Typography variant={'body5'} color={'text_80'}>
                            {text}
                        </Typography>
                    </Stack>
                    <Typography variant={'body9'} color={'text_60'}>
                        {keyText}
                    </Typography>
                </Stack>
            </MenuItem>
            <Menu
                anchorEl={anchorEl}
                open={Boolean(anchorEl)}
                onClose={() => setAnchorEl(null)}
                PaperProps={{
                    sx: {
                        ml: 1,
                        borderRadius: '6px',
                        boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
                        border: `1px solid ${palette.control_border_normal}`,
                        minWidth: 220,
                    },
                    onMouseEnter: handleSubMenuEnter,
                    onMouseLeave: handleMenuClose,
                }}
                anchorOrigin={{
                    vertical: 'top',
                    horizontal: 'right',
                }}
                transformOrigin={{
                    vertical: 'top',
                    horizontal: 'left',
                }}
                MenuListProps={{
                    'aria-labelledby': 'basic-button',
                }}
            >
                <MenuItem>test</MenuItem>
                <MenuItem>test</MenuItem>
            </Menu>
        </>
    );
}

const ContextMenuItems = ({
    menuState,
    onClose,
    reload,
    t,
    items,
    setCheckItems,
    setDrawerOpen,
    user,
}) => {
    const { openDialog } = useDialog();
    const { showSnackbar } = useSnackbarContext();
    const { refreshResources } = useResources();
    const device = checkDevice();
    const { intellectEnabled } = useClientConfig();
    const officeFeature = useOfficeFeature();

    const router = useRouter();
    const canDeleteAll = items.length > 0 && items.every(item => item.canDelete);
    const canDownloadAll = items.length > 0 && items.every(item => item.canDownload);
    const canEditAll = items.length > 0 && items.every(item => item.canEdit) && !device.isPhone;
    const canShareAll = items.length > 0 && items.every(item => item.canShare);
    const canUploadAll = items.length > 0 && items.every(item => item.canUpload);
    const canViewAll = items.length > 0 && items.every(item => item.canView);
    const isFolder = items.findIndex(i => i.resourceType === 'FOLDER') > -1;
    const isNotOffice = items.some(i => !officeFeature.canEdit(getItemExt(i)));
    const isNotIntellect = items.findIndex(i => !isIntellectFile(getItemExt(i))) > -1;
    const areAllItemsStarred = items.every(item => item.starredSeq !== null);

    const handleStarred = async () => {
        const isAddStarred = areAllItemsStarred || router.pathname.includes('starred');
        for (let item of items) {
            if (isAddStarred) {
                await api.Delete(`starred/${item?.starredSeq}`, {});
            } else {
                console.info(item?.isStarred);
                if (item?.isStarred) {
                    continue;
                }
                await api.Post(`starred`, {
                    ss: item?.sharedByUserSeq || item?.userSeq || undefined,
                    rs: item?.resourceSeq,
                });
            }

            showSnackbar(
                isAddStarred ? t(T.REMOVE_STARRED_SUCCESS) : t(T.ADD_STARRED_SUCCESS),
                'success'
            );
            setCheckItems([]);
            reload(true);
        }
        onClose();
    };

    const { restoreItems } = useTrashRestore({
        onRestored: async () => {
            await reload(true);
            setCheckItems([]);
            refreshResources();
        },
    });

    const handleRestore = async () => {
        await restoreItems(items);
        onClose();
    };

    const handleDeleteDrive = async () => {
        openDialog('alert', {
            title: t(T.DELETE),
            description: t(T.TRASH_DESCRIPTION),
            onClick: async () => {
                for (let item of items) {
                    const res = await (item?.resourceType === 'FOLDER'
                        ? api.Patch(`folders/${item?.resourceSeq}/delete`, {
                              ss: item?.sharedByUserSeq || item?.userSeq || undefined,
                          })
                        : api.Patch(`files/${item?.resourceSeq}/delete`, {
                              ss: item?.sharedByUserSeq || item?.userSeq || undefined,
                          }));

                    if (res?.status === 200) {
                        showSnackbar(t(T.MOVE_TO_TRASH_SUCCESS), 'success');
                    } else if (res?.status === 423) {
                        showSnackbar(t(T.LOCKED), 'error');
                    } else {
                        showSnackbar(t(T.MOVE_TO_TRASH_FAIL), 'error');
                    }
                }

                setCheckItems([]);
                await reload(true);
                await refreshResources();
            },
        });
        onClose();
    };

    const handleCompletelyDeleteDrive = async () => {
        openDialog('alert', {
            title: t(T.HARD_DELETE_TITLE),
            description:
                items.length === 1
                    ? t(T.HARD_DELETE_DESC_SINGLE, { resourceName: items[0]?.resourceName })
                    : items.length > 1 && t(T.HARD_DELETE_DESC_MULTI, { count: items.length }),
            onClick: async () => {
                for (let item of items) {
                    await (item?.resourceType === 'FOLDER'
                        ? api.Delete(`trash/folders/${item?.resourceSeq}`, {})
                        : api.Delete(`trash/files/${item?.resourceSeq}`, {}));
                }
                showSnackbar(t(T.DELETE_SUCCESS), 'success');
                setCheckItems([]);
                await reload(true);
            },
        });
        onClose();
    };

    const handleEdit = async () => {
        const item = items?.[0];

        if (item.isLocked) {
            showSnackbar(t(T.LOCKED), 'error');
            return;
        }

        const popup = openPendingPopup();
        const editUrl = await getOfficeEditUrl(item, user);
        if (!editUrl) {
            closePendingPopup(popup);
            showSnackbar(t(T.EDIT_FAIL), 'error');
            return;
        }
        openDocumentWindow(editUrl, popup);

        const registered = await registerRecent(
            item.resourceSeq,
            item.sharedByUserSeq || item.userSeq
        );
        if (registered) {
            showSnackbar(t(T.RECENT_REGISTER_SUCCESS), 'success');
        } else {
            showSnackbar(t(T.RECENT_REGISTER_FAIL), 'error');
        }
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
        });
        onClose();
    };

    const handleRename = async () => {
        openDialog('fileName', {
            item: items?.[0],
            setCheckItems,
            reload,
        });
        onClose();
    };

    const handleFileMove = async () => {
        openDialog('fileMove', {
            item: items,
            setCheckItems,
            reload,
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
        });
        onClose();
    };

    const { handlePreview } = useDrivePreview({ t, handleRestore });
    const { handleDownload } = useDriveDownload({ items, onClose, t });

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
                                keyText={'⌘X'}
                                onClick={() => handlePreview(items[0])}
                                disabled={!canViewAll}
                            />
                            {!isFolder &&
                                canEditAll &&
                                (!isNotOffice || (!isNotIntellect && intellectEnabled)) &&
                                !device.isPhone && (
                                    <CustomMenuItem
                                        text={t(T.EDIT)}
                                        keyText={'⌘X'}
                                        onClick={handleEdit}
                                        disabled={!canEditAll}
                                    />
                                )}
                            <CustomMenuItem
                                text={t(T.DOWNLOAD)}
                                keyText={'⌘X'}
                                onClick={() => handleDownload(items)}
                                disabled={!canDownloadAll}
                            />
                            <CustomMenuItem
                                text={t(T.SHARE)}
                                onClick={handleShare}
                                disabled={!canShareAll}
                            />
                            <Divider
                                style={{
                                    margin: '4px 0',
                                    borderColor: palette.divider_8,
                                    borderBottomWidth: 0.5,
                                }}
                            />
                            <CustomMenuItem
                                text={t(T.RENAME)}
                                onClick={handleRename}
                                disabled={!canEditAll}
                            />
                            <CustomMenuItem
                                text={t(T.MOVE)}
                                onClick={handleFileMove}
                                disabled={!canEditAll}
                            />
                            {!isFolder && (
                                <CustomMenuItem
                                    text={t(T.COPY)}
                                    onClick={handleFileCopy}
                                    disabled={!canEditAll}
                                />
                            )}
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
                                text={'test'}
                                isSubMenu={true}
                                keyText={<ArrowFiledRight opacity={'.8'} />}
                            />
                            <CustomMenuItem
                                text={
                                    items[0]?.starredSeq !== null ||
                                    router.pathname.includes('/starred')
                                        ? t(T.REMOVE_STARRED)
                                        : t(T.ADD_STARRED)
                                }
                                onClick={handleStarred}
                                disabled={!canEditAll}
                            />
                            <CustomMenuItem
                                text={t(T.DELETE)}
                                onClick={handleDeleteDrive}
                                disabled={!canDeleteAll}
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
                                disabled={!canViewAll}
                            />
                            {!isFolder && (
                                <CustomMenuItem
                                    text={t(T.VERSION)}
                                    onClick={handleOpenVersionDialog}
                                    disabled={!canViewAll}
                                />
                            )}
                        </>
                    ) : (
                        <>
                            <CustomMenuItem
                                text={t(T.DOWNLOAD)}
                                keyText={'⌘X'}
                                onClick={() => handleDownload(items)}
                                disabled={!canDownloadAll}
                            />
                            <CustomMenuItem
                                text={t(T.MOVE)}
                                onClick={handleFileMove}
                                disabled={!canEditAll}
                            />
                            <CustomMenuItem
                                text={
                                    areAllItemsStarred || router.pathname.includes('/starred')
                                        ? t(T.REMOVE_STARRED)
                                        : t(T.ADD_STARRED)
                                }
                                onClick={handleStarred}
                                disabled={!canEditAll}
                            />
                            <CustomMenuItem
                                text={t(T.DELETE)}
                                onClick={handleDeleteDrive}
                                disabled={!canDeleteAll}
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
                        keyText={'⌘X'}
                        onClick={() => handlePreview(items[0])}
                        disabled={!canViewAll}
                    />
                    {!isFolder &&
                        canEditAll &&
                        (!isNotOffice || (!isNotIntellect && intellectEnabled)) &&
                        !device.isPhone && (
                            <CustomMenuItem
                                text={t(T.EDIT)}
                                keyText={'⌘X'}
                                onClick={handleEdit}
                                disabled={!canEditAll}
                            />
                        )}
                    <CustomMenuItem
                        text={t(T.DOWNLOAD)}
                        keyText={'⌘X'}
                        onClick={() => handleDownload(items)}
                        disabled={!canDownloadAll}
                    />
                    <CustomMenuItem
                        text={t(T.SHARE)}
                        onClick={handleShare}
                        disabled={!canShareAll}
                    />
                    <Divider
                        style={{
                            margin: '4px 0',
                            borderColor: palette.divider_8,
                            borderBottomWidth: 0.5,
                        }}
                    />
                    <CustomMenuItem
                        text={t(T.RENAME)}
                        onClick={handleRename}
                        disabled={!canEditAll}
                    />
                    <CustomMenuItem
                        text={t(T.MOVE)}
                        onClick={handleFileMove}
                        disabled={!canEditAll}
                    />
                    {!isFolder && (
                        <CustomMenuItem
                            text={t(T.COPY)}
                            onClick={handleFileCopy}
                            disabled={!canEditAll || items.find(i => i.resourceType === 'FOLDER')}
                        />
                    )}
                    <CustomMenuItem
                        text={
                            items[0]?.isStarred || router.pathname.includes('/starred')
                                ? t(T.REMOVE_STARRED)
                                : t(T.ADD_STARRED)
                        }
                        onClick={handleStarred}
                        disabled={!canEditAll}
                    />
                    <CustomMenuItem
                        text={t(T.DELETE)}
                        onClick={handleDeleteDrive}
                        disabled={!canDeleteAll}
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
                        disabled={!canViewAll}
                    />
                    {!isFolder && (
                        <CustomMenuItem
                            text={t(T.VERSION)}
                            onClick={handleOpenVersionDialog}
                            disabled={!canViewAll}
                        />
                    )}
                </>
            ) : (
                <>
                    <CustomMenuItem
                        text={t(T.DOWNLOAD)}
                        keyText={'⌘X'}
                        onClick={() => handleDownload(items)}
                        disabled={!canDownloadAll}
                    />
                    <CustomMenuItem
                        text={t(T.MOVE)}
                        onClick={handleFileMove}
                        disabled={!canEditAll}
                    />
                    <CustomMenuItem
                        text={
                            areAllItemsStarred || router.pathname.includes('/starred')
                                ? t(T.REMOVE_STARRED)
                                : t(T.ADD_STARRED)
                        }
                        onClick={handleStarred}
                        disabled={!canEditAll}
                    />
                    <CustomMenuItem
                        text={t(T.DELETE)}
                        onClick={handleDeleteDrive}
                        disabled={!canDeleteAll}
                    />
                </>
            );
        case '/trash':
            return (
                <>
                    {items?.length === 1 && (
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
                                keyText={'⌘X'}
                                onClick={() => handlePreview(items[0])}
                                // disabled={!canViewAll}
                            />
                            <CustomMenuItem
                                text={t(T.DOWNLOAD)}
                                keyText={'⌘X'}
                                onClick={() => handleDownload(items)}
                                // disabled={!canDownloadAll}
                            />
                        </>
                    )}
                    <CustomMenuItem text={t(T.TRASH_RESTORE)} onClick={handleRestore} />
                    <CustomMenuItem
                        text={t(T.TRASH_HARD_DELETE)}
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
                        // disabled={!canViewAll}
                    />
                </>
            );
    }
};

const StyledContextMenu = ({ menuState, onClose, checkItems, ...props }) => {
    const menuRef = useRef(null);
    const t = useTranslations();
    const [position, setPosition] = useState({ top: menuState.top, left: menuState.left });

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

        if (top + offsetHeight > innerHeight) {
            top = innerHeight - offsetHeight - 10;
        }

        if (left + offsetWidth > innerWidth) {
            left = innerWidth - offsetWidth - 324;
        } else {
            left = menuState.left + 4;
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
            {...props}
            style={{
                ...menuStyles,
                zIndex: 1202,
                top: position.top,
                left: position.left,
            }}
        >
            <CustomMenuItem text={'test'} />
            <CustomMenuItem text={'test'} />
            {/*{ContextMenuItems({*/}
            {/*    menuState,*/}
            {/*    onClose,*/}
            {/*    reload,*/}
            {/*    t,*/}
            {/*    items,*/}
            {/*    setCheckItems,*/}
            {/*    setDrawerOpen,*/}
            {/*    user*/}
            {/*})}*/}
        </ul>
    );
};

export default StyledContextMenu;

import React, { useEffect, useMemo, useRef, useState, useLayoutEffect } from 'react';
import { Menu, MenuItem, Stack, Typography } from '@mui/material';
import { useTranslations } from 'next-intl';
import { useDialog } from '@/provider/DialogProvider';
import palette from '@/theme/palette';
import { useRouter } from '@/hooks/useRouter';
import api from '@/utils/api';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { formatShortcut } from '@/utils/platform';
import { useUserDelete } from '@/hooks/useUserDelete';
import { useLicense } from '@/provider/LicenseProvider';
import useClientConfig from '@/hooks/useClientConfig';
import { ROUTES } from '@/constants/routes';

/** 파일 전체에서 사용되는 번역 키 상수 */
const T = {
    // 사용자 상태 변경 다이얼로그
    UPDATE_USER_STATUS_TITLE: 'UpdateUserStatusDialog.title',
    UPDATE_USER_STATUS_ACTIVATE: 'UpdateUserStatusDialog.text1',
    UPDATE_USER_STATUS_DEACTIVATE: 'UpdateUserStatusDialog.text2',
    // 사용자 활성화/비활성화 스낵바
    ACTIVATE_SUCCESS: 'adminUsers.activate.success',
    ACTIVATE_FAIL: 'adminUsers.activate.fail',
    DEACTIVATE_SUCCESS: 'adminUsers.deactivate.success',
    DEACTIVATE_FAIL: 'adminUsers.deactivate.fail',
    // 계정 복원 다이얼로그 / 스낵바
    RESTORE_ACCOUNT_TITLE: 'restoreAccountDialog.title',
    RESTORE_ACCOUNT_TEXT_MULTIPLE: 'restoreAccountDialog.textMultiple',
    RESTORE_ACCOUNT_TEXT_SINGLE: 'restoreAccountDialog.textSingle',
    RESTORE_ACCOUNT_BUTTON: 'common.action.restore',
    RESTORE_SUCCESS_MULTIPLE: 'pendingDeletion.restore.successMultiple',
    RESTORE_SUCCESS_SINGLE: 'pendingDeletion.restore.successSingle',
    RESTORE_PARTIAL: 'pendingDeletion.restore.partial',
    RESTORE_FAIL: 'pendingDeletion.restore.fail',
    // SSO 상태 변경 다이얼로그 / 스낵바
    UPDATE_SSO_STATUS_TITLE: 'UpdateSsoStatusDialog.title',
    UPDATE_SSO_STATUS_ACTIVATE: 'UpdateSsoStatusDialog.text1',
    UPDATE_SSO_STATUS_DEACTIVATE: 'UpdateSsoStatusDialog.text2',
    SSO_ACTIVATE_SUCCESS: 'adminSsoProvider.activate.success',
    SSO_ACTIVATE_FAIL: 'adminSsoProvider.activate.fail',
    SSO_DEACTIVATE_SUCCESS: 'adminSsoProvider.deactivate.success',
    SSO_DEACTIVATE_FAIL: 'adminSsoProvider.deactivate.fail',
    // 메뉴 항목 - 사용자/관리자
    MENU_ACTIVE: 'common.action.activate',
    MENU_INACTIVE: 'common.action.deactivate',
    MENU_EDIT: 'common.action.modify',
    MENU_DELETE: 'common.action.delete',
    MENU_RESTORE: 'common.action.restore',
    // 메뉴 항목 - SSO
    SSO_MENU_ACTIVE: 'common.action.activate',
    SSO_MENU_INACTIVE: 'common.action.deactivate',
    SSO_MENU_EDIT: 'common.action.modify',
};

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

function CustomMenuItem({ text, keyText, img, isSubMenu, ...props }) {
    const [anchorEl, setAnchorEl] = useState(null);
    const handleMenuClose = drive => {
        setAnchorEl(null);
    };
    return (
        <>
            <MenuItem
                {...props}
                sx={{ width: '100%', px: 3 }}
                onClick={e => {
                    if (isSubMenu) setAnchorEl(e.currentTarget);
                    props.onClick && props.onClick(e);
                }}
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
                        {/* {img ? img : <Box sx={{width: 20}}/>} */}
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
                onClose={() => handleMenuClose()}
                PaperProps={{
                    sx: {
                        ml: 1,
                        borderRadius: '6px',
                        boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
                        border: `1px solid ${palette.control_border_normal}`,
                        minWidth: 220,
                    },
                }}
                anchorOrigin={{
                    vertical: 'top',
                    horizontal: 'right',
                }}
                transformOrigin={{
                    vertical: 'top',
                    horizontal: 'left',
                }}
            >
                <MenuItem>test</MenuItem>
                <MenuItem>test</MenuItem>
            </Menu>
        </>
    );
}

const renderItems = ({
    menuState,
    onClose,
    reload,
    listPatch,
    refreshLicense,
    t,
    items,
    setCheckItems,
    user,
    openDialog,
    showSnackbar,
    router,
    handleDeleteAdmin,
    handleDeleteUser,
    handleCompletelyDeleteUser,
    isSystemManaged,
}) => {
    const areAllItemsActive = items.every(item => item.status === 'ACTIVE');
    const areAllItemsInactive = items.every(item => item.status === 'INACTIVE');
    const hasNonDeactivatableAccounts = items.some(
        item => item.userSeq === 1 || item.userSeq === user.userSeq
    );
    // 보호 계정(테넌트 생성 때 만든 계정)은 테넌트 유형과 관계없이 탈퇴·삭제·비활성화를 막는다(서버도 USER_034 로 거부). 활성화는 허용
    const hasProtectedAccounts = items.some(item => item.isProtected === true);
    const hasNonDeletableAccounts = items.some(
        item => item.userSeq === 1 || item.userSeq === user.userSeq || item.isProtected === true
    );
    const deactivateBlocked = areAllItemsActive && hasProtectedAccounts;

    const handleActivateUser = async () => {
        openDialog('alert', {
            title: t(T.UPDATE_USER_STATUS_TITLE),
            description: t(T.UPDATE_USER_STATUS_ACTIVATE),
            onClick: async () => {
                let success = true;
                for (let item of items) {
                    const res = await api.Patch(`admins/users/${item?.userSeq}/activate`, {});
                    if (res?.status !== 200) {
                        success = false;
                        break;
                    }
                }

                if (success) {
                    showSnackbar(t(T.ACTIVATE_SUCCESS), 'success');
                    setCheckItems([]);
                    if (listPatch) {
                        listPatch.patchStatus(
                            items.map(it => it.userSeq),
                            'ACTIVE'
                        );
                    } else {
                        await reload(true);
                    }
                    refreshLicense();
                } else {
                    showSnackbar(t(T.ACTIVATE_FAIL), 'error');
                }
            },
        });
        onClose();
    };

    const handleDeactivateUser = async () => {
        openDialog('alert', {
            title: t(T.UPDATE_USER_STATUS_TITLE),
            description: t(T.UPDATE_USER_STATUS_DEACTIVATE),
            onClick: async () => {
                let success = true;
                for (let item of items) {
                    const res = await api.Patch(`admins/users/${item?.userSeq}/deactivate`, {});
                    if (res?.status !== 200) {
                        success = false;
                        break;
                    }
                }
                if (success) {
                    showSnackbar(t(T.DEACTIVATE_SUCCESS), 'success');
                    setCheckItems([]);
                    if (listPatch) {
                        listPatch.patchStatus(
                            items.map(it => it.userSeq),
                            'INACTIVE'
                        );
                    } else {
                        await reload(true);
                    }
                    refreshLicense();
                } else {
                    showSnackbar(t(T.DEACTIVATE_FAIL), 'error');
                }
            },
        });
        onClose();
    };

    const handleUpdateUserDialog = async () => {
        openDialog('updateUser', {
            item: items[0],
            reload: reload,
            // 본인 계정이면 ADMIN 역할 해제를 막는다(서버도 USER_031 로 거부)
            loginUserSeq: user?.userSeq,
            // 수정된 행만 다시 읽어 교체한다 — 없으면 다이얼로그가 페이지를 새로고침한다
            onUpdated: listPatch ? () => listPatch.refresh(items[0]?.userSeq) : undefined,
        });
        onClose();
    };

    const handleRestoreUser = async () => {
        let successCount = 0;
        let failCount = 0;

        openDialog('alert', {
            title: t(T.RESTORE_ACCOUNT_TITLE),
            description:
                items.length > 1
                    ? t(T.RESTORE_ACCOUNT_TEXT_MULTIPLE, { n: items.length })
                    : t(T.RESTORE_ACCOUNT_TEXT_SINGLE),
            buttonName: t(T.RESTORE_ACCOUNT_BUTTON),
            onClick: async () => {
                for (let item of items) {
                    const res = await api.Patch(
                        `admins/users/pending-delete/${item.userSeq}/restore`,
                        {}
                    );

                    if (res?.status === 200) {
                        successCount++;
                    } else {
                        failCount++;
                    }
                }

                if (successCount > 0 && failCount === 0) {
                    showSnackbar(
                        items.length > 1
                            ? t(T.RESTORE_SUCCESS_MULTIPLE, { n: items.length })
                            : t(T.RESTORE_SUCCESS_SINGLE),
                        'success'
                    );
                } else if (successCount > 0 && failCount > 0) {
                    showSnackbar(
                        t(T.RESTORE_PARTIAL, {
                            success: successCount,
                            fail: failCount,
                        }),
                        'warning'
                    );
                } else {
                    showSnackbar(t(T.RESTORE_FAIL), 'error');
                }

                setCheckItems([]);
                await reload(true);
            },
        });
        onClose();
    };

    // 계정 목록(사용자·관리자 통합) — 삭제 안내 문구는 선택 행에 관리자가 있으면 관리자용
    const handleDeleteAccounts = targets =>
        targets.some(it => it?.roles?.includes('ADMIN'))
            ? handleDeleteAdmin(targets)
            : handleDeleteUser(targets);

    switch (router.pathname) {
        case ROUTES.ADMIN_ACCOUNTS_USERS:
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
                                text={!areAllItemsActive ? t(T.MENU_ACTIVE) : t(T.MENU_INACTIVE)}
                                onClick={
                                    !areAllItemsActive ? handleActivateUser : handleDeactivateUser
                                }
                                disabled={deactivateBlocked}
                            />
                            <CustomMenuItem
                                text={t(T.MENU_EDIT)}
                                onClick={handleUpdateUserDialog}
                                // SYSTEM_MANAGED 보호 계정은 계정 값을 외부 시스템이 정해 수정할 수 없다(서버도 TENANT_011 로 거부)
                                disabled={isSystemManaged && hasProtectedAccounts}
                            />
                            <CustomMenuItem
                                text={t(T.MENU_DELETE)}
                                keyText={`${formatShortcut('Delete')}`}
                                onClick={() => handleDeleteAccounts(items)}
                                disabled={hasNonDeletableAccounts}
                            />
                        </>
                    ) : (
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
                                text={!areAllItemsActive ? t(T.MENU_ACTIVE) : t(T.MENU_INACTIVE)}
                                onClick={
                                    !areAllItemsActive ? handleActivateUser : handleDeactivateUser
                                }
                                disabled={
                                    !(areAllItemsActive || areAllItemsInactive) || deactivateBlocked
                                }
                            />
                            <CustomMenuItem
                                text={t(T.MENU_DELETE)}
                                keyText={`${formatShortcut('Delete')}`}
                                onClick={() => handleDeleteAccounts(items)}
                                disabled={hasNonDeletableAccounts}
                            />
                        </>
                    )}
                </>
            );
        case ROUTES.ADMIN_ACCOUNTS_PENDING_DELETION:
            return (
                <>
                    <CustomMenuItem text={t(T.MENU_RESTORE)} onClick={handleRestoreUser} />
                    <CustomMenuItem
                        text={t(T.MENU_DELETE)}
                        keyText={`${formatShortcut('Delete')}`}
                        onClick={() => handleCompletelyDeleteUser(items)}
                        disabled={hasNonDeletableAccounts}
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
    user,
    listPatch,
}) => {
    const menuRef = useRef(null);
    const t = useTranslations();
    const [position, setPosition] = useState({ top: menuState.top, left: menuState.left });

    const { openDialog } = useDialog();
    const { showSnackbar } = useSnackbarContext();
    const { refreshLicense } = useLicense();
    const { isSystemManaged } = useClientConfig();
    const router = useRouter();

    const { handleDeleteAdmin, handleDeleteUser, handleCompletelyDeleteUser } = useUserDelete({
        onClose,
        t,
        setCheckItems,
        getData: reload,
        refreshLicense,
        listPatch,
    });

    const items = useMemo(() => {
        const base = checkItems?.length > 1 ? checkItems : [menuState.selectedRow];
        return (base ?? []).filter(Boolean); // [null] 방어
    }, [menuState.selectedRow, checkItems]);

    // 메뉴 위치 보정
    useLayoutEffect(() => {
        if (!menuRef.current) return;

        const { offsetHeight, offsetWidth } = menuRef.current;
        const { innerHeight, innerWidth, e } = window;

        let top = menuState.top;
        let left = menuState.left;

        if (window.event.target.id === 'dots') {
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
                listPatch,
                refreshLicense,
                t,
                items,
                setCheckItems,
                user,
                openDialog,
                showSnackbar,
                router,
                handleDeleteAdmin,
                handleDeleteUser,
                handleCompletelyDeleteUser,
                isSystemManaged,
            })}
        </ul>
    );
};

export default StyledContextMenu;

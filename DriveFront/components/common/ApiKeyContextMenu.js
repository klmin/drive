/** API Key 행 옵션 메뉴 — 활성/비활성 토글, 재발급, 만료일 수정. */
import React, { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Box, MenuItem, Typography } from '@mui/material';
import { useTranslations } from 'next-intl';
import { useDialog } from '@/provider/DialogProvider';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import palette from '@/theme/palette';
import api from '@/utils/api';

const menuStyles = {
    position: 'fixed',
    zIndex: 1202,
    minWidth: 160,
    borderRadius: '6px',
    boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
    border: `1px solid ${palette.control_border_normal}`,
    backgroundColor: palette.btn_white_bg_normal,
    padding: '8px 0',
    listStyle: 'none',
    margin: 0,
};

/** 시안: 상하 패딩 6 + 줄 상자 24 */
const menuItemSx = {
    width: '100%',
    minHeight: 36,
    py: '6px',
    px: 3,
};

function CustomMenuItem({ text, ...props }) {
    return (
        <MenuItem {...props} sx={menuItemSx}>
            <Typography variant="body5" color="text_80">
                {text}
            </Typography>
        </MenuItem>
    );
}

/** basePath — 역할별 키 API 경로(관리자 admins/api-keys, 사용자 users/api-keys, 슈퍼관리자 super-admin/api-keys) */
export default function ApiKeyContextMenu({
    menuState,
    onClose,
    reload,
    listPatch,
    item,
    basePath = 'admins/api-keys',
}) {
    const menuRef = useRef(null);
    const t = useTranslations();
    const { openDialog } = useDialog();
    const { showSnackbar } = useSnackbarContext();
    const [position, setPosition] = useState({ top: menuState.top, left: menuState.left });

    useLayoutEffect(() => {
        if (!menuRef.current) return;

        const { offsetHeight, offsetWidth } = menuRef.current;
        let top = menuState.top;
        let left = menuState.left;

        if (window.event?.target?.id === 'dots') {
            const rect = window.event.target.getBoundingClientRect();
            top = rect.bottom + 12;
        } else if (window.event?.target?.id === 'dots-button') {
            const rect = window.event.target.getBoundingClientRect();
            top = rect.bottom + 6;
        }

        if (top + offsetHeight > window.innerHeight) top = window.innerHeight - offsetHeight - 10;
        if (left + offsetWidth > window.innerWidth) left = window.innerWidth - offsetWidth - 100;

        setPosition({ top, left });
    }, [menuState.top, menuState.left, menuState.visible]);

    useEffect(() => {
        const handleClickOutside = e => {
            if (menuRef.current && !menuRef.current.contains(e.target)) onClose();
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

    if (!menuState.visible || !item) return null;

    const isActive = item.status === 'ACTIVE';
    // 보호 키는 슈퍼관리자 경로에서만 바꿀 수 있다 — 관리자·사용자 화면에서는 메뉴를 막고 이유를 보여 준다(서버도 API_KEY_017 로 거부)
    const locked = item.isProtected === true && basePath !== 'super-admin/api-keys';

    const handleActivate = () => {
        openDialog('alert', {
            title: t('updateApiKeyStatusDialog.activateTitle'),
            description: t('updateApiKeyStatusDialog.activateText'),
            buttonName: t('common.action.activate'),
            onClick: async () => {
                const res = await api.Patch(`${basePath}/${item.apiKeySeq}/activate`, {});
                if (res?.status === 200) {
                    showSnackbar(t('updateApiKeyStatusDialog.activate.success'), 'success');
                    // 목록 전체 재조회 대신 상태가 바뀐 행만 갱신한다(스크롤 위치 유지)
                    if (listPatch?.patchStatus) listPatch.patchStatus(item.apiKeySeq, 'ACTIVE');
                    else await reload?.();
                } else {
                    showSnackbar(t('updateApiKeyStatusDialog.activate.fail'), 'error');
                }
            },
        });
        onClose();
    };

    const handleDeactivate = () => {
        openDialog('alert', {
            title: t('updateApiKeyStatusDialog.deactivateTitle'),
            description: t('updateApiKeyStatusDialog.deactivateText'),
            buttonName: t('common.action.deactivate'),
            onClick: async () => {
                const res = await api.Patch(`${basePath}/${item.apiKeySeq}/deactivate`, {});
                if (res?.status === 200) {
                    showSnackbar(t('updateApiKeyStatusDialog.deactivate.success'), 'success');
                    if (listPatch?.patchStatus) listPatch.patchStatus(item.apiKeySeq, 'INACTIVE');
                    else await reload?.();
                } else {
                    showSnackbar(t('updateApiKeyStatusDialog.deactivate.fail'), 'error');
                }
            },
        });
        onClose();
    };

    const handleReissue = () => {
        openDialog('alert', {
            title: t('reissueApiKeyDialog.title'),
            description: t('reissueApiKeyDialog.text'),
            buttonName: t('reissueApiKeyDialog.confirm'),
            onClick: async () => {
                const res = await api.Post(`${basePath}/${item.apiKeySeq}/reissue`, {});
                if (res?.code === 200) {
                    // TODO: 재발급 응답이 apiKey만 돌려줘 행을 채울 수 없다.
                    // 단건 조회 API가 생기거나 응답에 목록 필드가 추가되면 행만 갱신하도록 바꾼다.
                    await reload?.();
                    openDialog('reissueApiKeyComplete', { apiKey: res?.data?.apiKey ?? '' });
                } else {
                    showSnackbar(t('reissueApiKeyDialog.fail'), 'error');
                }
            },
        });
        onClose();
    };

    const handleUpdateExpireDate = () => {
        openDialog('updateApiKeyExpireDate', { item, reload, listPatch, basePath });
        onClose();
    };

    // 보호 설정·해제(PATCH …/protection) — 슈퍼관리자 경로만. 배지·메뉴 잠금이 바뀌므로 목록을 다시 조회한다
    const canEditProtection = basePath === 'super-admin/api-keys';
    const handleProtection = () => {
        const next = !item.isProtected;
        const key = next ? 'protect' : 'unprotect';
        openDialog('alert', {
            title: t(`superAdmin.apiKey.protectionDialog.${key}Title`),
            description: t(`superAdmin.apiKey.protectionDialog.${key}Text`),
            buttonName: t(`superAdmin.apiKey.menu.${key}`),
            onClick: async () => {
                const res = await api.Patch(`${basePath}/${item.apiKeySeq}/protection`, {
                    pt: next,
                });
                if (res?.status === 200) {
                    showSnackbar(t(`superAdmin.apiKey.protectionDialog.${key}Success`), 'success');
                    await reload?.();
                } else {
                    showSnackbar(t('superAdmin.apiKey.protectionDialog.fail'), 'error');
                }
            },
        });
        onClose();
    };

    return (
        <ul ref={menuRef} style={{ ...menuStyles, top: position.top, left: position.left }}>
            {locked && (
                <Box component="li" sx={{ px: 3, pb: 1, maxWidth: 260 }}>
                    <Typography variant="body6" color="text_40" sx={{ whiteSpace: 'normal' }}>
                        {t('admin.apiKey.badge.protectedTooltip')}
                    </Typography>
                </Box>
            )}
            <CustomMenuItem
                text={isActive ? t('common.action.deactivate') : t('common.action.activate')}
                onClick={isActive ? handleDeactivate : handleActivate}
                disabled={locked}
            />
            <CustomMenuItem
                text={t('admin.apiKey.menu.reissue')}
                onClick={handleReissue}
                disabled={locked}
            />
            <Box component="li" sx={{ py: 1 }}>
                <Box sx={{ height: '1px', backgroundColor: palette.divider_8 }} />
            </Box>
            <CustomMenuItem
                text={t('admin.apiKey.menu.updateExpiry')}
                onClick={handleUpdateExpireDate}
                disabled={locked}
            />
            {/* 보호 설정·해제 — 슈퍼관리자 화면만 */}
            {canEditProtection && (
                <CustomMenuItem
                    text={t(
                        item.isProtected
                            ? 'superAdmin.apiKey.menu.unprotect'
                            : 'superAdmin.apiKey.menu.protect'
                    )}
                    onClick={handleProtection}
                />
            )}
        </ul>
    );
}

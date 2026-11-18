import React, { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { MenuItem, Stack, Typography } from '@mui/material';
import { useTranslations } from 'next-intl';
import { useDialog } from '@/provider/DialogProvider';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import palette from '@/theme/palette';
import api from '@/utils/api';

const menuStyles = {
    position: 'fixed',
    zIndex: 1202,
    minWidth: 77,
    borderRadius: '6px',
    boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
    border: `1px solid ${palette.control_border_normal}`,
    backgroundColor: palette.btn_white_bg_normal,
    padding: '8px 0',
    listStyle: 'none',
    margin: 0,
};

function CustomMenuItem({ text, ...props }) {
    return (
        <MenuItem {...props} sx={{ width: '100%', px: 3 }}>
            <Stack
                direction="row"
                sx={{ width: '100%' }}
                alignItems="center"
                justifyContent="center"
            >
                <Typography variant="body5" color="text_80">
                    {text}
                </Typography>
            </Stack>
        </MenuItem>
    );
}

export default function TenantContextMenu({ menuState, onClose, reload, item, listPatch }) {
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

    const handleEdit = () => {
        openDialog('editTenant', { tenantSeq: item.tenantSeq, reload, listPatch });
        onClose();
    };

    // 라이선스가 있으면 갱신(새 파일로 교체), 없으면 등록 — 같은 API, 문구와 현재 라이선스 표시만 다르다
    const licenseRegistered = !!item.license;
    const handleLicense = () => {
        openDialog('tenantLicense', { item, reload, registered: licenseRegistered });
        onClose();
    };

    // 테넌트당 하나 — 연결돼 있으면 수정, 아니면 등록(같은 다이얼로그, 수정이면 현재 값을 채운다)
    const officeRegistered = item.office?.connectionType === 'onPremise';
    const handleOffice = () => {
        openDialog('tenantOffice', { item, reload, registered: officeRegistered });
        onClose();
    };

    // 테넌트 삭제는 SYSTEM_MANAGED 만(서버도 SELF_MANAGED 는 TENANT_010) — 되돌릴 수 없어 확인을 받는다
    const handleDelete = () => {
        const tenantSeq = item.tenantSeq;
        openDialog('alert', {
            title: t('superAdmin.tenantDeleteDialog.title'),
            description: t('superAdmin.tenantDeleteDialog.desc', { tenantName: item.tenantName }),
            buttonName: t('common.action.delete'),
            onClick: async () => {
                const res = await api.Delete(`super-admin/tenants/${tenantSeq}`);
                // 이미 지워진 테넌트(404)도 목록에서 빠지면 되므로 성공으로 본다
                // 실패면 api 헬퍼가 응답 본문({ code, errorCode })을 돌려준다
                if (res?.status === 200 || res?.code === 404) {
                    showSnackbar(t('superAdmin.tenantDeleteDialog.success'), 'success');
                    await reload?.();
                } else {
                    showSnackbar(t('superAdmin.tenantDeleteDialog.fail'), 'error');
                }
            },
        });
        onClose();
    };

    return (
        <ul ref={menuRef} style={{ ...menuStyles, top: position.top, left: position.left }}>
            <CustomMenuItem text={t('common.action.modify')} onClick={handleEdit} />
            <CustomMenuItem
                text={t(
                    licenseRegistered
                        ? 'superAdmin.tenantMenu.licenseRenew'
                        : 'superAdmin.tenantMenu.license'
                )}
                onClick={handleLicense}
            />
            <CustomMenuItem
                text={t(
                    officeRegistered
                        ? 'superAdmin.tenantMenu.officeModify'
                        : 'superAdmin.tenantMenu.officeRegister'
                )}
                onClick={handleOffice}
            />
            {item.tenantType === 'SYSTEM_MANAGED' && (
                <CustomMenuItem text={t('common.action.delete')} onClick={handleDelete} />
            )}
        </ul>
    );
}

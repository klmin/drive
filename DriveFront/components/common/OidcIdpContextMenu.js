import React, { useState } from 'react';
import { Menu, MenuItem, Typography } from '@mui/material';
import { useTranslations } from 'next-intl';
import palette from '@/theme/palette';
import { useDialog } from '@/provider/DialogProvider';
import UpdateIdpDialog from '@/components/common/dialog/UpdateIdpDialog';

const menuItemSx = {
    height: '40px',
    pl: '12px',
    pr: '8px',
    gap: '10px',
    justifyContent: 'center',
    backgroundColor: palette.btn_white_bg_normal,
    '&:hover': {
        backgroundColor: palette.bg_hover,
    },
};

export default function OidcIdpContextMenu({
    anchorEl,
    onClose,
    provider,
    onEditSuccess,
    onDeleteSuccess,
}) {
    const t = useTranslations();
    const { openDialog } = useDialog();
    const [editOpen, setEditOpen] = useState(false);

    const handleEdit = () => {
        onClose();
        setEditOpen(true);
    };

    const handleDelete = () => {
        onClose();
        openDialog('alert', {
            title: t('admin.loginSetting.deleteIdpDialog.title'),
            description: t('admin.loginSetting.deleteIdpDialog.desc'),
            buttonName: t('common.action.delete'),
            maxWidth: 360,
            onClick: () => {
                onDeleteSuccess?.(provider.ssoProviderSeq);
            },
        });
    };

    const isActive = provider?.status === 'ACTIVE';

    return (
        <>
            <Menu
                anchorEl={anchorEl}
                open={Boolean(anchorEl)}
                onClose={onClose}
                PaperProps={{
                    sx: {
                        borderRadius: '6px',
                        boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
                        border: `1px solid ${palette.control_border_normal}`,
                        backgroundColor: palette.btn_white_bg_normal,
                        minWidth: 'unset',
                        width: '77px',
                        py: 0,
                    },
                }}
            >
                <MenuItem sx={menuItemSx} onClick={handleEdit}>
                    <Typography variant="body5" color="text_80">
                        {t('common.action.modify')}
                    </Typography>
                </MenuItem>
                <MenuItem sx={menuItemSx} onClick={handleDelete} disabled={isActive}>
                    <Typography variant="body5" color={'text_80'}>
                        {t('common.action.delete')}
                    </Typography>
                </MenuItem>
            </Menu>
            <UpdateIdpDialog
                open={editOpen}
                onClose={() => setEditOpen(false)}
                provider={provider}
                onSuccess={onEditSuccess}
            />
        </>
    );
}

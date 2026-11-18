'use client';

import React, { useState } from 'react';
import {
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Typography,
} from '@mui/material';
import StyledButton from '@/components/common/button/StyledButton';
import { useTranslations } from 'next-intl';
import palette from '@/theme/palette';

export default function AlertDialog({ open, onClose, onConfirm, ...props }) {
    const t = useTranslations();
    const { title, description, onClick, buttonName, cancelButtonName, maxWidth } = props;
    const btn = cancelButtonName === undefined ? t('common.button.cancel') : cancelButtonName;
    const [sending, setSending] = useState(false);

    const handleConfirm = async () => {
        try {
            setSending(true);
            if (onConfirm) {
                await onConfirm();
            }
            onClose();
        } finally {
            setSending(false);
        }
    };

    return (
        <Dialog
            open={open}
            onClose={onClose}
            PaperProps={{
                sx: {
                    borderRadius: 4,
                    p: '32px 32px 24px 32px',
                    margin: 0,
                    maxWidth: maxWidth ?? '480px !important',
                    width: '100%',
                },
            }}
        >
            <DialogTitle
                sx={{
                    p: 0,
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                }}
            >
                <Typography
                    variant="body1"
                    color={'text_100'}
                    sx={{ whiteSpace: 'pre-wrap', overflow: 'hidden' }}
                >
                    {title}
                </Typography>
                {sending && <CircularProgress size={30} color="inherit" />}
            </DialogTitle>
            {description && (
                <DialogContent sx={{ p: 0, mt: title ? 4 : 0 }}>
                    <Typography
                        variant="body5_1"
                        color={'text_80'}
                        sx={{ whiteSpace: 'pre-line', wordBreak: 'break-all' }}
                    >
                        {description}
                    </Typography>
                </DialogContent>
            )}
            {/* theme styleOverrides의 미디어쿼리(tablet 미만 column)가 sx보다 나중에 주입되어 !important로 override */}
            <DialogActions sx={{ flexDirection: 'row !important' }}>
                {btn !== '' && (
                    <StyledButton
                        onClick={onClose}
                        color={'btn_border'}
                        variant="outlined"
                        size="small"
                        sx={{
                            minWidth: 80,
                            height: 40,
                            gap: '10px',
                            outline: `1px solid ${palette.btn_border}`,
                            outlineOffset: '-1px',
                            border: 'none',
                            background: palette.btn_white_bg_normal,
                        }}
                        disabled={sending}
                    >
                        <Typography variant={'body5'} color={'text_100'}>
                            {btn}
                        </Typography>
                    </StyledButton>
                )}
                <StyledButton
                    color={'btn_point_bg'}
                    variant="contained"
                    size="small"
                    sx={{ minWidth: 80, height: 40, gap: '10px' }}
                    onClick={handleConfirm}
                    disabled={sending}
                >
                    <Typography variant={'body5'} color={'white'}>
                        {buttonName || t('common.button.confirm')}
                    </Typography>
                </StyledButton>
            </DialogActions>
        </Dialog>
    );
}

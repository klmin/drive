// components/dialog/FileDuplicateDialog.js
'use client';

import React from 'react';
import { Dialog, DialogActions, DialogContent, DialogTitle, Typography } from '@mui/material';
import StyledButton from '@/components/common/button/StyledButton';
import { useTranslations } from 'next-intl';

export default function FileDuplicateDialog({ open, onClose, onSelect, ...props }) {
    const t = useTranslations();
    const { title, description, keepBothText, replaceText } = props;

    const handleSelect = choice => {
        if (onSelect) onSelect(choice); // 'keepBoth' | 'replace'
        onClose();
    };

    return (
        <Dialog
            open={open}
            onClose={onClose}
            PaperProps={{
                sx: {
                    borderRadius: 4,
                    p: 8,
                    margin: 0,
                    maxWidth: '480px !important',
                    width: '100%',
                },
            }}
        >
            <DialogTitle sx={{ p: 0 }}>
                <Typography variant="body1" color={'text_100'}>
                    {title || t('duplicateFileDialog.title')}
                </Typography>
            </DialogTitle>

            {description && (
                <DialogContent sx={{ p: 0, mt: title ? 4 : 0 }}>
                    <Typography variant="body5" color={'text_80'} sx={{ whiteSpace: 'pre-line' }}>
                        {description}
                    </Typography>
                </DialogContent>
            )}

            <DialogActions>
                <StyledButton
                    onClick={() => handleSelect('keepBoth')}
                    color={'btn_border'}
                    variant="outlined"
                    size="small"
                    sx={{ minWidth: { xs: '100%', tablet: 160 } }}
                >
                    <Typography variant={'body5'} color={'text_100'}>
                        {keepBothText || t('duplicateFileDialog.keepBoth')}
                    </Typography>
                </StyledButton>

                <StyledButton
                    onClick={() => handleSelect('replace')}
                    color={'btn_point_bg'}
                    variant="contained"
                    size="small"
                    sx={{ minWidth: { xs: '100%', tablet: 160 } }}
                >
                    <Typography variant={'body5'} color={'white'}>
                        {replaceText || t('duplicateFileDialog.replace')}
                    </Typography>
                </StyledButton>
            </DialogActions>
        </Dialog>
    );
}

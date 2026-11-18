import React, { useEffect, useState } from 'react';
import { Dialog, DialogActions, DialogContent, DialogTitle, Typography } from '@mui/material';
import StyledTextField from '@/components/common/input/StyledTextField';
import StyledButton from '@/components/common/button/StyledButton';
import { useTranslations } from 'next-intl';
import api from '@/utils/api';
import { useSnackbarContext } from '@/provider/SnackbarProvider';

const INVALID_CHARS_REGEX = /[\\/:*?"<>|]/g;
const RESERVED_WORDS = [
    'CON',
    'PRN',
    'AUX',
    'NUL',
    ...Array.from({ length: 9 }, (_, i) => `COM${i + 1}`),
    ...Array.from({ length: 9 }, (_, i) => `LPT${i + 1}`),
];

export default function FileNameDialog({ open, onClose, ...props }) {
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    const { item, reload } = props;

    const [name, setName] = useState('');
    const [error, setError] = useState('');

    const handleChange = e => {
        let val = e.target.value;

        const trimmed = val.trim();

        if (val !== trimmed) {
            setError(t('nameValidation.leadingOrTrailingSpaces'));
            return;
        }

        if (trimmed.length > 255) {
            setError(t('nameValidation.exceedsMaxLength'));
            return;
        }

        if (INVALID_CHARS_REGEX.test(val)) {
            setError(t('nameValidation.invalidSpecialChars'));
            return;
        }

        if (trimmed.startsWith('.') || trimmed.endsWith('.')) {
            setError(t('nameValidation.leadingOrTrailingDot'));
            return;
        }

        const upperVal = trimmed.toUpperCase();
        if (RESERVED_WORDS.includes(upperVal)) {
            setError(t('nameValidation.windowsReservedName'));
            return;
        }

        setError('');
        setName(val);
    };

    const handleChangeFileName = async () => {
        const res = await api.Patch(`folders/${item?.resourceSeq}/rename`, {
            ss: item?.sharedByUserSeq || item?.userSeq || undefined,
            rn: name,
        });

        if (res.status === 200 || res.status === 201) {
            showSnackbar(t('common.toast.changeSuccess'), 'success');
            reload();
            onClose();
        }
    };

    useEffect(() => {
        setName(item?.resourceName);
    }, [item]);

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
            <DialogTitle
                sx={{
                    p: 0,
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                }}
            >
                <Typography variant="body1">{t('renameDialog.title')}</Typography>
            </DialogTitle>
            <DialogContent sx={{ p: 0, mt: 4 }}>
                <StyledTextField
                    placeholder={t('uploadDialog.newDocs')}
                    size="small"
                    value={name}
                    onChange={handleChange}
                    onKeyDown={async e => {
                        if (e.key === 'Enter') {
                            e.preventDefault();
                            await handleChangeFileName();
                        }
                    }}
                    error={!!error}
                    errorMessage={error}
                    variant="outlined"
                    sx={{ width: '100%' }}
                />
            </DialogContent>
            <DialogActions>
                <StyledButton
                    onClick={onClose}
                    color={'btn_border'}
                    variant="outlined"
                    sx={{ minWidth: { xs: '100%', tablet: 80 } }}
                    size="small"
                >
                    <Typography variant={'body5'} color={'text_100'}>
                        {t('common.button.cancel')}
                    </Typography>
                </StyledButton>
                <StyledButton
                    color={'btn_point_bg'}
                    variant="contained"
                    size="small"
                    onClick={handleChangeFileName}
                    sx={{ minWidth: { xs: '100%', tablet: 80 } }}
                >
                    <Typography variant={'body5'} color={'white'}>
                        {t('common.button.confirm')}
                    </Typography>
                </StyledButton>
            </DialogActions>
        </Dialog>
    );
}

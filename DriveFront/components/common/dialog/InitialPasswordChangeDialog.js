'use client';

import React, { useCallback, useEffect, useState } from 'react';
import {
    Dialog,
    DialogContent,
    DialogActions,
    IconButton,
    InputAdornment,
    Typography,
    Stack,
} from '@mui/material';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import VisibilityOffOutlinedIcon from '@mui/icons-material/VisibilityOffOutlined';
import StyledTextField from '@/components/common/input/StyledTextField';
import StyledButton from '@/components/common/button/StyledButton';
import { useTranslations } from 'next-intl';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import api from '@/utils/api';
import { useRouter } from '@/hooks/useRouter';

export default function InitialPasswordChangeDialog({ open, onClose }) {
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    const router = useRouter();

    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [showNewPassword, setShowNewPassword] = useState(false);
    const [showConfirmPassword, setShowConfirmPassword] = useState(false);
    const [errorNewPassword, setErrorNewPassword] = useState('');
    const [errorConfirmPassword, setErrorConfirmPassword] = useState('');
    const [isDisabled, setIsDisabled] = useState(true);

    const checkPassword = (value, type) => {
        const PASSWORD_REGEX = /^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^\w\s]).{8,64}$/;
        const setError = type === 'confirm' ? setErrorConfirmPassword : setErrorNewPassword;

        if (!PASSWORD_REGEX.test(value)) {
            setError(t('common.validate.password'));
            return;
        }

        if (type === 'confirm') {
            setError(value !== newPassword ? t('changePasswordDialog.error.mismatch') : '');
        } else {
            setErrorNewPassword('');
            setErrorConfirmPassword(
                confirmPassword && value !== confirmPassword
                    ? t('changePasswordDialog.error.mismatch')
                    : ''
            );
        }
    };

    const handleChange = useCallback(async () => {
        // 새 비밀번호만 받음
        const res = await api.Patch('users/password', { currentPwd: null, newPwd: newPassword });
        if (res?.status === 200) {
            showSnackbar(t('changePasswordDialog.change.success'), 'success');
            router.reload();
        }
        // 새 비밀번호가 이전 비밀번호와 동일한 경우
        else if (res?.errorCode === 'USER_010') {
            showSnackbar(t('changePasswordDialog.error.sameAsOld'), 'error');
        } else {
            showSnackbar(t('changePasswordDialog.change.fail'), 'error');
        }
    }, [newPassword]);

    useEffect(() => {
        setIsDisabled(
            !(
                newPassword &&
                confirmPassword &&
                errorNewPassword === '' &&
                errorConfirmPassword === ''
            )
        );
    }, [newPassword, confirmPassword, errorNewPassword, errorConfirmPassword]);

    const passwordAdornment = (visible, toggle) => ({
        endAdornment: (
            <InputAdornment position="end">
                <IconButton onClick={toggle} sx={{ p: 0 }}>
                    {visible ? (
                        <VisibilityOutlinedIcon fontSize="small" style={{ opacity: 0.8 }} />
                    ) : (
                        <VisibilityOffOutlinedIcon fontSize="small" style={{ opacity: 0.8 }} />
                    )}
                </IconButton>
            </InputAdornment>
        ),
    });

    return (
        <Dialog
            open={open}
            onClose={onClose}
            PaperProps={{
                sx: {
                    borderRadius: 4,
                    pt: 8,
                    pb: 6,
                    px: 8,
                    margin: 0,
                    minWidth: '480px !important',
                    width: '100%',
                },
            }}
        >
            <DialogContent sx={{ p: 0, overflow: 'visible' }}>
                <Stack direction="column" sx={{ gap: '16px' }}>
                    <Typography variant="body1" color="#3A3D49">
                        {t('common.tempPasswordDialog.title')}
                    </Typography>
                    <Typography
                        variant="body5_1"
                        sx={{ color: 'rgba(58,61,73,0.80)', whiteSpace: 'pre-line' }}
                    >
                        {t('common.tempPasswordDialog.desc')}
                    </Typography>
                    <StyledTextField
                        type={showNewPassword ? 'text' : 'password'}
                        placeholder={t('changePasswordDialog.newPassword')}
                        size="small"
                        variant="outlined"
                        sx={{ width: '100%' }}
                        height={40}
                        value={newPassword}
                        error={!!errorNewPassword}
                        errorMessage={errorNewPassword}
                        onChange={e => {
                            setNewPassword(e.target.value);
                            checkPassword(e.target.value, 'new');
                        }}
                        InputProps={passwordAdornment(showNewPassword, () =>
                            setShowNewPassword(prev => !prev)
                        )}
                    />
                    <StyledTextField
                        type={showConfirmPassword ? 'text' : 'password'}
                        placeholder={t('changePasswordDialog.ConfirmPassword')}
                        size="small"
                        variant="outlined"
                        sx={{ width: '100%' }}
                        height={40}
                        value={confirmPassword}
                        error={!!errorConfirmPassword}
                        errorMessage={errorConfirmPassword}
                        onChange={e => {
                            setConfirmPassword(e.target.value);
                            checkPassword(e.target.value, 'confirm');
                        }}
                        InputProps={passwordAdornment(showConfirmPassword, () =>
                            setShowConfirmPassword(prev => !prev)
                        )}
                    />
                </Stack>
            </DialogContent>
            <DialogActions sx={{ p: 0, mt: '24px' }}>
                <StyledButton
                    onClick={onClose}
                    color="btn_border"
                    variant="outlined"
                    sx={{ minWidth: { xs: '100%', tablet: 80 }, height: 40 }}
                    size="small"
                >
                    <Typography variant="body5" color="text_100">
                        {t('common.tempPasswordDialog.laterBtn')}
                    </Typography>
                </StyledButton>
                <StyledButton
                    variant="contained"
                    sx={{
                        minWidth: { xs: '100%', tablet: 80 },
                        height: 40,
                        backgroundColor: '#1A1A1A',
                        '&:hover': { backgroundColor: '#333' },
                    }}
                    size="small"
                    onClick={handleChange}
                    disabled={isDisabled}
                >
                    <Typography variant="body5" color="white">
                        {t('common.button.change')}
                    </Typography>
                </StyledButton>
            </DialogActions>
        </Dialog>
    );
}

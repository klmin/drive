// components/dialog/AddUserGroupDialog.js
'use client';

import React, { useCallback, useEffect, useState } from 'react';
import {
    Dialog,
    DialogTitle,
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
import { useDialog } from '@/provider/DialogProvider';
import { clearAuth } from '@/utils/authTokens';
import { ROUTES } from '@/constants/routes';

export default function ChangePassword({ open, onClose, ...props }) {
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    const { type, item } = props;
    const router = useRouter();
    const { openDialog } = useDialog();
    const isAdmin = router.asPath.includes('admin');

    const [password, setPassword] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [showNewPassword, setShowNewPassword] = useState(false);
    const [showConfirmPassword, setShowConfirmPassword] = useState(false);
    const [errorPassword, setErrorPassword] = useState('');
    const [errorNewPassword, setErrorNewPassword] = useState('');
    const [errorConfirmPassword, setErrorConfirmPassword] = useState('');
    const [isDisabled, setIsDisabled] = useState(true);

    const handleChangePassword = useCallback(async () => {
        const url = !isAdmin ? `users/password` : `admins/users/${item?.userSeq}/password`;
        const res = await api.Patch(url, {
            currentPwd: password,
            newPwd: newPassword,
        });
        if (res?.status === 200) {
            showSnackbar(t('changePasswordDialog.change.success'), 'success');
            router.reload();
        }
        // 입력한 비밀번호와 현재 비밀번호 불일치하는 경우
        else if (res?.errorCode === 'USER_009') {
            showSnackbar(t('changePasswordDialog.error.notCurrent'), 'error');
        }
        // 새 비밀번호가 이전 비밀번호와 동일한 경우
        else if (res?.errorCode === 'USER_010') {
            showSnackbar(t('changePasswordDialog.error.sameAsOld'), 'error');
        }
        // 기타 에러
        else {
            showSnackbar(t('changePasswordDialog.change.fail'), 'error');
        }
    }, [password, newPassword]);

    const checkPassword = async (_password, type) => {
        const PASSWORD_REGEX = /^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^\w\s]).{8,64}$/;

        const typeBySetter =
            type === 'confirm' ? setErrorConfirmPassword : type === 'new' && setErrorNewPassword;
        if (!typeBySetter) return;

        // 백엔드 패턴 검증
        if (!PASSWORD_REGEX.test(_password)) {
            return typeBySetter(t('common.validate.password'));
        }

        // 비밀번호 확인 검증
        if (type === 'confirm') {
            if (_password !== newPassword) {
                return setErrorConfirmPassword(t('changePasswordDialog.error.mismatch'));
            }
        } else if (type === 'new') {
            if (_password !== confirmPassword) {
                setErrorConfirmPassword(t('changePasswordDialog.error.mismatch'));
            } else {
                setErrorConfirmPassword('');
            }
        }

        // 통과 시 에러 초기화
        return typeBySetter('');
    };

    const handleResetMyPassword = async () => {
        try {
            const res = await api.Post(`users/password/reset-link/me`, {});

            if (res?.code === 200) {
                showSnackbar(t('passwordResetEmail.success'), 'success');

                // 비밀번호 초기화 후 로그아웃 처리
                const res = await api.Post('auth/logout', null, null, { __roleType: 'USER' });
                if (res?.code === 200) {
                    clearAuth('USER');
                    router.replace(ROUTES.LOGIN);
                }
                onClose();
            } else {
                showSnackbar(t('passwordResetEmail.fail'), 'error');
            }
        } catch (err) {
            console.log(err);
            showSnackbar(t('passwordResetEmail.fail'), 'error');
        }
    };

    useEffect(() => {
        if (
            errorNewPassword === '' &&
            errorConfirmPassword === '' &&
            newPassword !== '' &&
            confirmPassword !== '' &&
            (isAdmin || (errorPassword === '' && password !== ''))
        ) {
            setIsDisabled(false);
        } else {
            setIsDisabled(true);
        }
    }, [
        password,
        newPassword,
        confirmPassword,
        errorPassword,
        errorNewPassword,
        errorConfirmPassword,
    ]);

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
                <Typography variant="body1">{t('common.title.changePassword')}</Typography>
            </DialogTitle>
            <DialogContent sx={{ p: 0, mt: '16px', overflow: 'visible' }}>
                <Stack direction={'column'} spacing={'16px'}>
                    {/* 현재 비밀번호 */}
                    {!isAdmin && (
                        <StyledTextField
                            type={showPassword ? 'text' : 'password'}
                            placeholder={t('changePasswordDialog.password')}
                            size="small"
                            variant="outlined"
                            sx={{ width: '100%' }}
                            height={40}
                            value={password}
                            onChange={e => setPassword(e.target.value)}
                            InputProps={{
                                endAdornment: (
                                    <InputAdornment position="end">
                                        <IconButton
                                            onClick={() => setShowPassword(prev => !prev)}
                                            sx={{ p: 0 }}
                                        >
                                            {showPassword ? (
                                                <VisibilityOutlinedIcon
                                                    size={20}
                                                    fontSize={'small'}
                                                    style={{ opacity: 0.8 }}
                                                />
                                            ) : (
                                                <VisibilityOffOutlinedIcon
                                                    fontSize={'small'}
                                                    style={{ opacity: 0.8 }}
                                                />
                                            )}
                                        </IconButton>
                                    </InputAdornment>
                                ),
                            }}
                        />
                    )}
                    {/* 새 비밀번호 */}
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
                        InputProps={{
                            endAdornment: (
                                <InputAdornment position="end">
                                    <IconButton
                                        onClick={() => setShowNewPassword(prev => !prev)}
                                        sx={{ p: 0 }}
                                    >
                                        {showNewPassword ? (
                                            <VisibilityOutlinedIcon
                                                size={20}
                                                fontSize={'small'}
                                                style={{ opacity: 0.8 }}
                                            />
                                        ) : (
                                            <VisibilityOffOutlinedIcon
                                                fontSize={'small'}
                                                style={{ opacity: 0.8 }}
                                            />
                                        )}
                                    </IconButton>
                                </InputAdornment>
                            ),
                        }}
                    />
                    {/* 새 비밀번호 확인 */}
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
                        InputProps={{
                            endAdornment: (
                                <InputAdornment position="end">
                                    <IconButton
                                        onClick={() => setShowConfirmPassword(prev => !prev)}
                                        sx={{ p: 0 }}
                                    >
                                        {showConfirmPassword ? (
                                            <VisibilityOutlinedIcon
                                                size={20}
                                                fontSize={'small'}
                                                style={{ opacity: 0.8 }}
                                            />
                                        ) : (
                                            <VisibilityOffOutlinedIcon
                                                fontSize={'small'}
                                                style={{ opacity: 0.8 }}
                                            />
                                        )}
                                    </IconButton>
                                </InputAdornment>
                            ),
                        }}
                    />
                    {!isAdmin && (
                        <StyledButton
                            variant="text"
                            sx={{ p: 0 }}
                            onClick={() => {
                                openDialog('alert', {
                                    title: t('resetPasswordConfirmDialog.title'),
                                    description: t('resetPasswordConfirmDialog.text'),
                                    buttonName: t('common.button.confirm'),
                                    cancelButtonName: t('common.button.cancel'),
                                    onClick: handleResetMyPassword,
                                });
                            }}
                        >
                            <Typography variant={'body5'} color={'text_60'}>
                                {t('changePasswordDialog.forgotPassword')}
                            </Typography>
                        </StyledButton>
                    )}
                </Stack>
            </DialogContent>
            <DialogActions>
                <StyledButton
                    onClick={onClose}
                    color={'btn_border'}
                    variant="outlined"
                    sx={{ minWidth: { xs: '100%', tablet: 80 }, height: 40 }}
                    size="small"
                >
                    <Typography variant={'body5'} color={'text_100'}>
                        {t('common.button.cancel')}
                    </Typography>
                </StyledButton>
                <StyledButton
                    color={'btn_point_bg'}
                    variant="contained"
                    sx={{ minWidth: { xs: '100%', tablet: 80 }, height: 40 }}
                    size="small"
                    onClick={handleChangePassword}
                    disabled={isDisabled}
                >
                    <Typography variant={'body5'} color={'white'}>
                        {t('common.button.change')}
                    </Typography>
                </StyledButton>
            </DialogActions>
        </Dialog>
    );
}

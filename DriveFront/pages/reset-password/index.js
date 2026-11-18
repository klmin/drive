import React, { useCallback, useEffect, useState } from 'react';
import {
    Box,
    CircularProgress,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    IconButton,
    InputAdornment,
    Stack,
    Typography,
} from '@mui/material';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import VisibilityOffOutlinedIcon from '@mui/icons-material/VisibilityOffOutlined';
import StyledTextField from '@/components/common/input/StyledTextField';
import StyledButton from '@/components/common/button/StyledButton';
import { useTranslations } from 'next-intl';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import api from '@/utils/api';
import { useRouter } from '@/hooks/useRouter';
import { ROUTES } from '@/constants/routes';

export default function ResetPasswordPage() {
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    const router = useRouter();
    const { t: tokenParam } = router.query;

    const [tokenValid, setTokenValid] = useState(null); // null: 검사중, true: 유효, false: 무효
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [showNewPassword, setShowNewPassword] = useState(false);
    const [showConfirmPassword, setShowConfirmPassword] = useState(false);
    const [errorNewPassword, setErrorNewPassword] = useState('');
    const [errorConfirmPassword, setErrorConfirmPassword] = useState('');
    const [isDisabled, setIsDisabled] = useState(true);

    const PASSWORD_REGEX = /^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^\w\s]).{8,64}$/;

    // 토큰 유효성 검사
    useEffect(() => {
        if (!tokenParam) return;
        const checkToken = async () => {
            const res = await api.Get(`users/password/reset-link/verify`, {
                params: {
                    token: tokenParam,
                },
            });
            setTokenValid(res?.data?.code === 200);
        };
        checkToken();
    }, [tokenParam]);

    useEffect(() => {
        if (
            errorNewPassword === '' &&
            errorConfirmPassword === '' &&
            newPassword !== '' &&
            confirmPassword !== ''
        ) {
            setIsDisabled(false);
        } else {
            setIsDisabled(true);
        }
    }, [newPassword, confirmPassword, errorNewPassword, errorConfirmPassword]);

    const checkPassword = (_password, type) => {
        const typeBySetter =
            type === 'confirm' ? setErrorConfirmPassword : type === 'new' && setErrorNewPassword;
        if (!typeBySetter) return;

        if (!PASSWORD_REGEX.test(_password)) {
            return typeBySetter(t('common.validate.password'));
        }

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

        return typeBySetter('');
    };

    const handleSubmit = useCallback(async () => {
        const res = await api.Post(`users/password/reset-link/confirm`, {
            token: tokenParam,
            newPwd: newPassword,
        });
        console.log('비밀번호 재설정 결과: ', res);
        if (res?.code === 200) {
            showSnackbar(t('changePasswordDialog.change.success'), 'success');
            router.replace(ROUTES.LOGIN);
        } else {
            showSnackbar(t('changePasswordDialog.change.fail'), 'error');
        }
    }, [tokenParam, newPassword]);

    // 토큰 검사 중
    if (tokenValid === null) {
        return (
            <Box
                sx={{
                    width: '100vw',
                    height: '100vh',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                }}
            >
                <CircularProgress />
            </Box>
        );
    }

    return (
        <Box
            sx={{
                width: '100vw',
                height: '100vh',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                backgroundColor: 'background.default',
            }}
        >
            <Dialog
                open
                hideBackdrop
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
                {tokenValid === false ? (
                    // 토큰 만료 / 무효
                    <DialogContent sx={{ p: 0 }}>
                        <Typography variant="body1" color="text_80">
                            {t('changePasswordDialog.expiredTitle')}
                        </Typography>
                    </DialogContent>
                ) : (
                    // 토큰 유효 → isAdmin 형태와 동일 (현재 비밀번호 입력 없음)
                    <>
                        <DialogTitle
                            sx={{
                                p: 0,
                                display: 'flex',
                                justifyContent: 'space-between',
                                alignItems: 'center',
                            }}
                        >
                            <Typography variant="body1">
                                {t('common.title.changePassword')}
                            </Typography>
                        </DialogTitle>
                        <DialogContent sx={{ p: 0, mt: '16px', overflow: 'visible' }}>
                            <Stack direction={'column'} spacing={'16px'}>
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
                                                    onClick={() =>
                                                        setShowNewPassword(prev => !prev)
                                                    }
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
                                                    onClick={() =>
                                                        setShowConfirmPassword(prev => !prev)
                                                    }
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
                            </Stack>
                        </DialogContent>
                        <DialogActions>
                            <StyledButton
                                color={'btn_point_bg'}
                                variant="contained"
                                sx={{ minWidth: { xs: '100%', tablet: 80 }, height: 40 }}
                                size="small"
                                onClick={handleSubmit}
                                disabled={isDisabled}
                            >
                                <Typography variant={'body5'} color={'white'}>
                                    {t('common.button.change')}
                                </Typography>
                            </StyledButton>
                        </DialogActions>
                    </>
                )}
            </Dialog>
        </Box>
    );
}

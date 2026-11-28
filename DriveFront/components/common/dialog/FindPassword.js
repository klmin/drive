import React, { useCallback, useState } from 'react';
import {
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Typography,
    Stack,
    CircularProgress,
} from '@mui/material';
import StyledTextField from '@/components/common/input/StyledTextField';
import StyledButton from '@/components/common/button/StyledButton';
import { useTranslations } from 'next-intl';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import api from '@/utils/api';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';

export default function FindPassword({ open, onClose, ...props }) {
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    const { type } = props;

    const [email, setEmail] = useState('');
    const [error, setError] = useState(''); // 에러가 없으면 ''로 두고, 아래 커스텀 헬퍼가 기본 문구 표시
    const [sending, setSending] = useState(false);

    const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

    // 공용 검증 함수
    const validateEmail = value => {
        console.log(value);
        if (!value) return t('common.validate.emptyCheck');
        if (!EMAIL_REGEX.test(value)) return t('common.validate.emailPattern'); // 예: "이메일 형식이 올바르지 않습니다."
        return '';
    };

    const sendEmail = useCallback(async () => {
        // 전송 직전 최종 검증
        const msg = validateEmail(email);
        if (msg) {
            setError(msg);
            return;
        }

        setSending(true);

        try {
            const res = await api.Post(`users/password/reset-link`, { userId: email });
            if (res?.code === 200) {
                showSnackbar(t('passwordResetEmail.success'), 'success');
                onClose();
            } else if (res?.errorCode === 'REQUEST_013') {
                // 요청 수 제한(429)
                showSnackbar(t('passwordResetEmail.tooManyRequests'), 'error');
            } else {
                showSnackbar(t('passwordResetEmail.fail'), 'error');
            }
        } catch (e) {
            showSnackbar(t('passwordResetEmail.fail'), 'error');
        } finally {
            setSending(false);
        }
    }, [email]);

    const handleKeyDown = async e => {
        if (e.key === 'Enter') {
            e.preventDefault();
            await sendEmail();
        }
    };

    const handleBlur = () => {
        setError(validateEmail(email));
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
            <DialogTitle
                component="div"
                sx={{
                    p: 0,
                    display: 'flex',
                    justifyContent: 'space-between',
                    flexDirection: 'row',
                }}
            >
                <Typography variant="h4">{t('passwordResetDialog.title')}</Typography>
                {sending && <CircularProgress size={30} color="inherit" />}
            </DialogTitle>

            <DialogContent sx={{ p: 0, mt: 2, overflow: 'visible' }}>
                <Typography variant="body5" color={'text_80'} sx={{ whiteSpace: 'pre-line' }}>
                    {t('passwordResetDialog.description')}
                </Typography>

                <Stack sx={{ mt: 2.5 }} spacing={1}>
                    <StyledTextField
                        type="text"
                        placeholder={t('login.inputEmail')}
                        size="small"
                        variant="outlined"
                        sx={{ width: '100%' }}
                        height={40}
                        value={email}
                        error={!!error}
                        errorMessage={error}
                        onChange={e => {
                            const v = e.target.value;
                            setEmail(v);
                            // 입력할 때마다 실시간 검증
                            setError(validateEmail(v));
                        }}
                        onBlur={handleBlur}
                        onKeyDown={handleKeyDown}
                    />
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
                    size="small"
                    sx={{ minWidth: { xs: '100%', tablet: 80 }, height: 40 }}
                    onClick={sendEmail}
                    disabled={error}
                >
                    <Typography variant={'body5'} color={'white'}>
                        {t('common.button.confirm')}
                    </Typography>
                </StyledButton>
            </DialogActions>
        </Dialog>
    );
}

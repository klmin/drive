import React, { useEffect, useState } from 'react';
import {
    Box,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControl,
    FormLabel,
    IconButton,
    Stack,
    Typography,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import StyledButton from '@/components/common/button/StyledButton';
import StyledTextField from '@/components/common/input/StyledTextField';
import StyledDatePicker from '@/components/common/input/StyledDatePicker';
import palette from '@/theme/palette';
import typography from '@/theme/typography';
import api from '@/utils/api';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { useTranslations } from 'next-intl';
import { copyTextToClipboard } from '@/utils/clipboard';
import dayjs from 'dayjs';

/** 만료일 기본값 — 발급 시점 기준 90일 */
const DEFAULT_EXPIRE_DAYS = 90;

/** 관리자 키는 scope 를 고르지 않고 전체 권한으로 발급한다. */
const FULL_SCOPES = ['api:read', 'api:write', 'mcp:read', 'mcp:write'];

const INITIAL_FORM = {
    keyName: '',
    purpose: '',
};

const textFieldSx = {
    width: '100%',
    '& .MuiInputBase-input': {
        color: palette.text_80,
    },
};

const formLabelSx = {
    ...typography.body5,
    color: palette.text_80,
    '& .MuiFormLabel-asterisk': {
        color: palette.status_error,
        fontWeight: typography.body4.fontWeight,
    },
};

function requireField(value, message) {
    if (!value.trim()) throw new Error(message);
}

/**
 * 관리자 API Key 발급 다이얼로그 — scope 선택 없이 전체 권한으로 발급한다.
 */
export default function AddAdminApiKeyDialog({ open, onClose, ...props }) {
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    const { reload } = props;

    const [step, setStep] = useState('form'); // 'form' | 'complete'
    const [createdApiKey, setCreatedApiKey] = useState('');

    const [formData, setFormData] = useState(INITIAL_FORM);
    const [expireDate, setExpireDate] = useState(null);
    const [isDisabled, setIsDisabled] = useState(true);

    const [errorMessageKeyName, setErrorMessageKeyName] = useState('');
    const [errorMessagePurpose, setErrorMessagePurpose] = useState('');

    useEffect(() => {
        if (open) {
            setStep('form');
            setCreatedApiKey('');
            setFormData(INITIAL_FORM);
            setExpireDate(dayjs().add(DEFAULT_EXPIRE_DAYS, 'day'));
            setErrorMessageKeyName('');
            setErrorMessagePurpose('');
        }
    }, [open]);

    useEffect(() => {
        const valid =
            errorMessageKeyName === '' &&
            errorMessagePurpose === '' &&
            formData.keyName.trim() !== '' &&
            formData.purpose.trim() !== '' &&
            // 지난 날짜는 서버가 거부하므로 오늘 이후만 허용
            expireDate?.isValid?.() === true &&
            !expireDate.endOf('day').isBefore(dayjs());
        setIsDisabled(!valid);
    }, [formData, expireDate, errorMessageKeyName, errorMessagePurpose]);

    const handleChange = e => {
        const { name, value } = e.target;
        setFormData(prev => ({ ...prev, [name]: value }));

        try {
            if (name === 'keyName') {
                requireField(value, t('adminApiKeyDialog.error.keyName'));
                setErrorMessageKeyName('');
            }
            if (name === 'purpose') {
                requireField(value, t('adminApiKeyDialog.error.purpose'));
                setErrorMessagePurpose('');
            }
        } catch (error) {
            if (name === 'keyName') setErrorMessageKeyName(error.message);
            if (name === 'purpose') setErrorMessagePurpose(error.message);
        }
    };

    const handleSave = async () => {
        const res = await api.Post('admins/api-keys', {
            keyName: formData.keyName,
            keyPurpose: formData.purpose,
            scopes: FULL_SCOPES,
            // 날짜만 보내고 만료 시각(그 날 23:59:59)은 서버가 정한다
            expireDate: expireDate.format('YYYY-MM-DD'),
        });

        if (res?.code === 201) {
            setCreatedApiKey(res?.data?.apiKey ?? '');
            setStep('complete');
            await reload();
        } else {
            showSnackbar(t('adminApiKeyDialog.addFail'), 'error');
        }
    };

    const handleCopy = async () => {
        const success = await copyTextToClipboard(
            createdApiKey,
            t('shareClipboardPrompt.description')
        );
        showSnackbar(
            t(success ? 'adminApiKeyDialog.copySuccess' : 'clipboard.copyFail'),
            success ? 'success' : 'error'
        );
    };

    return (
        <Dialog
            open={open}
            onClose={onClose}
            disableEnforceFocus
            PaperProps={{
                sx: {
                    borderRadius: 4,
                    pt: '32px',
                    px: '32px',
                    pb: '24px',
                    margin: 0,
                    maxWidth: '480px !important',
                    width: '100%',
                },
            }}
        >
            {step === 'form' ? (
                <>
                    {/* 제목 + 닫기 버튼 */}
                    <DialogTitle
                        sx={{
                            p: 0,
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                        }}
                    >
                        <Typography variant="body1" color="text_100">
                            {t('adminApiKeyDialog.addTitle')}
                        </Typography>
                        <IconButton onClick={onClose} size="small" sx={{ p: 0.5 }}>
                            <CloseIcon fontSize="small" />
                        </IconButton>
                    </DialogTitle>

                    <DialogContent sx={{ p: 0, mt: 4, overflow: 'visible' }}>
                        <Stack sx={{ pt: '4px', gap: '16px' }}>
                            {/* Key 이름 */}
                            <FormControl fullWidth sx={{ gap: '10px' }}>
                                <FormLabel required sx={formLabelSx}>
                                    {t('adminApiKeyDialog.keyName')}
                                </FormLabel>
                                <StyledTextField
                                    name="keyName"
                                    value={formData.keyName}
                                    onChange={handleChange}
                                    placeholder={t('adminApiKeyDialog.error.keyName')}
                                    error={!!errorMessageKeyName}
                                    errorMessage={errorMessageKeyName}
                                    variant="outlined"
                                    height={40}
                                    sx={textFieldSx}
                                />
                            </FormControl>

                            {/* 사용 용도 */}
                            <FormControl fullWidth sx={{ gap: '10px' }}>
                                <FormLabel required sx={formLabelSx}>
                                    {t('adminApiKeyDialog.purpose')}
                                </FormLabel>
                                <StyledTextField
                                    name="purpose"
                                    value={formData.purpose}
                                    onChange={handleChange}
                                    placeholder={t('adminApiKeyDialog.error.purpose')}
                                    error={!!errorMessagePurpose}
                                    errorMessage={errorMessagePurpose}
                                    variant="outlined"
                                    height={40}
                                    sx={textFieldSx}
                                />
                            </FormControl>

                            {/* 만료일 */}
                            <FormControl fullWidth sx={{ gap: '10px' }}>
                                <FormLabel required sx={formLabelSx}>
                                    {t('admin.apiKey.createDialog.expiryLabel')}
                                </FormLabel>
                                <StyledDatePicker
                                    value={expireDate}
                                    setValue={setExpireDate}
                                    minDate={dayjs()}
                                    fullWidth
                                    height={40}
                                    borderRadius={4}
                                    iconSize={20}
                                />
                            </FormControl>
                        </Stack>
                    </DialogContent>

                    <DialogActions sx={{ p: 0 }}>
                        <StyledButton
                            onClick={onClose}
                            color="btn_border"
                            variant="outlined"
                            sx={{ minWidth: { xs: '100%', tablet: 80 } }}
                        >
                            <Typography variant="body5" color="text_100">
                                {t('common.button.cancel')}
                            </Typography>
                        </StyledButton>
                        <StyledButton
                            color="btn_point_bg"
                            variant="contained"
                            sx={{ minWidth: { xs: '100%', tablet: 80 } }}
                            onClick={handleSave}
                            disabled={isDisabled}
                        >
                            <Typography variant="body5" color="white">
                                {t('common.button.create')}
                            </Typography>
                        </StyledButton>
                    </DialogActions>
                </>
            ) : (
                <>
                    {/* 완료 화면 제목 */}
                    <DialogTitle sx={{ p: 0 }}>
                        <Typography variant="body1">
                            {t('adminApiKeyDialog.completeTitle')}
                        </Typography>
                    </DialogTitle>

                    <DialogContent sx={{ p: 0, mt: 4 }}>
                        {/* 안내 박스 */}
                        <Box
                            sx={{
                                backgroundColor: 'rgba(250, 255, 0, 0.11)',
                                borderRadius: '16px',
                                border: '1px solid #FFC130',
                                pt: '20px',
                                pb: '20px',
                                px: '32px',
                                mb: 4,
                                boxShadow: '0px 1px 4px rgba(0, 0, 0, 0.1)',
                                display: 'flex',
                                flexDirection: 'column',
                                gap: '24px',
                            }}
                        >
                            <Typography
                                variant="body5"
                                color="text_80"
                                sx={{ whiteSpace: 'pre-line' }}
                            >
                                {t('adminApiKeyDialog.completeNotice')}
                            </Typography>
                        </Box>

                        {/* API Key 표시 */}
                        <FormControl fullWidth>
                            <FormLabel>{t('adminApiKeyDialog.apiKey')}</FormLabel>
                            <Box
                                sx={{
                                    display: 'flex',
                                    flexDirection: 'row',
                                    alignItems: 'center',
                                    gap: '10px',
                                    borderRadius: '16px',
                                    border: '1px solid #000000',
                                    pt: '20px',
                                    pb: '20px',
                                    px: '32px',
                                    boxShadow: '0px 1px 10px 0px rgba(58, 61, 73, 0.12)',
                                }}
                            >
                                <StyledTextField
                                    value={createdApiKey}
                                    inputProps={{ readOnly: true }}
                                    variant="outlined"
                                    height={40}
                                    sx={{
                                        flex: 1,
                                        minWidth: 0,
                                        '& .MuiInputBase-input': {
                                            color: palette.text_80,
                                        },
                                        '& .MuiOutlinedInput-notchedOutline': {
                                            borderColor: palette.control_border_normal,
                                        },
                                        '& .MuiInputBase-root:hover .MuiOutlinedInput-notchedOutline':
                                            {
                                                borderColor: palette.control_border_normal,
                                            },
                                        '& .MuiInputBase-root.Mui-focused .MuiOutlinedInput-notchedOutline':
                                            {
                                                borderColor: palette.control_border_normal,
                                                borderWidth: '1px',
                                            },
                                    }}
                                />
                                <StyledButton
                                    color="btn_point_bg"
                                    variant="contained"
                                    onClick={handleCopy}
                                    sx={{ minWidth: { xs: '100%', tablet: 72 }, height: 40 }}
                                >
                                    <Typography variant="body5" color="white">
                                        {t('adminApiKeyDialog.copy')}
                                    </Typography>
                                </StyledButton>
                            </Box>
                        </FormControl>
                    </DialogContent>

                    <DialogActions sx={{ p: 0, mt: 4, justifyContent: 'stretch' }}>
                        <StyledButton
                            onClick={onClose}
                            color="btn_border"
                            variant="outlined"
                            fullWidth
                            sx={{
                                height: '40px',
                                width: '100%',
                                minWidth: '80px',
                                borderRadius: '4px',
                                pt: '8px',
                                pb: '8px',
                                px: '16px',
                                gap: '10px',
                                backgroundColor: palette.btn_white_bg_normal,
                            }}
                        >
                            <Typography variant="body5" color="text_100">
                                {t('adminApiKeyDialog.confirm')}
                            </Typography>
                        </StyledButton>
                    </DialogActions>
                </>
            )}
        </Dialog>
    );
}

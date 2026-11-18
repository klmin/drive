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
import StyledRadioButton from '@/components/common/button/StyledRadioButton';
import StyledDatePicker from '@/components/common/input/StyledDatePicker';
import palette from '@/theme/palette';
import typography from '@/theme/typography';
import api from '@/utils/api';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { useTranslations } from 'next-intl';
import dayjs from 'dayjs';

/** 만료일 기본값 — 발급 시점 기준 90일 */
const DEFAULT_EXPIRE_DAYS = 90;

/**
 * 발급 화면에서 고를 수 있는 scope 조합 — 백엔드 ApiKeyScope.ALLOWED_COMBINATIONS 와 같아야 한다.
 */
const SCOPE_OPTIONS = [
    { key: 'readOnly', scopes: ['api:read', 'mcp:read'] },
    { key: 'fullAccess', scopes: ['api:read', 'api:write', 'mcp:read', 'mcp:write'] },
];

const INITIAL_FORM = {
    keyName: '',
    purpose: '',
    scopeKey: '',
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

/** 카드용 라디오 — 아이콘 20x20 */
const scopeRadioSx = {
    p: 0,
    width: 20,
    height: 20,
    '& .MuiSvgIcon-root': {
        fontSize: 20,
    },
};

/** 라디오 + 제목 + 설명으로 이루어진 scope 선택 카드 */
function ScopeCard({ selected, title, description, onSelect }) {
    return (
        <Box
            onClick={onSelect}
            sx={{
                display: 'flex',
                alignItems: 'flex-start',
                gap: '8px',
                width: '100%',
                p: '12px',
                borderRadius: '6px',
                cursor: 'pointer',
                boxSizing: 'border-box',
                // border는 높이에 2px 더해져 시안(80px)과 어긋나므로 outline으로 그린다.
                outline: `1px solid ${selected ? palette.control_border_selected : palette.divider_8}`,
                outlineOffset: '-1px',
                '&:hover': {
                    outlineColor: selected
                        ? palette.control_border_selected
                        : palette.control_border_hover,
                },
            }}
        >
            <StyledRadioButton checked={selected} onChange={onSelect} sx={scopeRadioSx} />
            <Stack sx={{ flex: 1, gap: '4px', pb: '2px' }}>
                <Typography variant="body6" color="text_80">
                    {title}
                </Typography>
                <Typography variant="body9" color="text_60" sx={{ whiteSpace: 'pre-line' }}>
                    {description}
                </Typography>
            </Stack>
        </Box>
    );
}

function requireField(value, message) {
    if (!value.trim()) throw new Error(message);
}

/**
 * 사용자 본인 API Key 발급 다이얼로그 — 발급된 키 원문은 호출 측의 완료 다이얼로그가 보여준다.
 * 대상 계정은 로그인 사용자 본인으로 서버가 고정한다. 슈퍼관리자는 AddSuperAdminApiKeyDialog 를 쓴다.
 */
export default function AddUserApiKeyDialog({ open, onClose, ...props }) {
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    const { onCreated, reload } = props;

    const [formData, setFormData] = useState(INITIAL_FORM);
    const [expireDate, setExpireDate] = useState(null);
    const [isDisabled, setIsDisabled] = useState(true);

    const [errorMessageKeyName, setErrorMessageKeyName] = useState('');
    const [errorMessagePurpose, setErrorMessagePurpose] = useState('');

    useEffect(() => {
        if (open) {
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
            formData.scopeKey !== '' &&
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
        const selectedScope = SCOPE_OPTIONS.find(option => option.key === formData.scopeKey);

        const res = await api.Post('users/api-keys', {
            keyName: formData.keyName,
            keyPurpose: formData.purpose,
            scopes: selectedScope?.scopes,
            // 날짜만 보내고 만료 시각(그 날 23:59:59)은 서버가 정한다
            expireDate: expireDate.format('YYYY-MM-DD'),
        });

        if (res?.code === 201) {
            await reload?.();
            onClose?.();
            onCreated?.(res?.data?.apiKey ?? '');
        } else {
            showSnackbar(t('adminApiKeyDialog.addFail'), 'error');
        }
    };

    return (
        <Dialog
            open={open}
            onClose={onClose}
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

                    {/* Scope — 읽기 전용 / 전체 권한 중 하나 필수 */}
                    <FormControl fullWidth sx={{ gap: '10px' }}>
                        <FormLabel required sx={formLabelSx}>
                            {t('admin.apiKey.createDialog.scopeLabel')}
                        </FormLabel>
                        <Stack sx={{ gap: '8px' }}>
                            {SCOPE_OPTIONS.map(option => (
                                <ScopeCard
                                    key={option.key}
                                    selected={formData.scopeKey === option.key}
                                    title={t(`admin.apiKey.scope.${option.key}`)}
                                    description={t(`admin.apiKey.scope.${option.key}Desc`)}
                                    onSelect={() =>
                                        setFormData(prev => ({ ...prev, scopeKey: option.key }))
                                    }
                                />
                            ))}
                        </Stack>
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
        </Dialog>
    );
}

import React, { useEffect, useRef, useState } from 'react';
import {
    Avatar,
    Box,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControl,
    FormLabel,
    Typography,
    IconButton,
    InputAdornment,
    Stack,
} from '@mui/material';
import StyledButton from '@/components/common/button/StyledButton';
import StyledTextField from '@/components/common/input/StyledTextField';
import QuotaInput from '@/components/common/input/QuotaInput';
import RoleCheckboxGroup from '@/components/common/input/RoleCheckboxGroup';
import { useTranslations } from 'next-intl';
import api from '@/utils/api';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import {
    emptyCheck,
    trimEdgeSpaces,
    validateDuplicate,
    validateEmailPattern,
    validateNoEdgeSpaces,
    validatePassword,
    validatePhoneAndCountry,
    validateProfileImage,
    validateUserName,
    PROFILE_IMAGE_ACCEPT,
} from '@/utils/userUtils';
import { FieldErrorMessage } from '@/components/common/input/StyledTextField';
import { convertSizeToBytes } from '@/utils/math';
import {
    bytesToGbNumber,
    quotaErrorMessage,
    userAvailableQuotaByte,
    validateUserAvailableQuota,
    validateUserQuota,
} from '@/utils/quota';
import { useRouter } from '@/hooks/useRouter';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import VisibilityOffOutlinedIcon from '@mui/icons-material/VisibilityOffOutlined';
import palette from '@/theme/palette';
import CountrySelect from '../input/CountrySelect';
import { getDialCodeDigits } from '@/utils/format';
import { check } from 'prettier';

const textFieldSx = {
    width: '100%',
    pt: 1,
    '& .MuiInputBase-input': {
        color: palette.text_80,
    },
};

export default function RegisterUserDialog({ open, onClose }) {
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();

    const [errorMessageUserId, setErrorMessageUserId] = useState('');
    const [errorMessagePwd, setErrorMessagePwd] = useState('');
    const [errorMessageUsername, setErrorMessageUsername] = useState('');
    const [errorMessageQuota, setErrorMessageQuota] = useState('');
    const [errorMessagePhone, setErrorMessagePhone] = useState('');
    const [isDisabled, setIsDisabled] = useState(true);
    const [previewUrl, setPreviewUrl] = useState(null);
    const [errorMessageProfileImage, setErrorMessageProfileImage] = useState('');
    // ID 검사 요청 번호 — 마지막 요청의 응답만 반영(A→B→A 처럼 같은 값을 다시 입력한 경우도 구분)
    const userIdCheckSeqRef = useRef(0);
    const [showPassword, setShowPassword] = useState(null);

    const router = useRouter();

    // 역할은 이 다이얼로그에서 고른다 — 처음엔 사용자(USER)만 체크(가장 흔한 경우)
    const [formData, setFormData] = useState({
        userId: '',
        encPwd: '',
        userName: '',
        totalQuotaByte: '1',
        totalQuotaUnit: 'GB',
        roles: ['USER'],
        countryCode: null,
        phoneNumber: '',
        profileImageFile: null,
    });

    // 용량은 USER 역할이 있을 때만 — ADMIN 만이면 할당 용량 없음(서버가 0 으로 저장)
    const hasUserRole = formData.roles.includes('USER');

    // 테넌트 총 용량·사용자 할당량 합 — 조회 실패면 null(할당 가능 용량 표시·상한 판정 생략)
    const [quotaCapacity, setQuotaCapacity] = useState(null);
    const availableQuotaByte = userAvailableQuotaByte(quotaCapacity);

    const fetchQuotaCapacity = async () => {
        const res = await api.Get('admins/system-config/storage-quota', {});
        if (res?.data?.code === 200) setQuotaCapacity(res?.data?.data ?? null);
    };

    useEffect(() => {
        if (open) fetchQuotaCapacity();
    }, [open]);

    // 형식·사용량 → 할당 가능 용량 순으로 판정
    const checkQuota = (value, unit, available = availableQuotaByte) => {
        validateUserQuota(value, unit, 0, t);
        validateUserAvailableQuota(convertSizeToBytes(value, unit), available, 0, t);
    };

    // 할당 가능 용량을 늦게 받으면 이미 입력한 값을 다시 판정
    useEffect(() => {
        if (availableQuotaByte == null || !hasUserRole || formData.totalQuotaByte === '') return;
        try {
            checkQuota(formData.totalQuotaByte, formData.totalQuotaUnit, availableQuotaByte);
            setErrorMessageQuota('');
        } catch (error) {
            setErrorMessageQuota(error.message);
        }
    }, [quotaCapacity]);

    useEffect(() => {
        const isCommonValid =
            errorMessageUserId === '' &&
            errorMessagePwd === '' &&
            errorMessageUsername === '' &&
            errorMessageQuota === '' &&
            errorMessagePhone === '' &&
            formData.userId !== '' &&
            formData.encPwd !== '' &&
            formData.userName !== '' &&
            formData.roles.length > 0;

        const quota = Number(formData.totalQuotaByte);
        const isQuotaValid = hasUserRole ? formData.totalQuotaByte !== '' && quota > 0 : true;

        if (isCommonValid && isQuotaValid) {
            setIsDisabled(false);
        } else {
            setIsDisabled(true);
        }
    }, [formData, hasUserRole, errorMessageQuota, errorMessageUserId]);

    // 단위 변경 — 환산하지 않고 빈칸(저장 비활성, 오류 문구는 띄우지 않음)
    const handleQuotaUnitChange = unit => {
        setFormData(prev => ({ ...prev, totalQuotaByte: '', totalQuotaUnit: unit }));
        setErrorMessageQuota('');
    };

    // USER 를 빼면 용량 칸이 사라지므로 그 칸의 오류 문구도 지운다(저장 버튼이 숨은 오류로 막히지 않게).
    // USER 를 다시 넣으면 남아 있던 용량 값을 다시 검사한다 — 빼면서 지운 오류가 다시 드러나게(빈칸이면 문구 없이 저장만 비활성)
    const handleRolesChange = roles => {
        setFormData(prev => ({ ...prev, roles }));
        if (!roles.includes('USER')) {
            setErrorMessageQuota('');
            return;
        }
        if (!formData.roles.includes('USER') && formData.totalQuotaByte !== '') {
            try {
                checkQuota(formData.totalQuotaByte, formData.totalQuotaUnit);
                setErrorMessageQuota('');
            } catch (error) {
                setErrorMessageQuota(error.message);
            }
        }
    };

    const handleChange = async e => {
        const { name, value, files } = e.target;

        if (name === 'profileImage') {
            const file = files[0];
            e.target.value = ''; // 같은 파일 재선택도 변경으로 잡기 위해 초기화
            if (file) {
                // 서버 기준에 안 맞는 파일은 받지 않고 이미지를 비운 뒤 문구 표시
                try {
                    validateProfileImage(file, t);
                } catch (error) {
                    setErrorMessageProfileImage(error.message);
                    setPreviewUrl(null);
                    setFormData(prev => ({
                        ...prev,
                        profileImageFile: null,
                    }));
                    return;
                }
                setErrorMessageProfileImage('');
                setPreviewUrl(URL.createObjectURL(file));
                setFormData(prev => ({
                    ...prev,
                    profileImageFile: file,
                }));
            }
            return; // 여기서 끝내줌
        }

        let checkValue =
            name === 'countryCode' || name === 'userName' ? value : trimEdgeSpaces(value);

        setFormData(prev => ({
            ...prev,
            [name]: checkValue,
        }));

        const userIdCheckSeq = name === 'userId' ? ++userIdCheckSeqRef.current : 0;

        try {
            // 순서대로 검사
            // user ID
            if (name === 'userId') {
                emptyCheck(checkValue, t);
                validateEmailPattern(checkValue, t);
                await validateDuplicate(checkValue, t);
                if (userIdCheckSeq !== userIdCheckSeqRef.current) return;
                setErrorMessageUserId('');
            }
            if (name === 'encPwd') {
                emptyCheck(checkValue, t);
                validatePassword(checkValue, t);
                setErrorMessagePwd('');
            }
            if (name === 'userName') {
                emptyCheck(checkValue, t);
                validateUserName(checkValue, t);
                validateNoEdgeSpaces(checkValue, t);
                setErrorMessageUsername('');
            }
            if (name === 'totalQuotaByte') {
                emptyCheck(checkValue, t);
                checkQuota(checkValue, formData.totalQuotaUnit);
                setErrorMessageQuota('');
            }
            if (name === 'phoneNumber' || name === 'countryCode') {
                const phone = name === 'phoneNumber' ? checkValue : formData.phoneNumber;
                const code =
                    name === 'countryCode' ? checkValue : formData.countryCode?.value || '';
                validatePhoneAndCountry(phone, code, t);
                setErrorMessagePhone('');
            }
        } catch (error) {
            if (name === 'userId' && userIdCheckSeq === userIdCheckSeqRef.current) {
                setErrorMessageUserId(error.message);
            }
            if (name === 'encPwd') {
                setErrorMessagePwd(error.message);
            }
            if (name === 'userName') {
                setErrorMessageUsername(error.message);
            }
            if (name === 'totalQuotaByte') {
                setErrorMessageQuota(error.message);
            }
            if (name === 'phoneNumber' || name === 'countryCode') {
                setErrorMessagePhone(error.message);
            }
        }
    };

    const handleCreateUser = async () => {
        console.log('formDate', formData);
        const res = await api.PostForm(`admins/users`, {
            ui: formData.userId,
            encPwd: formData.encPwd,
            un: formData.userName,
            // USER 역할이 없으면 할당 용량 없음 — tq 를 보내지 않으면 서버가 0 으로 저장
            tq: hasUserRole
                ? convertSizeToBytes(formData.totalQuotaByte, formData.totalQuotaUnit)
                : null,
            roles: formData.roles.join(','),
            ct: formData.countryCode ? getDialCodeDigits(formData.countryCode.value) : null,
            pn: formData.phoneNumber,
            pif: formData.profileImageFile || null,
        });
        console.log(res);
        if (res?.status === 201) {
            showSnackbar(t('adminAccounts.register.success'), 'success');
            router.reload();
        } else {
            const quotaError = hasUserRole ? quotaErrorMessage(res?.errorCode, t) : null;
            if (quotaError) setErrorMessageQuota(quotaError.inline);
            // 중복 확인 뒤 다른 관리자가 같은 ID 를 먼저 등록한 경우 — 입력 중 중복 확인과 같은 문구
            if (res?.errorCode === 'USER_004') {
                const duplicateMessage = t('common.validate.duplicateUserId');
                setErrorMessageUserId(duplicateMessage);
                showSnackbar(duplicateMessage, 'error');
                return;
            }
            // 단건 등록의 USER_013 은 좌석(라이선스) 부족뿐
            const failToast =
                res?.errorCode === 'USER_013'
                    ? t('admin.userManagement.toast.seatLimitReached')
                    : t('adminAccounts.register.fail');
            showSnackbar(quotaError?.toast ?? failToast, 'error');
        }
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
                    maxWidth: '500px !important',
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
                <Typography variant="body1">{t('common.title.addAccount')}</Typography>
            </DialogTitle>
            <DialogContent sx={{ p: 0, mt: 4 }}>
                <Box>
                    {/** profileImage */}
                    <Box
                        sx={{
                            display: 'flex',
                            flexDirection: 'column',
                            alignItems: 'center',
                            mb: 3,
                        }}
                    >
                        <FormLabel sx={{ pb: 1.5 }}>{t(`editUserDialog.profileImage`)}</FormLabel>
                        <Avatar src={previewUrl} sx={{ width: 80, height: 80, mb: 1 }} />
                        <Box sx={{ display: 'flex', gap: 1 }}>
                            <StyledButton
                                size="small"
                                color="btn_border"
                                variant="outlined"
                                component="label"
                                sx={{ minWidth: { xs: '100%', tablet: 'auto' } }}
                            >
                                <Typography variant="body5" color="text_100">
                                    {t(`common.button.update`)}
                                </Typography>
                                <input
                                    hidden
                                    accept={PROFILE_IMAGE_ACCEPT}
                                    type="file"
                                    name="profileImage"
                                    onChange={handleChange}
                                />
                            </StyledButton>
                            <StyledButton
                                size="small"
                                color="btn_border"
                                variant="outlined"
                                onClick={() => {
                                    setPreviewUrl(null);
                                    setErrorMessageProfileImage('');
                                    setFormData(prev => ({
                                        ...prev,
                                        profileImageFile: null,
                                    }));
                                }}
                                sx={{ minWidth: { xs: '100%', tablet: 'auto' } }}
                            >
                                <Typography variant="body5" color="status_error">
                                    {t(`common.action.delete`)}
                                </Typography>
                            </StyledButton>
                        </Box>
                        {errorMessageProfileImage && (
                            <FieldErrorMessage message={errorMessageProfileImage} />
                        )}
                    </Box>

                    {/* userId */}
                    <FormControl fullWidth margin="normal">
                        <FormLabel required>{t(`common.label.userId`)}</FormLabel>
                        <StyledTextField
                            name="userId"
                            value={formData.userId}
                            onChange={handleChange}
                            error={!!errorMessageUserId}
                            errorMessage={errorMessageUserId}
                            variant="outlined"
                            height={40}
                            sx={textFieldSx}
                        />
                    </FormControl>

                    {/* encPwd */}
                    <FormControl fullWidth margin="normal">
                        <FormLabel required>{t(`common.label.password`)}</FormLabel>
                        <StyledTextField
                            name="encPwd"
                            type={showPassword ? 'text' : 'password'}
                            value={formData.encPwd}
                            onChange={handleChange}
                            error={!!errorMessagePwd}
                            errorMessage={errorMessagePwd}
                            variant="outlined"
                            height={40}
                            sx={textFieldSx}
                            InputProps={{
                                endAdornment: (
                                    <InputAdornment position="end">
                                        <IconButton
                                            onClick={() => setShowPassword(prev => !prev)}
                                            edge="end"
                                        >
                                            {showPassword ? (
                                                <VisibilityOutlinedIcon
                                                    size={20}
                                                    fontSize={'small'}
                                                />
                                            ) : (
                                                <VisibilityOffOutlinedIcon fontSize={'small'} />
                                            )}
                                        </IconButton>
                                    </InputAdornment>
                                ),
                            }}
                        />
                    </FormControl>

                    {/* userName */}
                    <FormControl fullWidth margin="normal">
                        <FormLabel required>{t(`common.label.userName`)}</FormLabel>
                        <StyledTextField
                            name="userName"
                            value={formData.userName}
                            onChange={handleChange}
                            error={!!errorMessageUsername}
                            errorMessage={errorMessageUsername}
                            variant="outlined"
                            height={40}
                            sx={textFieldSx}
                        />
                    </FormControl>

                    {/* roles */}
                    <FormControl fullWidth margin="normal">
                        <FormLabel required>{t(`AddUserDialog.roleType`)}</FormLabel>
                        <RoleCheckboxGroup roles={formData.roles} onChange={handleRolesChange} />
                    </FormControl>

                    {/* totalQuotaByte */}
                    {hasUserRole && (
                        <FormControl fullWidth margin="normal">
                            <FormLabel required>{t(`AddUserDialog.totalQuotaByte`)}</FormLabel>
                            <QuotaInput
                                name="totalQuotaByte"
                                value={formData.totalQuotaByte}
                                unit={formData.totalQuotaUnit}
                                onChange={handleChange}
                                onUnitChange={handleQuotaUnitChange}
                                error={!!errorMessageQuota}
                                errorMessage={errorMessageQuota}
                                sx={textFieldSx}
                            />
                            {/* 할당 가능 용량 — 조회 실패·총 용량 미설정이면 숨김 */}
                            {availableQuotaByte != null && (
                                <Typography variant="body7" color="text_60" sx={{ pt: 1 }}>
                                    {t('admin.userDialog.storageQuota.availableDesc', {
                                        availableQuota: bytesToGbNumber(
                                            Math.max(availableQuotaByte, 0)
                                        ),
                                    })}
                                </Typography>
                            )}
                        </FormControl>
                    )}

                    {/* phoneNumber */}
                    <FormControl fullWidth margin="normal">
                        <FormLabel>{t(`common.label.phone`)}</FormLabel>
                        <Stack direction="row" spacing={1} sx={{ pt: 1 }}>
                            {/* 국가 코드 선택 */}
                            <CountrySelect
                                countryCode={formData.countryCode}
                                handleChange={handleChange}
                            />
                            <StyledTextField
                                name="phoneNumber"
                                value={formData.phoneNumber}
                                placeholder={t('common.validate.numericOnly')}
                                onChange={e => {
                                    const el = e.target;
                                    const val = el.value;

                                    if (val === '' || /^([0-9]+|-1)$/.test(val)) {
                                        // 허용: 빈값, 숫자
                                        handleChange(e);
                                    } else {
                                        // 비허용: 한글/영문/특수기호 등 -> 빈값으로 치환 후 전달
                                        el.value = '';
                                        handleChange(e);
                                    }
                                }}
                                error={!!errorMessagePhone}
                                errorMessage={errorMessagePhone}
                                variant="outlined"
                                height={40}
                                sx={{
                                    flex: 1,
                                    '& .MuiInputBase-input': {
                                        color: palette.text_80,
                                    },
                                }}
                            />
                        </Stack>
                    </FormControl>
                </Box>
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
                        {t('common.button.close')}
                    </Typography>
                </StyledButton>
                <StyledButton
                    color={'btn_point_bg'}
                    variant="contained"
                    size="small"
                    sx={{ minWidth: { xs: '100%', tablet: 80 } }}
                    onClick={handleCreateUser}
                    disabled={isDisabled}
                >
                    <Typography variant={'body5'} color={'white'}>
                        {t('common.button.add')}
                    </Typography>
                </StyledButton>
            </DialogActions>
        </Dialog>
    );
}

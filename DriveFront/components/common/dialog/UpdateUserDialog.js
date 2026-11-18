import React, { useEffect, useState } from 'react';
import {
    Avatar,
    Box,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControl,
    FormLabel,
    Stack,
    Typography,
} from '@mui/material';
import StyledButton from '@/components/common/button/StyledButton';
import StyledTextField, { FieldErrorMessage } from '@/components/common/input/StyledTextField';
import QuotaInput from '@/components/common/input/QuotaInput';
import RoleCheckboxGroup from '@/components/common/input/RoleCheckboxGroup';
import { useTranslations } from 'next-intl';
import api from '@/utils/api';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import convertBytesToReadableSize from '@/utils/math';
import dayjs from 'dayjs';
import {
    emptyCheck,
    trimEdgeSpaces,
    validateNoEdgeSpaces,
    validatePhoneAndCountry,
    validateProfileImage,
    validateUserName,
    PROFILE_IMAGE_ACCEPT,
} from '@/utils/userUtils';
import {
    bytesToGbNumber,
    bytesToQuotaInput,
    isQuotaInputUnchanged,
    quotaErrorMessage,
    quotaValueForUnit,
    resolveQuotaBytes,
    userAvailableQuotaByte,
    validateUserAvailableQuota,
    validateUserQuota,
} from '@/utils/quota';
import { useRouter } from '@/hooks/useRouter';
import { useDialog } from '@/provider/DialogProvider';
import CountrySelect from '../input/CountrySelect';
import palette from '@/theme/palette';
import { getDialCodeDigits } from '@/utils/format';

// 역할 해제 거부 오류 → 안내 문구(다이얼로그 체크박스 잠금을 지나 서버가 거부한 경우)
const ROLE_ERROR_MESSAGE_KEYS = {
    USER_030: 'editUserDialog.roleUserInUse',
    USER_031: 'editUserDialog.roleSelfAdmin',
};

const textFieldSx = {
    width: '100%',
    pt: 1,
    '& .MuiInputBase-input': {
        color: palette.text_80,
    },
};

export default function UpdateUserDialog({ open, onClose, ...props }) {
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    const { item } = props;

    const [errorMessageUsername, setErrorMessageUsername] = useState('');
    const [errorMessageQuota, setErrorMessageQuota] = useState('');
    const [errorMessagePhone, setErrorMessagePhone] = useState('');
    const [isDisabled, setIsDisabled] = useState(true);
    const [user, setUser] = useState();
    // 조회한 할당 용량 — 입력을 건드리지 않았으면 이 값을 그대로 보낸다(MB 반올림 표시로 값이 바뀌지 않게)
    const [quotaOrigin, setQuotaOrigin] = useState({ bytes: null, value: '', unit: 'GB' });
    const [previewUrl, setPreviewUrl] = useState(null);
    const [errorMessageProfileImage, setErrorMessageProfileImage] = useState('');
    // 테넌트 총 용량·사용자 할당량 합 — 조회 실패면 null(할당 가능 용량 표시·상한 판정 생략)
    const [quotaCapacity, setQuotaCapacity] = useState(null);
    // 할당 가능 용량 — 대상 사용자의 현재 할당량은 합계에 이미 들어 있으므로 되돌려 더한다
    const currentQuotaByte = quotaOrigin.bytes ?? 0;
    const availableQuotaByte = userAvailableQuotaByte(quotaCapacity, currentQuotaByte);

    const router = useRouter();
    // 열 때 보유 역할 기준 — 제목·완료 문구·사용량 요약에만 쓴다. 용량 입력은 체크한 역할(hasUserRole) 기준
    const isAdmins = !(item?.roles ?? []).includes('USER');

    const { openDialog } = useDialog();

    const [formData, setFormData] = useState({
        userName: '',
        totalQuotaByte: '',
        totalQuotaUnit: 'GB',
        roles: item?.roles ?? [],
        countryCode: null,
        phoneNumber: '',
        profileImageFile: null,
        deleteProfileImage: false,
    });

    const getUser = async () => {
        const res = await api.Get(`admins/users/${item?.userSeq}`, {});

        if (res?.status === 200) {
            const data = res.data?.data;
            setUser(data);
            const { value: totalQuotaInput, unit: totalQuotaUnit } = bytesToQuotaInput(
                data?.totalQuotaByte
            );
            setQuotaOrigin({
                bytes: data?.totalQuotaByte ?? null,
                value: totalQuotaInput,
                unit: totalQuotaUnit,
            });
            setFormData({
                userName: data?.userName,
                totalQuotaByte: totalQuotaInput,
                totalQuotaUnit,
                roles: data?.roles ?? item?.roles ?? [],
                countryCode: !data?.countryCode
                    ? null
                    : {
                          name: '',
                          value: '+' + data?.countryCode,
                      },
                phoneNumber: data?.phoneNumber || '',
                deleteProfileImage: false,
            });
            // 할당 용량 0 은 과거 데이터 — 사용자 계정이면 1 이상으로 바꿔야 저장할 수 있다
            setErrorMessageQuota(
                !isAdmins && data?.totalQuotaByte === 0
                    ? t('userInfoValidation.validateStorageLimitNumber')
                    : ''
            );

            const profileImageUrl = api.getProfileImage(data?.userSeq);
            setPreviewUrl(profileImageUrl);
        }
    };

    const fetchQuotaCapacity = async () => {
        const res = await api.Get('admins/system-config/storage-quota', {});
        if (res?.data?.code === 200) setQuotaCapacity(res?.data?.data ?? null);
    };

    // 용량은 USER 역할이 있을 때만 — USER 를 빼면 서버가 할당 용량을 0 으로 저장
    const hasUserRole = formData.roles.includes('USER');

    // 해제할 수 없는 역할 — 서버도 같은 규칙으로 거부(USER_030·USER_031)
    const lockedRoles = {
        ...(user?.useQuotaByte > 0 && { USER: t('editUserDialog.roleUserInUse') }),
        ...(props.loginUserSeq === item?.userSeq && { ADMIN: t('editUserDialog.roleSelfAdmin') }),
    };

    // USER 를 새로 체크하면 용량을 다시 입력받는다(관리자 전용 계정은 0), 남은 값은 다시 판정, 빼면 그 칸의 오류 문구를 지운다
    const handleRolesChange = roles => {
        const addsUser = roles.includes('USER') && !formData.roles.includes('USER');
        const keepsQuota = quotaOrigin.bytes > 0;
        setFormData(prev => ({
            ...prev,
            roles,
            ...(addsUser && !keepsQuota && { totalQuotaByte: '' }),
        }));
        if (!roles.includes('USER')) {
            setErrorMessageQuota('');
            return;
        }
        if (addsUser && keepsQuota && formData.totalQuotaByte !== '') {
            try {
                checkQuota(formData.totalQuotaByte, formData.totalQuotaUnit);
                setErrorMessageQuota('');
            } catch (error) {
                setErrorMessageQuota(error.message);
            }
        }
    };

    useEffect(() => {
        const isCommonValid =
            errorMessageUsername === '' &&
            errorMessageQuota === '' &&
            errorMessagePhone === '' &&
            formData.userName !== '' &&
            formData.roles.length > 0;

        const isQuotaValid = hasUserRole ? formData.totalQuotaByte !== '' : true;

        if (isCommonValid && isQuotaValid) {
            setIsDisabled(false);
        } else {
            setIsDisabled(true);
        }
    }, [formData, hasUserRole, errorMessageQuota]);

    // 할당 용량 검증 — 사용량·할당 가능 용량은 저장할 byte 와 비교
    // - 조회한 값·단위 그대로면 원래 byte 를 보내므로 생략(기존 0 은 검증)
    const checkQuota = (value, unit, available = availableQuotaByte) => {
        emptyCheck(value, t);
        if (!isQuotaInputUnchanged(value, unit, quotaOrigin) || !(quotaOrigin.bytes > 0)) {
            validateUserQuota(value, unit, user?.useQuotaByte || 0, t, { basis: quotaOrigin });
            validateUserAvailableQuota(
                resolveQuotaBytes(value, unit, quotaOrigin),
                available,
                currentQuotaByte,
                t
            );
        }
    };

    // 할당 가능 용량을 늦게 받으면 이미 입력한 값을 다시 판정
    useEffect(() => {
        if (availableQuotaByte == null || !user || !hasUserRole) return;
        if (formData.totalQuotaByte === '') return;
        try {
            checkQuota(formData.totalQuotaByte, formData.totalQuotaUnit, availableQuotaByte);
            setErrorMessageQuota('');
        } catch (error) {
            setErrorMessageQuota(error.message);
        }
    }, [quotaCapacity]);

    // 단위 변경 — 빈칸(저장 비활성, 오류 문구는 띄우지 않음), 조회한 단위로 돌아오면 조회 값 복원
    const handleQuotaUnitChange = unit => {
        const value = quotaValueForUnit(unit, quotaOrigin);
        setFormData(prev => ({ ...prev, totalQuotaByte: value, totalQuotaUnit: unit }));
        try {
            if (value !== '') checkQuota(value, unit);
            setErrorMessageQuota('');
        } catch (error) {
            setErrorMessageQuota(error.message);
        }
    };

    const handleChange = async e => {
        const { name, value, files } = e.target;

        if (name === 'profileImage') {
            const file = files[0];
            e.target.value = ''; // 같은 파일 재선택도 변경으로 잡기 위해 초기화
            if (file) {
                // 서버 기준에 안 맞는 파일은 받지 않고 이미지를 비운 뒤 문구 표시 — 삭제 버튼과 같이 저장 시 기존 이미지도 삭제
                try {
                    validateProfileImage(file, t);
                } catch (error) {
                    setErrorMessageProfileImage(error.message);
                    setPreviewUrl(null);
                    setFormData(prev => ({
                        ...prev,
                        profileImageFile: null,
                        deleteProfileImage: true,
                    }));
                    return;
                }
                setErrorMessageProfileImage('');
                setPreviewUrl(URL.createObjectURL(file));
                setFormData(prev => ({
                    ...prev,
                    profileImageFile: file,
                    deleteProfileImage: false,
                }));
            }
            return; // 여기서 끝내줌
        }

        // 그 외 필드 처리
        let checkValue =
            name === 'countryCode' || name === 'userName' ? value : trimEdgeSpaces(value);

        setFormData(prev => ({
            ...prev,
            [name]: checkValue,
        }));

        try {
            if (name === 'userName') {
                emptyCheck(checkValue, t);
                validateUserName(checkValue, t);
                validateNoEdgeSpaces(checkValue, t);
                setErrorMessageUsername('');
            }
            if (name === 'totalQuotaByte') {
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

    const handleUpdateUser = async () => {
        const res = await api.PatchForm(`admins/users/${item?.userSeq}`, {
            un: formData.userName,
            // USER 역할이 없으면 할당 용량 없음 — tq 를 보내지 않으면 서버가 0 으로 저장
            tq: hasUserRole
                ? resolveQuotaBytes(formData.totalQuotaByte, formData.totalQuotaUnit, quotaOrigin)
                : null,
            roles: formData.roles.join(','),
            ct: formData.countryCode ? getDialCodeDigits(formData.countryCode.value) : null,
            pn: formData.phoneNumber,
            pif: formData.profileImageFile || null,
            dpi: formData.deleteProfileImage,
        });
        // console.log('formData', formData);
        if (res?.status === 200) {
            showSnackbar(
                !isAdmins ? t('adminUsers.update.success') : t('adminAdmins.update.success'),
                'success'
            );

            // 목록이 행만 갱신할 수 있으면 그쪽에 맡긴다 — 페이지 새로고침은 스크롤이 초기화된다
            if (props.onUpdated) {
                await props.onUpdated();
                onClose();
            } else {
                router.reload();
            }
        } else {
            const quotaError = hasUserRole ? quotaErrorMessage(res?.errorCode, t) : null;
            if (quotaError) setErrorMessageQuota(quotaError.inline);
            showSnackbar(
                quotaError?.toast ??
                    (ROLE_ERROR_MESSAGE_KEYS[res?.errorCode]
                        ? t(ROLE_ERROR_MESSAGE_KEYS[res?.errorCode])
                        : null) ??
                    (!isAdmins ? t('adminUsers.update.fail') : t('adminAdmins.update.fail')),
                'error'
            );
        }
    };

    const handleOpenChangePassword = async () => {
        openDialog('changePassword', {
            item: item,
        });
    };

    // 관리자 > 특정 사용자 2FA 초기화
    const reset2FA = async () => {
        try {
            const res = await api.Patch(`admins/users/${item?.userSeq}/mfa/reset`);

            if (res?.data?.code === 200) {
                showSnackbar(t('adminResetUser2fa.success'), 'success');
                await getUser();
            }
        } catch (err) {
            console.log(err);
            showSnackbar(t('adminResetUser2fa.fail'), 'error');
        }
    };

    const handleOpenReset2FA = () => {
        openDialog('alert', {
            title: t('adminReset2faConfirmDialog.title'),
            description: t('adminReset2faConfirmDialog.text'),
            buttonName: t('common.button.reset'),
            onClick: async () => {
                await reset2FA();
            },
        });
    };

    useEffect(() => {
        const fetchData = async () => {
            await getUser();
        };

        if (item) {
            fetchData();
            fetchQuotaCapacity();
        }
    }, []);

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
                <Typography variant="body1">
                    {!isAdmins ? t('editUserDialog.title.user') : t('editUserDialog.title.admin')}
                </Typography>
            </DialogTitle>
            <DialogContent sx={{ p: 0, mt: 4 }}>
                {/* --- 사용자 요약 정보 --- */}
                {user && !isAdmins && (
                    <Box
                        sx={{
                            border: '1px solid #ddd',
                            borderRadius: 2,
                            p: 2,
                            mb: 3,
                            display: 'flex',
                        }}
                    >
                        {/* 왼쪽 */}
                        <Box
                            sx={{
                                flex: 1,
                                display: 'flex',
                                flexDirection: 'column',
                                gap: 1,
                                pr: 3, // 오른쪽 padding 살짝
                            }}
                        >
                            <Typography variant="body9" color={'text_80'}>
                                {t(`common.label.userName`)}: {user.userName}
                            </Typography>
                            <Typography variant="body9" color={'text_80'}>
                                {t(`common.label.userId`)}: {user.userId}
                            </Typography>
                            <Typography variant="body9" color={'text_80'}>
                                {t(`common.label.lastLogin`)}:{' '}
                                {item.lastLoginDate
                                    ? dayjs(item.lastLoginDate).format('YYYY. M. D. H:mm')
                                    : ''}
                            </Typography>
                        </Box>

                        {/* 오른쪽 */}
                        <Box
                            sx={{
                                flex: 1,
                                display: 'flex',
                                flexDirection: 'column',
                                gap: 1,
                                pl: 2, // 왼쪽 padding 살짝
                            }}
                        >
                            <Typography variant="body9" color={'text_80'}>
                                {t(`editUserDialog.totalQuota`)}:{' '}
                                {convertBytesToReadableSize(user.totalQuotaByte)}
                            </Typography>
                            <Typography variant="body9" color={'text_80'}>
                                {t(`editUserDialog.useQuota`)}:{' '}
                                {convertBytesToReadableSize(user.useQuotaByte)}
                            </Typography>
                        </Box>
                    </Box>
                )}

                {/** 수정 가능한 정보 */}
                <Box>
                    {/* encPwd */}
                    <Box
                        sx={{
                            display: 'flex',
                            flexDirection: 'column',
                            gap: 2,
                            pb: 5,
                            maxWidth: '110px',
                        }}
                    >
                        {/** 비밀번호 변경 */}
                        <StyledButton
                            color="btn_border"
                            variant="outlined"
                            onClick={handleOpenChangePassword}
                            // 보호 계정은 관리자가 비밀번호를 바꿀 수 없다(서버 USER_034) — 본인 변경은 SELF_MANAGED 만 허용
                            disabled={!!item?.isProtected}
                            sx={{ minWidth: { xs: '100%', tablet: 'auto' } }}
                        >
                            <Typography variant="body5" color="text_100">
                                {t(`common.title.changePassword`)}
                            </Typography>
                        </StyledButton>
                        {/** 2FA 초기화 */}
                        <StyledButton
                            color="btn_border"
                            variant="outlined"
                            onClick={handleOpenReset2FA}
                            disabled={!user?.isMfaEnabled}
                            sx={{ minWidth: { xs: '100%', tablet: 'auto' } }}
                        >
                            <Typography variant="body5" color="text_100">
                                {t(`editUserDialog.resetTwoFactor`)}
                            </Typography>
                        </StyledButton>
                    </Box>
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
                                        deleteProfileImage: true,
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
                        <FormLabel required>{t(`editUserDialog.roleType`)}</FormLabel>
                        <RoleCheckboxGroup
                            roles={formData.roles}
                            onChange={handleRolesChange}
                            lockedRoles={lockedRoles}
                            // 보호 계정은 역할을 바꿀 수 없다(유형 무관, 서버 USER_034) — 이름·용량 등 다른 수정은 허용
                            lockedMessage={
                                item?.isProtected ? t('admin.protectedUser.tooltip') : undefined
                            }
                        />
                    </FormControl>
                    {/* totalQuotaByte */}
                    {hasUserRole && (
                        <FormControl fullWidth margin="normal">
                            <FormLabel required>{t(`editUserDialog.totalQuotaByte`)}</FormLabel>
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
                            {/* 할당 가능 용량 — 사용자 조회 전·조회 실패·총 용량 미설정이면 숨김 */}
                            {user && availableQuotaByte != null && (
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
                    sx={{ minWidth: { xs: '100%', tablet: 80 } }}
                    size="small"
                    onClick={handleUpdateUser}
                    disabled={isDisabled}
                >
                    <Typography variant={'body5'} color={'white'}>
                        {t('common.button.update')}
                    </Typography>
                </StyledButton>
            </DialogActions>
        </Dialog>
    );
}

import { MenuItem, Stack, Typography } from '@mui/material';
import Box from '@mui/material/Box';
import Image from 'next/image';
import palette from '@/theme/palette';
import StyledTextField from '@/components/common/input/StyledTextField';
import StyledSelect from '@/components/common/input/StyledSelect';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslations } from 'next-intl';
import useAuth from '@/hooks/useAuth';
import { useStorageInfo } from '@/hooks/useStorageInfo';
import StyledButton from '@/components/common/button/StyledButton';
import MaskedValue from '@/components/common/MaskedValue';
import useClientConfig from '@/hooks/useClientConfig';
import { MASKED_PLACEHOLDER } from '@/utils/masking';
import api from '@/utils/api';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import {
    bytesToQuotaInput,
    formatQuotaLabel,
    quotaValueForUnit,
    resolveQuotaBytes,
} from '@/utils/quota';

const UPLOAD_SIZE_UNITS = ['GB', 'MB'];

/** 테넌트 총 용량 표시용 — 1GB(1024MB) 이상이면 GB(소수 셋째 자리), 미만이면 MB */
const formatSizeLabel = formatQuotaLabel;

const UserInfoStack = ({ title, text, ele, sx, id }) => {
    return (
        <Stack
            direction={{
                md: 'row',
                xs: 'column',
            }}
            sx={{
                borderBottom: {
                    md: id === 'language' ? '' : `1px solid ${palette.control_border_normal}`,
                    xs: 'none',
                },
            }}
        >
            <Typography
                sx={{
                    backgroundColor: {
                        md: palette.bg_gray,
                    },
                    p: {
                        md: 3,
                        xs: 0,
                    },
                    minWidth: 200,
                    borderRight: {
                        ...sx,
                        md: `1px solid ${palette.control_border_normal}`,
                    },
                }}
                variant={'body7'}
                color={'text_60'}
            >
                {title}
            </Typography>
            {text ? (
                // SYSTEM_MANAGED 에서 서버가 숨긴 접속 정보(__MASKED__)는 블러로 표시
                <MaskedValue
                    value={text}
                    dummy={'xxxxxxxxxxxxxxxxxxxx'}
                    sx={{
                        py: {
                            md: 3,
                            xs: 0,
                        },
                        pl: {
                            md: 6,
                            xs: 0,
                        },
                        width: '100%',
                    }}
                    color={'text_100'}
                    variant={'body5'}
                />
            ) : (
                ele
            )}
        </Stack>
    );
};

export default function Storage({ loading, setLoading }) {
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    const [user] = useAuth();
    const [uploadSizeLimitChange, setUploadSizeLimitChange] = useState(false);
    const [uploadSizeLimit, setUploadSizeLimit] = useState('');
    const [uploadSizeUnit, setUploadSizeUnit] = useState('GB');
    // 조회(저장)한 업로드 용량 제한 — 단위를 바꿨다 돌아오면 복원, 그대로 저장하면 이 byte 를 보낸다
    const [uploadSizeOrigin, setUploadSizeOrigin] = useState({
        bytes: null,
        value: '',
        unit: 'GB',
    });
    const uploadSizeLimitInputRef = useRef(null);
    const [storageQuota, setStorageQuota] = useState(null);

    // 테넌트 총 용량 조회 (화면엔 총 용량만 표시)
    const getStorageQuota = async () => {
        const res = await api.Get('admins/system-config/storage-quota', {});

        if (res?.data?.code === 200) {
            setStorageQuota(res?.data?.data);
        }
    };

    // 총 용량 키가 없으면(null) "설정되지 않음"
    const formatQuota = bytes => {
        if (bytes === null || bytes === undefined) return t('adminStorage.quotaNotSet');
        return formatSizeLabel(bytes);
    };

    // 현재 업로드 용량 제한 설정 조회
    const getUploadSizeLimit = async () => {
        const res = await api.Get('admins/system-config/file-size-limit', {});

        if (res?.data?.code === 200) {
            // 제한 없음(null)이면 빈칸
            const bytes = res?.data?.data?.maxFileUploadSize ?? null;
            const { value, unit } = bytesToQuotaInput(bytes);
            setUploadSizeOrigin({ bytes, value, unit });
            setUploadSizeUnit(unit);
            setUploadSizeLimit(value);
        }
    };

    // 단위 변경 — 환산하지 않고 빈칸(빈칸 저장은 제한 없음), 조회한 단위로 돌아오면 조회 값 복원
    const handleUploadSizeUnitChange = unit => {
        setUploadSizeUnit(unit);
        setUploadSizeLimit(quotaValueForUnit(unit, uploadSizeOrigin));
    };

    // 업로드 용량 제한 설정 저장
    const handleUploadSizeLimitSave = useCallback(async () => {
        const bytes = uploadSizeLimit
            ? resolveQuotaBytes(uploadSizeLimit, uploadSizeUnit, uploadSizeOrigin)
            : null;
        const res = await api.Put('admins/system-config/file-size-limit', {
            maxFileUploadSize: bytes,
        });

        if (res?.data?.code === 200) {
            showSnackbar(t('adminSetting.save.success'), 'success');
            setUploadSizeOrigin({ bytes, value: uploadSizeLimit, unit: uploadSizeUnit });
            setUploadSizeLimitChange(false);
        } else {
            showSnackbar(t('adminSetting.save.fail'), 'error');
        }
    });

    useEffect(() => {
        if (!user) return;
    }, [user]);

    const { storageInfo, isCeph } = useStorageInfo();
    // SYSTEM_MANAGED 는 유형까지 블러 — 유형별로 다른 행(리전·엔드포인트)이 유형을 드러내지 않게 두 행 모두 블러로 보여준다
    const { isSystemManaged } = useClientConfig();
    const lockedValue = value => (isSystemManaged ? MASKED_PLACEHOLDER : value);

    useEffect(() => {
        if (storageInfo) {
            setLoading(!loading);
        }
    }, [storageInfo]);

    useEffect(() => {
        getUploadSizeLimit();
        getStorageQuota();
    }, []);

    return (
        <Box
            id={'general-container'}
            sx={{
                maxWidth: 840,
                width: '100%',
                display: 'flex',
                flexDirection: 'column',
                gap: 4,
                pt: {
                    md: 'calc(32px + 30px + 32px + 16px)',
                    xs: '87px',
                },
                //  상단 패딩 + tab메뉴 높이 + 기본 패딩 + 컨텐츠 패딩
            }}
        >
            <Stack
                direction={{
                    sm: 'row',
                    xs: 'column',
                }}
                alignItems={'center'}
                gap={1}
            >
                <Stack
                    direction={'row'}
                    alignItems={'center'}
                    gap={6}
                    pb={{
                        xs: 6,
                        md: 4,
                    }}
                    pl={{
                        md: 2,
                        xs: 0,
                    }}
                    sx={{ width: '100%' }}
                >
                    <Typography variant="body5" color={'text_80'} sx={{ whiteSpace: 'pre-line' }}>
                        {t(`adminStorage.description`)}
                    </Typography>
                </Stack>
            </Stack>
            {/** 스토리지 정보 */}
            {storageInfo && (
                <Stack
                    direction={{
                        xs: 'column',
                    }}
                    sx={{
                        borderRadius: 2,
                        border: {
                            md: `1px solid ${palette.control_border_normal}`,
                            xs: 'none',
                        },
                        gap: {
                            xs: 5,
                            md: 0,
                        },
                    }}
                >
                    <UserInfoStack
                        title={t('adminStorage.storageType')}
                        text={lockedValue(storageInfo.storageType)}
                        sx={{ borderTopLeftRadius: '8px' }}
                    />
                    <UserInfoStack
                        title={t('adminStorage.accessKey')}
                        text={storageInfo.accessKey}
                        sx={{ borderTopLeftRadius: '8px' }}
                    />
                    <UserInfoStack
                        title={t('adminStorage.secretKey')}
                        text={storageInfo.secretKey}
                        sx={{ borderTopLeftRadius: '8px' }}
                    />
                    <UserInfoStack
                        title={t('adminStorage.bucketName')}
                        text={storageInfo.bucketName}
                        sx={{ borderTopLeftRadius: '8px' }}
                    />
                    {(!isCeph || isSystemManaged) && (
                        <UserInfoStack
                            title={t('adminStorage.region')}
                            text={lockedValue(storageInfo.region)}
                            sx={{ borderTopLeftRadius: '8px' }}
                        />
                    )}
                    {(isCeph || isSystemManaged) && (
                        <UserInfoStack
                            title={t('adminStorage.endPoint')}
                            text={lockedValue(storageInfo.endPoint)}
                            sx={{ borderTopLeftRadius: '8px' }}
                        />
                    )}
                    <UserInfoStack
                        title={t('common.label.status')}
                        text={storageInfo.status}
                        sx={{ borderTopLeftRadius: '8px' }}
                    />
                    {storageQuota && (
                        <UserInfoStack
                            title={t('adminStorage.totalQuota')}
                            text={formatQuota(storageQuota.totalQuotaByte)}
                            sx={{ borderTopLeftRadius: '8px' }}
                        />
                    )}
                    <UserInfoStack
                        title={t('adminStorage.uploadSizeLimit')}
                        ele={
                            <Stack
                                direction={'row'}
                                justifyContent={{
                                    xs: 'flex-start',
                                    md: 'justify-between',
                                }}
                                alignItems={'center'}
                                sx={{
                                    width: '100%',
                                    height: { xs: 32, md: 40 },
                                    pr: 4,
                                    pl: {
                                        md: 6,
                                        xs: 0,
                                    },
                                }}
                            >
                                {uploadSizeLimitChange ? (
                                    <Stack
                                        direction={'row'}
                                        alignItems={'center'}
                                        gap={1}
                                        sx={{ width: '100%' }}
                                    >
                                        <StyledTextField
                                            ref={uploadSizeLimitInputRef}
                                            type="number"
                                            variant="outlined"
                                            value={uploadSizeLimit}
                                            onChange={e => {
                                                const val = e.target.value;
                                                if (val === '' || /^[0-9]+$/.test(val)) {
                                                    setUploadSizeLimit(val);
                                                }
                                            }}
                                            height={40}
                                            sx={{
                                                width: '100%',
                                                '& .MuiInputBase-input': {
                                                    color: palette.text_80,
                                                },
                                                '& .MuiOutlinedInput-notchedOutline': {
                                                    border: 'none',
                                                },
                                                '& .MuiInputBase-root': {
                                                    backgroundColor: '#f5f5f5',
                                                },
                                            }}
                                        />
                                        <StyledSelect
                                            select
                                            value={uploadSizeUnit}
                                            onChange={e =>
                                                handleUploadSizeUnitChange(e.target.value)
                                            }
                                            height={40}
                                            width={88}
                                        >
                                            {UPLOAD_SIZE_UNITS.map(unit => (
                                                <MenuItem key={unit} value={unit}>
                                                    <Typography variant="body5">{unit}</Typography>
                                                </MenuItem>
                                            ))}
                                        </StyledSelect>
                                    </Stack>
                                ) : (
                                    <Typography
                                        sx={{ width: { xs: 'max-content', md: '100%' } }}
                                        variant={'body5'}
                                        color={'text_60'}
                                    >
                                        {/* 입력한 단위 그대로 표시 */}
                                        {uploadSizeLimit && `${uploadSizeLimit} ${uploadSizeUnit}`}
                                    </Typography>
                                )}
                                <StyledButton
                                    variant={'text'}
                                    sx={{ gap: 1, px: 2, py: 0, height: 24 }}
                                    onClick={() => {
                                        setUploadSizeLimitChange(!uploadSizeLimitChange);
                                        setTimeout(
                                            () => uploadSizeLimitInputRef.current?.focus(),
                                            100
                                        );
                                    }}
                                >
                                    <Image
                                        src={'/setting/icon-ic-edit.svg'}
                                        alt={'edit'}
                                        width={16}
                                        height={16}
                                    />
                                    <Typography
                                        variant={'body9'}
                                        sx={{ display: { xs: 'none', md: 'block' } }}
                                        color={'text_tab_selected'}
                                    >
                                        {t('common.button.update')}
                                    </Typography>
                                </StyledButton>
                            </Stack>
                        }
                        sx={{ borderTopLeftRadius: '8px' }}
                    />
                </Stack>
            )}
            {/** 저장 버튼 */}

            <Stack justifyContent={'flex-end'} alignItems={{ xs: 'flex-start', md: 'flex-end' }}>
                <StyledButton
                    color={'btn_point_bg'}
                    variant="contained"
                    onClick={handleUploadSizeLimitSave}
                    sx={{ px: { xs: '29.5px', md: 2 }, height: { xs: 32, md: 24 } }}
                >
                    <Typography variant={'body9'} color={'white'}>
                        {t('common.button.save')}
                    </Typography>
                </StyledButton>
            </Stack>
        </Box>
    );
}

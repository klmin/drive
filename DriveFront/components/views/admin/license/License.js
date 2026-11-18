import { Stack, Typography } from '@mui/material';
import Box from '@mui/material/Box';
import palette from '@/theme/palette';
import StyledButton from '@/components/common/button/StyledButton';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import api from '@/utils/api';
import { useTranslations } from 'next-intl';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { useRouter } from '@/hooks/useRouter';
import useAuth from '@/hooks/useAuth';
import { useDialog } from '@/provider/DialogProvider';
import dayjs from 'dayjs';
import { getDaysOver, getLicenseBlockStatus, isAuthGracePeriod } from '@/utils/license';
import { useLicense } from '@/provider/LicenseProvider';
import useClientConfig from '@/hooks/useClientConfig';

const UserInfoStack = ({ title, text, ele, sx, id }) => {
    return (
        <Stack
            direction={{
                md: 'row',
                xs: 'column',
            }}
            sx={{
                // 아래 테두리 1px 을 빼고 내용 높이 40px
                height: { md: 40 },
                boxSizing: 'content-box',
                borderBottom: {
                    md: id === 'language' ? '' : `1px solid ${palette.control_border_normal}`,
                    xs: 'none',
                },
                // 마지막 행은 바깥 테두리와 겹치지 않게
                '&:last-of-type': { borderBottom: 'none' },
            }}
        >
            <Typography
                sx={{
                    display: { md: 'flex' },
                    alignItems: 'center',
                    backgroundColor: {
                        md: palette.bg_gray,
                    },
                    px: {
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
                <Typography
                    sx={{
                        display: { md: 'flex' },
                        alignItems: 'center',
                        pl: {
                            md: 6,
                            xs: 0,
                        },
                        width: '100%',
                    }}
                    color={'text_100'}
                    variant={'body5'}
                >
                    {text}
                </Typography>
            ) : (
                ele
            )}
        </Stack>
    );
};

const LICENSE_STATUS_LABEL = {
    VALID: 'adminLicense.status.valid',
    EXPIRED: 'adminLicense.status.expired',
    SEAT_EXCEEDED: 'adminLicense.status.exceeded',
    AUTH_FAILED: 'admin.licenseAuthFailDialog.title',
};

// 차단·인증 실패 상태별 안내 다이얼로그 i18n 키 (관리자 로그인과 동일)
const LICENSE_DIALOG = {
    SEAT_EXCEEDED: {
        titleKey: 'licenseDialog.seatExceeded.title',
        descKey: 'licenseDialog.seatExceeded.message',
    },
    EXPIRED: {
        titleKey: 'admin.licenseExpiredDialog.title',
        descKey: 'admin.licenseExpiredDialog.desc',
    },
    AUTH_FAILED: {
        titleKey: 'admin.licenseAuthFailDialog.title',
        descKey: 'admin.licenseAuthFailDialog.desc',
    },
};

// 만료일 기준 D-day 문자열: 양수(초과)면 D+N, 음수(잔여)면 D-N
function formatDDay(expiresOn) {
    const diff = getDaysOver(expiresOn);
    return diff >= 0 ? `D+${diff}` : `D${diff}`;
}

function formatDate(date) {
    return dayjs(date).format('YYYY. M. D');
}

function formatDateTime(date) {
    return date ? dayjs(date).format('YYYY. M. D  HH:mm:ss') : '-';
}

export default function License({ loading, setLoading }) {
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    const [user] = useAuth();
    const router = useRouter();

    const [licenseInfo, setLicenseInfo] = useState(null);
    const [licenseFile, setLicenseFile] = useState(null);
    const [state, setState] = useState(null);
    const [verifying, setVerifying] = useState(false);
    const { refreshLicense } = useLicense();
    // SYSTEM_MANAGED 는 라이선스 등록·수동 인증이 막혀 있어(TENANT_011) 파일 선택·업로드·인증 버튼을 비활성으로 둔다
    const { isSystemManaged } = useClientConfig();

    const isSeat = licenseInfo?.licenseType === 'SEAT';

    const { openDialog } = useDialog();
    const fileInputRef = useRef(null);

    const handleFileChange = e => {
        if (e.target.files && e.target.files[0]) {
            setLicenseFile(e.target.files[0]);
        }
    };

    const handleLicenseUpload = useCallback(async () => {
        openDialog('alert', {
            title: t('adminLicenseUploadConfirmDialog.title'),
            description: t('adminLicenseUploadConfirmDialog.text'),
            onClick: async () => {
                const res = await api.PostForm('admins/license', {
                    licenseFile: licenseFile || '',
                });

                if (res?.status === 200) {
                    showSnackbar(t('adminLicense.upload.success'), 'success');
                    router.reload();
                } else {
                    showSnackbar(t('adminLicense.upload.fail'), 'error');
                }
            },
        });
    }, [licenseFile]);

    // showDialog: 수동 인증 후 재조회처럼 결과를 스낵바로 알린 경우엔 다이얼로그를 띄우지 않는다
    const getLicenseInfo = async ({ showDialog = true } = {}) => {
        const res = await api.Get('admins/license', {});
        if (res?.status !== 200) {
            // 미등록·예전 형식이 아닌 조회 실패는 미등록 화면과 구분되게 알린다
            if (res?.code !== 6998 && res?.code !== 6997) {
                showSnackbar(t('common.toast.fail'), 'error');
            }
            return;
        }

        const data = res.data;

        setLicenseInfo({
            publisher: data?.publisher || '',
            category: data?.CATEGORY || '',
            licenseType: data?.LICENSE_TYPE,
            licenseId: data?.LICENSE_ID || '',
            clientName: data?.CLIENT_NAME || '',
            startDate: data?.START_DATE || '',
            endDate: data?.END_DATE || '',
            expiresOn: data?.expiresOn || '',
            currentSeatNum: data?.currentSeatNum || '',
            constrainValue: data?.constrainValue || '',
            availableSeatNum: data?.availableSeatNum || '',
            paidWhiteLabel: data?.paidWhiteLabel || false,
            onlineAuth: data?.onlineAuth || false,
            lastAuthAt: data?.lastAuthAt || '',
        });

        let blockStatus = null;
        if (!data?.state || data?.availableSeatNum < 0) {
            blockStatus = getLicenseBlockStatus(await api.Get('config/license-status', {}));

            // 상태 재조회 실패 — 실패를 알리고, 1차 조회에서 확인한 무효 상태를 유지
            if (blockStatus === 'NO_RESPONSE') {
                showSnackbar(t('common.toast.fail'), 'error');
                showDialog = false;
            }
            if (blockStatus === 'NO_RESPONSE' || blockStatus === 'NOT_ISSUED') {
                blockStatus = data?.availableSeatNum < 0 ? 'SEAT_EXCEEDED' : 'EXPIRED';
            }
        }
        // 인증 실패 30일 유예 중이면 사용은 가능해도 인증 실패로 표시
        if (!blockStatus && isAuthGracePeriod(data)) blockStatus = 'AUTH_FAILED';

        setState(LICENSE_STATUS_LABEL[blockStatus] ? blockStatus : 'VALID');
        setLoading(!loading);

        const dialogCfg = LICENSE_DIALOG[blockStatus];
        if (showDialog && dialogCfg) {
            openDialog('alert', {
                title: t(dialogCfg.titleKey),
                description: t(dialogCfg.descKey),
                cancelButtonName: '',
            });
        }
    };

    // 수동 인증 — 실패해도 HTTP 200 이라 authStatus 로 판정
    const handleLicenseVerify = async () => {
        if (verifying) return;
        setVerifying(true);
        try {
            const res = await api.Post('admins/license/auth', null);
            if (res?.data?.authStatus === 'SUCCESS') {
                showSnackbar(t('admin.license.toast.verifySuccess'), 'success');
            } else {
                showSnackbar(t('admin.license.toast.verifyFail'), 'error');
            }
            await getLicenseInfo({ showDialog: false });
            refreshLicense();
        } finally {
            setVerifying(false);
        }
    };

    useEffect(() => {
        getLicenseInfo();
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
            {/** 라이선스 파일 등록 */}
            <Stack
                direction={{ xs: 'column' }}
                sx={{
                    borderRadius: 2,
                    // 제목 칸 회색 배경이 둥근 모서리 밖으로 나오지 않게
                    overflow: { md: 'hidden' },
                    border: {
                        md: `1px solid ${palette.control_border_normal}`,
                        xs: 'none',
                    },
                    gap: { xs: 5, md: 0 },
                }}
            >
                <UserInfoStack
                    title={t('adminLicense.licenseFileUpload')}
                    ele={
                        <Stack
                            direction={'row'}
                            justifyContent={{ xs: 'flex-start', md: 'justify-between' }}
                            alignItems={'center'}
                            sx={{
                                width: '100%',
                                height: { xs: 32, md: 40 },
                                pr: 4,
                                pl: { md: 6, xs: 0 },
                            }}
                        >
                            <input
                                type="file"
                                ref={fileInputRef}
                                style={{ display: 'none' }}
                                onChange={handleFileChange}
                            />
                            <StyledButton
                                variant="outlined"
                                disabled={isSystemManaged}
                                onClick={() => fileInputRef.current?.click()}
                            >
                                <Typography variant="body9" color={'text_60'}>
                                    {t('adminLicense.selectFile')}
                                </Typography>
                            </StyledButton>
                            <Typography
                                sx={{ width: { xs: 'max-content', md: '100%' } }}
                                variant={'body5'}
                                color={'text_60'}
                            >
                                {licenseFile ? licenseFile.name : ''}
                            </Typography>
                        </Stack>
                    }
                />
            </Stack>
            <Stack justifyContent={'flex-end'} alignItems={{ xs: 'flex-start', md: 'flex-end' }}>
                <StyledButton
                    color={'btn_point_bg'}
                    variant="contained"
                    disabled={isSystemManaged}
                    onClick={handleLicenseUpload}
                    sx={{ px: { xs: '29.5px', md: 2 }, height: { xs: 32, md: 24 } }}
                >
                    <Typography variant={'body9'} color={'white'}>
                        {t('common.button.upload')}
                    </Typography>
                </StyledButton>
            </Stack>

            {/** 라이선스 관리 */}
            {state && (
                <Stack
                    direction={{ xs: 'column' }}
                    sx={{
                        borderRadius: 2,
                        // 제목 칸 회색 배경이 둥근 모서리 밖으로 나오지 않게
                        overflow: { md: 'hidden' },
                        border: {
                            md: `1px solid ${palette.control_border_normal}`,
                            xs: 'none',
                        },
                        gap: { xs: 5, md: 0 },
                    }}
                >
                    <UserInfoStack
                        title={t('adminLicense.status.label')}
                        text={
                            t(LICENSE_STATUS_LABEL[state]) +
                            ` (${formatDDay(licenseInfo?.expiresOn)})`
                        }
                        sx={{ borderTopLeftRadius: '8px' }}
                    />
                    <UserInfoStack
                        title={t('adminLicense.type.label')}
                        ele={
                            <Stack
                                direction={'row'}
                                alignItems={'center'}
                                gap={'4px'}
                                sx={{
                                    pl: { md: 6, xs: 0 },
                                    width: '100%',
                                }}
                            >
                                <Typography variant={'body5'} color={'text_100'}>
                                    {licenseInfo?.category + ' / ' + licenseInfo?.licenseType}
                                </Typography>
                                {licenseInfo?.paidWhiteLabel && (
                                    <Box
                                        sx={{
                                            px: '8px',
                                            py: '4px',
                                            borderRadius: '25px',
                                            outline: `1px solid ${palette.btn_border}`,
                                            outlineOffset: '-1px',
                                            display: 'flex',
                                            alignItems: 'center',
                                            justifyContent: 'center',
                                        }}
                                    >
                                        <Typography variant={'body9'} color={'text_100'}>
                                            PWL
                                        </Typography>
                                    </Box>
                                )}
                            </Stack>
                        }
                        sx={{ borderTopLeftRadius: '8px' }}
                    />
                    <UserInfoStack
                        title={t('adminLicense.period.label')}
                        text={
                            formatDate(licenseInfo?.startDate) +
                            ' ~ ' +
                            formatDate(licenseInfo?.endDate) +
                            ` (${formatDDay(licenseInfo?.expiresOn)})`
                        }
                        sx={{ borderTopLeftRadius: '8px' }}
                    />
                    {isSeat && (
                        <UserInfoStack
                            title={t('adminLicense.sheets.label')}
                            text={licenseInfo?.currentSeatNum + ' / ' + licenseInfo?.constrainValue}
                            sx={{ borderTopLeftRadius: '8px' }}
                        />
                    )}
                    {licenseInfo?.onlineAuth && (
                        <UserInfoStack
                            title={t('admin.license.lastVerifiedAt')}
                            ele={
                                <Stack
                                    direction={'row'}
                                    justifyContent={'space-between'}
                                    alignItems={'center'}
                                    sx={{
                                        pl: { md: 6, xs: 0 },
                                        pr: 4,
                                        width: '100%',
                                    }}
                                >
                                    <Typography variant={'body5'} color={'text_100'}>
                                        {formatDateTime(licenseInfo?.lastAuthAt)}
                                    </Typography>
                                    <StyledButton
                                        variant="outlined"
                                        onClick={handleLicenseVerify}
                                        disabled={verifying || isSystemManaged}
                                        sx={{
                                            minWidth: 53,
                                            height: 24,
                                            p: '8px',
                                            borderRadius: '4px',
                                            borderColor: palette.btn_border,
                                            backgroundColor: palette.btn_white_bg_normal,
                                        }}
                                    >
                                        <Typography variant="body9" color={'text_100'}>
                                            {t('admin.license.verifyBtn')}
                                        </Typography>
                                    </StyledButton>
                                </Stack>
                            }
                            sx={{ borderTopLeftRadius: '8px' }}
                        />
                    )}
                </Stack>
            )}
        </Box>
    );
}

import React, { useEffect, useRef, useState } from 'react';
import {
    Box,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    IconButton,
    Stack,
    Typography,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import StyledButton from '@/components/common/button/StyledButton';
import palette from '@/theme/palette';
import api from '@/utils/api';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { useTranslations } from 'next-intl';
import { getLicenseStatus } from '@/utils/license';

/** 목록 상태 칸과 같은 문구 */
const STATUS_LABEL_KEY = {
    VALID: 'superAdmin.tenantManagement.badge.valid',
    EXPIRING_SOON: 'adminLicense.status.expiring',
    EXPIRED: 'superAdmin.tenantManagement.badge.expired',
    SEAT_EXCEEDED: 'superAdmin.tenantManagement.badge.seatExceeded',
};

/**
 * 슈퍼관리자 — 테넌트 라이선스 등록·갱신(POST /super-admin/tenants/{tenantSeq}/license, multipart licenseFile).
 * 관리자 라이선스 등록과 같은 처리라 SELF_MANAGED 도 진입 키 없이 등록할 수 있다.
 */
export default function TenantLicenseDialog({ open, onClose, ...props }) {
    const t = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    const { item, reload, registered } = props;
    const license = item?.license;
    const licenseStatus = getLicenseStatus(license);
    const fileInputRef = useRef(null);
    const [licenseFile, setLicenseFile] = useState(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    useEffect(() => {
        if (open) setLicenseFile(null);
    }, [open]);

    const handleFileChange = e => {
        if (e.target.files && e.target.files[0]) setLicenseFile(e.target.files[0]);
        // 같은 파일을 다시 골라도 onChange 가 오도록 비운다
        e.target.value = '';
    };

    const handleUpload = async () => {
        if (!licenseFile || isSubmitting) return;
        setIsSubmitting(true);
        try {
            const res = await api.PostForm(`super-admin/tenants/${item.tenantSeq}/license`, {
                licenseFile,
            });
            if (res?.status === 200) {
                showSnackbar(t('adminLicense.upload.success'), 'success');
                await reload?.();
                onClose?.();
            } else {
                showSnackbar(t('adminLicense.upload.fail'), 'error');
            }
        } finally {
            setIsSubmitting(false);
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
            <DialogTitle
                sx={{
                    p: 0,
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                }}
            >
                <Typography variant="body1" color="text_100">
                    {t(
                        registered
                            ? 'superAdmin.tenantLicenseDialog.titleRenew'
                            : 'superAdmin.tenantLicenseDialog.title'
                    )}
                </Typography>
                <IconButton onClick={onClose} size="small" sx={{ p: 0.5 }}>
                    <CloseIcon fontSize="small" />
                </IconButton>
            </DialogTitle>

            <DialogContent sx={{ p: 0, mt: 4 }}>
                <Stack sx={{ gap: '16px' }}>
                    <Typography variant="body5" color="text_80">
                        {t(
                            registered
                                ? 'superAdmin.tenantLicenseDialog.descRenew'
                                : 'superAdmin.tenantLicenseDialog.desc',
                            { tenantName: item?.tenantName }
                        )}
                    </Typography>

                    {/* 갱신 — 바뀌기 전 라이선스(목록 응답 값)를 보여 준다 */}
                    {registered && license && (
                        <Box
                            sx={{
                                p: 3,
                                borderRadius: '6px',
                                bgcolor: palette.bg_gray,
                                border: `1px solid ${palette.divider_8}`,
                            }}
                        >
                            <Typography
                                variant="body6"
                                color="text_80"
                                sx={{ mb: 1, display: 'block' }}
                            >
                                {t('superAdmin.tenantLicenseDialog.current')}
                            </Typography>
                            <Stack sx={{ gap: '4px' }}>
                                {[
                                    [
                                        'status',
                                        STATUS_LABEL_KEY[licenseStatus]
                                            ? t(STATUS_LABEL_KEY[licenseStatus])
                                            : '-',
                                    ],
                                    ['expiresOn', license.expiresOn ?? '-'],
                                    [
                                        'seat',
                                        license.seat
                                            ? `${license.seat.current} / ${license.seat.max}`
                                            : '-',
                                    ],
                                ].map(([key, value]) => (
                                    <Typography key={key} variant="body7" color="text_60">
                                        {t(`superAdmin.tenantLicenseDialog.${key}`)} : {value}
                                    </Typography>
                                ))}
                            </Stack>
                        </Box>
                    )}
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                        <input
                            type="file"
                            ref={fileInputRef}
                            style={{ display: 'none' }}
                            onChange={handleFileChange}
                        />
                        <StyledButton
                            variant="outlined"
                            color="btn_border"
                            onClick={() => fileInputRef.current?.click()}
                        >
                            <Typography variant="body9" color="text_60">
                                {t('adminLicense.selectFile')}
                            </Typography>
                        </StyledButton>
                        <Typography
                            variant="body5"
                            color={licenseFile ? 'text_80' : 'text_40'}
                            noWrap
                            sx={{ minWidth: 0 }}
                        >
                            {licenseFile?.name ?? t('superAdmin.tenantLicenseDialog.noFile')}
                        </Typography>
                    </Box>
                    <Typography variant="body7" color="text_60" sx={{ whiteSpace: 'pre-line' }}>
                        {t('superAdmin.tenantLicenseDialog.note')}
                    </Typography>
                </Stack>
            </DialogContent>

            <DialogActions sx={{ p: 0, mt: 4 }}>
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
                    sx={{ minWidth: { xs: '100%', tablet: 80 }, bgcolor: palette.btn_point_bg }}
                    onClick={handleUpload}
                    disabled={!licenseFile || isSubmitting}
                >
                    <Typography variant="body5" color="white">
                        {t('common.button.upload')}
                    </Typography>
                </StyledButton>
            </DialogActions>
        </Dialog>
    );
}

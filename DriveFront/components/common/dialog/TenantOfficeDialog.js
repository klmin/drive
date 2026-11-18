import React, { useEffect, useState } from 'react';
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
import OfficeSettingsFields from '@/components/common/office/OfficeSettingsFields';
import palette from '@/theme/palette';
import api from '@/utils/api';
import { officeSettingsErrors, officeSettingsPayload } from '@/utils/officeSettings';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { useTranslations } from 'next-intl';

const initialValues = tenantSeq => ({
    officeConnectionType: 'onPremise',
    officeDomain: '',
    officeAuthMode: 'ticket',
    officeTicketServiceToken: '',
    // 어댑터 이름 관례 = 테넌트 번호(TFO 에도 같은 이름으로 등록)
    officeHttpStorageAdapterName: tenantSeq != null ? String(tenantSeq) : '',
    officeHttpStorageRequestJwtSecret: '',
});

/**
 * 슈퍼관리자 — 테넌트 Office 등록·수정. 테넌트당 하나(테넌트별 설정 키)라 등록·수정이 같은 저장이다.
 * 열 때 GET /super-admin/tenants/{tenantSeq}/office 로 현재 값을 채우고, 저장은 PUT(develop SuperAdminOfficeController).
 * 저장하면 서비스 관리 표시가 켜져 그 테넌트 관리자는 office 를 고칠 수 없다 — 끄는 API 가 없다.
 */
export default function TenantOfficeDialog({ open, onClose, ...props }) {
    const t = useTranslations();
    const to = useTranslations('officeIntegration');
    const { showSnackbar } = useSnackbarContext();
    const { item, reload, registered } = props;
    const [values, setValues] = useState(initialValues(item?.tenantSeq));
    const [serviceManaged, setServiceManaged] = useState(false);
    const [savedAdapter, setSavedAdapter] = useState('');
    const [loaded, setLoaded] = useState(false);
    const [isSubmitting, setIsSubmitting] = useState(false);

    useEffect(() => {
        if (!open) return;
        let active = true;
        setLoaded(false);
        setValues(initialValues(item?.tenantSeq));
        setSavedAdapter('');
        (async () => {
            const res = await api.Get(`super-admin/tenants/${item.tenantSeq}/office`);
            if (!active) return;
            const data = res?.status === 200 ? res?.data?.data : null;
            if (data) {
                // 연동 안 함이어도 보관된 주소·키가 있으면 그대로 채운다(none 은 연결 유형만 바꾼다)
                setValues({
                    ...initialValues(item.tenantSeq),
                    ...Object.fromEntries(
                        Object.entries(data).filter(([, v]) => v !== null && v !== undefined)
                    ),
                    // 등록은 연결하려고 여는 것이라 On-premise 로 시작
                    officeConnectionType: registered ? data.officeConnectionType : 'onPremise',
                });
                setServiceManaged(!!data.serviceManaged);
                // 수정일 때만 — 이미 TFO 에 등록된 이름을 바꾸면 경고
                setSavedAdapter(registered ? data.officeHttpStorageAdapterName || '' : '');
            } else {
                showSnackbar(to('loadFailed'), 'error');
            }
            setLoaded(true);
        })();
        return () => {
            active = false;
        };
    }, [open]);

    const change = (field, value) => setValues(prev => ({ ...prev, [field]: value }));

    const invalid = Object.keys(officeSettingsErrors(values)).length > 0;

    const handleSave = async () => {
        if (invalid || isSubmitting) return;
        setIsSubmitting(true);
        try {
            const res = await api.Put(
                `super-admin/tenants/${item.tenantSeq}/office`,
                officeSettingsPayload(values)
            );
            if (res?.status === 200) {
                showSnackbar(t('adminSetting.save.success'), 'success');
                // 목록의 Office 컬럼·메뉴 이름(등록/수정)을 새 상태로
                await reload?.();
                onClose?.();
            } else {
                showSnackbar(t('adminSetting.save.fail'), 'error');
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
                    maxWidth: '520px !important',
                    width: '100%',
                    maxHeight: '90vh',
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
                            ? 'superAdmin.tenantOfficeDialog.titleModify'
                            : 'superAdmin.tenantOfficeDialog.titleRegister'
                    )}
                </Typography>
                <IconButton onClick={onClose} size="small" sx={{ p: 0.5 }}>
                    <CloseIcon fontSize="small" />
                </IconButton>
            </DialogTitle>

            <DialogContent sx={{ p: 0, mt: 4 }}>
                <Stack sx={{ pt: '4px', gap: '16px' }}>
                    <Typography variant="body5" color="text_80">
                        {t('superAdmin.tenantOfficeDialog.desc', { tenantName: item?.tenantName })}
                    </Typography>

                    {/* 서비스 관리 표시는 되돌릴 수 없다 — SELF_MANAGED 면 고객 관리자가 이후 office 를 못 고친다 */}
                    <Box
                        sx={{
                            p: 3,
                            borderRadius: '6px',
                            bgcolor: palette.bg_gray,
                            border: `1px solid ${palette.divider_8}`,
                        }}
                    >
                        <Typography variant="body7" color="text_80" sx={{ whiteSpace: 'pre-line' }}>
                            {t(
                                // 이미 서비스 관리면 되돌릴 게 없으니 일반 안내만
                                item?.tenantType === 'SELF_MANAGED' && !serviceManaged
                                    ? 'superAdmin.tenantOfficeDialog.selfManagedWarning'
                                    : 'superAdmin.tenantOfficeDialog.managedNote'
                            )}
                        </Typography>
                    </Box>

                    <OfficeSettingsFields
                        variant="dialog"
                        idPrefix="tenant-office"
                        values={values}
                        onChange={change}
                        disabled={!loaded || isSubmitting}
                        savedAdapter={savedAdapter}
                    />
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
                    sx={{ minWidth: { xs: '100%', tablet: 80 } }}
                    onClick={handleSave}
                    disabled={!loaded || invalid || isSubmitting}
                >
                    <Typography variant="body5" color="white">
                        {t('common.button.save')}
                    </Typography>
                </StyledButton>
            </DialogActions>
        </Dialog>
    );
}

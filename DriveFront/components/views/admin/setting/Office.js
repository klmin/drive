import React, { useEffect, useState } from 'react';
import { Alert, Box, Stack, Typography } from '@mui/material';
import { useTranslations } from 'next-intl';
import api from '@/utils/api';
import {
    OFFICE_SETTINGS_PATH,
    officeSettingsErrors,
    officeSettingsPayload,
} from '@/utils/officeSettings';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import StyledButton from '@/components/common/button/StyledButton';
import useClientConfig from '@/hooks/useClientConfig';
import OfficeSettingsFields, {
    InfoNote,
    LabeledField,
} from '@/components/common/office/OfficeSettingsFields';

const empty = {
    officeConnectionType: 'none',
    officeDomain: '',
    officeAuthMode: 'ticket',
    officeTicketServiceToken: '',
    officeHttpStorageAdapterName: '',
    officeHttpStorageRequestJwtSecret: '',
};

/** 저장 대상 필드만 비교 */
const isSameSettings = (a, b) => Object.keys(empty).every(key => (a[key] ?? '') === (b[key] ?? ''));

/** 다른 관리자 설정 화면(Intellect 등)과 같은 폭·상단 여백 */
const pageSx = {
    maxWidth: 840,
    width: '100%',
    pt: { md: 'calc(32px + 30px + 32px + 16px)', xs: '87px' },
};

export default function Office({ setLoading }) {
    const t = useTranslations('officeIntegration');
    const tc = useTranslations();
    const { showSnackbar } = useSnackbarContext();
    // SYSTEM_MANAGED 는 관리자 office 저장이 막혀 있어(TENANT_011) 옵션 선택·입력·저장을 모두 비활성으로 둔다
    const { isSystemManaged } = useClientConfig();
    const [values, setValues] = useState(empty);
    const [savedValues, setSavedValues] = useState(empty);
    const [ready, setReady] = useState(false);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    const [revision, setRevision] = useState(0);
    const [savedAdapter, setSavedAdapter] = useState('');
    useEffect(() => {
        let active = true;
        (async () => {
            try {
                const response = await api.Get(OFFICE_SETTINGS_PATH);
                if (!active) return;
                if (response?.status !== 200 || !response.data?.data) throw new Error();
                const loaded = { ...empty, ...response.data.data };
                setValues(loaded);
                setSavedValues(loaded);
                setSavedAdapter(response.data.data.officeHttpStorageAdapterName || '');
                setReady(true);
            } catch {
                if (active) setError(t('loadFailed'));
            } finally {
                if (active) setLoading(false);
            }
        })();
        return () => {
            active = false;
        };
    }, [setLoading, t]);
    const change = (field, value) => setValues(v => ({ ...v, [field]: value }));
    const busy = !ready || saving;
    const changed = !isSameSettings(values, savedValues);
    const hasErrors = Object.keys(officeSettingsErrors(values)).length > 0;
    const save = async event => {
        event.preventDefault();
        if (busy || !changed || hasErrors || isSystemManaged) return;
        setSaving(true);
        try {
            const response = await api.Put(OFFICE_SETTINGS_PATH, officeSettingsPayload(values));
            if (![200, 201].includes(response?.status)) throw new Error();
            const saved =
                values.officeConnectionType === 'onPremise' && values.officeAuthMode === 'legacy'
                    ? { ...values, officeTicketServiceToken: '' }
                    : values;
            setValues(saved);
            setSavedValues(saved);
            setSavedAdapter(values.officeHttpStorageAdapterName);
            setRevision(v => v + 1);
            window.dispatchEvent(new Event('api-response'));
            showSnackbar(tc('adminSetting.save.success'), 'success');
        } catch {
            showSnackbar(tc('adminSetting.save.fail'), 'error');
        } finally {
            setSaving(false);
        }
    };
    // 응답 전에는 그리지 않는다 — 기본값(연동 안 함)이 먼저 보였다가 바뀌지 않도록
    if (!ready) {
        return error ? (
            <Box sx={pageSx}>
                <Alert severity="error">{error}</Alert>
            </Box>
        ) : null;
    }
    if (values.serviceManaged) {
        return (
            <Box sx={pageSx}>
                <Stack gap="20px">
                    <InfoNote>{t('serviceManaged')}</InfoNote>
                    <LabeledField
                        id="office-managed-adapter"
                        label={t('adapter')}
                        value={values.officeHttpStorageAdapterName}
                        helperText={t('managedAdapterHelp')}
                        InputProps={{ readOnly: true }}
                    />
                </Stack>
            </Box>
        );
    }
    return (
        <Box component="form" noValidate onSubmit={save} sx={pageSx}>
            <Stack gap="20px">
                <Typography variant="body5" color="text_80" sx={{ whiteSpace: 'pre-line' }}>
                    {t('description')}
                </Typography>
                <OfficeSettingsFields
                    variant="page"
                    values={values}
                    onChange={change}
                    disabled={busy || isSystemManaged}
                    savedAdapter={savedAdapter}
                    revision={revision}
                />
                <Box display="flex" justifyContent="flex-end">
                    <StyledButton
                        type="submit"
                        color={'btn_point_bg'}
                        variant="contained"
                        disabled={busy || !changed || hasErrors || isSystemManaged}
                        sx={{ px: { xs: '29.5px', md: 2 }, minWidth: 80 }}
                    >
                        <Typography variant={'body9'} color={'white'}>
                            {tc('common.button.save')}
                        </Typography>
                    </StyledButton>
                </Box>
            </Stack>
        </Box>
    );
}

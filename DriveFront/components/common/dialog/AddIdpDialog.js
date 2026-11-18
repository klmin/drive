import React, { useEffect, useState } from 'react';
import {
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormLabel,
    IconButton,
    InputAdornment,
    Typography,
} from '@mui/material';
import Box from '@mui/material/Box';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import VisibilityOffOutlinedIcon from '@mui/icons-material/VisibilityOffOutlined';
import StyledButton from '@/components/common/button/StyledButton';
import StyledTextField from '@/components/common/input/StyledTextField';
import palette from '@/theme/palette';
import { useTranslations } from 'next-intl';
import { emptyCheck } from '@/utils/userUtils';

const INITIAL_FORM = {
    displayName: '',
    issuerUrl: '',
    clientId: '',
    clientSecret: '',
};

const formLabelSx = {
    fontSize: '14px',
    fontWeight: 400,
    lineHeight: '20px',
    color: palette.text_80,
    '& .MuiFormLabel-asterisk': {
        color: '#F86C5E',
        fontWeight: 600,
    },
};

export default function AddIdpDialog({ open, onClose, onSuccess }) {
    const t = useTranslations();

    const [formData, setFormData] = useState(INITIAL_FORM);
    const [showSecret, setShowSecret] = useState(false);
    const [isDisabled, setIsDisabled] = useState(true);

    const [errorDisplayName, setErrorDisplayName] = useState('');
    const [errorIssuerUrl, setErrorIssuerUrl] = useState('');
    const [errorClientId, setErrorClientId] = useState('');

    useEffect(() => {
        if (open) {
            setFormData(INITIAL_FORM);
            setErrorDisplayName('');
            setErrorIssuerUrl('');
            setErrorClientId('');
            setShowSecret(false);
        }
    }, [open]);

    useEffect(() => {
        const valid =
            errorDisplayName === '' &&
            errorIssuerUrl === '' &&
            errorClientId === '' &&
            formData.displayName.trim() !== '' &&
            formData.issuerUrl.trim() !== '' &&
            formData.clientId.trim() !== '';
        setIsDisabled(!valid);
    }, [formData, errorDisplayName, errorIssuerUrl, errorClientId]);

    const handleChange = e => {
        const { name, value } = e.target;
        setFormData(prev => ({ ...prev, [name]: value }));
        try {
            if (name === 'displayName') {
                emptyCheck(value, t);
                setErrorDisplayName('');
            }
            if (name === 'issuerUrl') {
                emptyCheck(value, t);
                setErrorIssuerUrl('');
            }
            if (name === 'clientId') {
                emptyCheck(value, t);
                setErrorClientId('');
            }
        } catch (error) {
            if (name === 'displayName') setErrorDisplayName(error.message);
            if (name === 'issuerUrl') setErrorIssuerUrl(error.message);
            if (name === 'clientId') setErrorClientId(error.message);
        }
    };

    const handleSave = () => {
        onSuccess?.({
            ssoProviderSeq: null,
            displayName: formData.displayName,
            status: 'ACTIVE',
            issuerUrl: formData.issuerUrl,
            clientId: formData.clientId,
            clientSecret: formData.clientSecret || '',
            scopes: '',
            allowIdpInitiated: false,
            registerDate: '',
        });
        onClose();
    };

    return (
        <Dialog
            open={open}
            onClose={onClose}
            PaperProps={{
                sx: {
                    borderRadius: '16px',
                    pt: '32px',
                    pb: '24px',
                    px: '32px',
                    margin: 0,
                    maxWidth: '480px !important',
                    width: '100%',
                    boxShadow: '0px 1px 10px rgba(58, 61, 73, 0.12)',
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
                <Typography
                    sx={{
                        fontSize: '16px',
                        fontWeight: 500,
                        lineHeight: '22px',
                        color: palette.text_100,
                    }}
                >
                    {t('admin.loginSetting.idpDialog.addTitle')}
                </Typography>
            </DialogTitle>

            <DialogContent sx={{ p: 0, mt: '18px', overflow: 'visible' }}>
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                    {/* IdP 이름 */}
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                        <FormLabel required sx={formLabelSx}>
                            {t('admin.loginSetting.idpName.label')}
                        </FormLabel>
                        <StyledTextField
                            fullWidth
                            name="displayName"
                            value={formData.displayName}
                            onChange={handleChange}
                            error={!!errorDisplayName}
                            errorMessage={errorDisplayName}
                            variant="outlined"
                            height={40}
                            placeholder={t('admin.loginSetting.idpName.placeholder')}
                        />
                    </Box>

                    {/* Issuer URL */}
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                        <FormLabel required sx={formLabelSx}>
                            {t('admin.loginSetting.issuerUrl.label')}
                        </FormLabel>
                        <StyledTextField
                            fullWidth
                            name="issuerUrl"
                            value={formData.issuerUrl}
                            onChange={handleChange}
                            error={!!errorIssuerUrl}
                            errorMessage={errorIssuerUrl}
                            variant="outlined"
                            height={40}
                            placeholder={t('admin.loginSetting.issuerUrl.placeholder')}
                        />
                    </Box>

                    {/* Client ID */}
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                        <FormLabel required sx={formLabelSx}>
                            {t('admin.loginSetting.clientId.label')}
                        </FormLabel>
                        <StyledTextField
                            fullWidth
                            name="clientId"
                            value={formData.clientId}
                            onChange={handleChange}
                            error={!!errorClientId}
                            errorMessage={errorClientId}
                            variant="outlined"
                            height={40}
                            placeholder={t('admin.loginSetting.clientId.placeholder')}
                        />
                    </Box>

                    {/* Client Secret */}
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                        <FormLabel sx={formLabelSx}>
                            {t('admin.loginSetting.clientSecret.label')}
                        </FormLabel>
                        <StyledTextField
                            fullWidth
                            name="clientSecret"
                            type={showSecret ? 'text' : 'password'}
                            value={formData.clientSecret}
                            onChange={handleChange}
                            variant="outlined"
                            height={40}
                            placeholder={t('admin.loginSetting.clientSecret.placeholder')}
                            InputProps={{
                                endAdornment: (
                                    <InputAdornment position="end">
                                        <IconButton
                                            onClick={() => setShowSecret(prev => !prev)}
                                            edge="end"
                                            size="small"
                                        >
                                            {showSecret ? (
                                                <VisibilityOutlinedIcon fontSize="small" />
                                            ) : (
                                                <VisibilityOffOutlinedIcon fontSize="small" />
                                            )}
                                        </IconButton>
                                    </InputAdornment>
                                ),
                            }}
                        />
                    </Box>
                </Box>
            </DialogContent>

            <DialogActions sx={{ p: 0, mt: '24px', gap: '6px', flexWrap: 'nowrap' }}>
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
                        {t('common.button.add')}
                    </Typography>
                </StyledButton>
            </DialogActions>
        </Dialog>
    );
}

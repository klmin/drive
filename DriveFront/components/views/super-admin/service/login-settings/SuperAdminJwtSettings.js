import { FormControl, FormLabel, MenuItem, Stack, Typography } from '@mui/material';
import Box from '@mui/material/Box';
import palette from '@/theme/palette';
import { useTranslations } from 'next-intl';
import StyledTextField from '@/components/common/input/StyledTextField';
import StyledSelect from '@/components/common/input/StyledSelect';

const KEY_SOURCE_TYPE_OPTIONS = ['public_key', 'jwks'];

const formLabelSx = {
    typography: 'body5',
    color: 'text_80',
};

const textFieldSx = {
    width: '100%',
};

const selectSx = {
    width: '100%',
};

export default function SuperAdminJwtSettings({
    formData,
    handleChange,
    errorMessageDisplayName,
    errorMessageIssuer,
    errorMessagePublicKey,
    errorMessageJwksUri,
    errorMessageErrorRedirectUrl,
}) {
    const t = useTranslations();

    return (
        <Box
            sx={{
                width: { xs: '100%', md: '100%' },
                p: '22px',
                borderRadius: '6px',
                outline: `1px ${palette.divider_8} solid`,
                outlineOffset: '-1px',
                background: palette.bg_gray,
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'flex-start',
                alignItems: 'flex-start',
                gap: '18px',
            }}
        >
            <Typography sx={{ typography: 'subtitle1', color: palette.text_80 }}>
                {t('adminLogin.jwt.title')}
            </Typography>

            <Box sx={{ width: '100%', display: 'flex', flexDirection: 'column', gap: '20px' }}>
                {/* 제공자 이름 + Issuer */}
                <Stack direction={{ xs: 'column', md: 'row' }} gap="10px" alignItems="flex-start">
                    <FormControl
                        fullWidth
                        sx={{ m: 0, display: 'flex', flexDirection: 'column', gap: '10px' }}
                    >
                        <FormLabel required sx={formLabelSx}>
                            {t('adminLogin.jwt.displayName')}
                        </FormLabel>
                        <StyledTextField
                            name="displayName"
                            value={formData.displayName}
                            onChange={handleChange}
                            error={!!errorMessageDisplayName}
                            errorMessage={errorMessageDisplayName}
                            variant="outlined"
                            height={40}
                            placeholder={t('admin.loginSetting.providerName.placeholder')}
                            sx={textFieldSx}
                        />
                    </FormControl>

                    <FormControl
                        fullWidth
                        sx={{ m: 0, display: 'flex', flexDirection: 'column', gap: '10px' }}
                    >
                        <FormLabel required sx={formLabelSx}>
                            {t('adminLogin.jwt.issuer')}
                        </FormLabel>
                        <StyledTextField
                            name="issuer"
                            value={formData.issuer}
                            onChange={handleChange}
                            error={!!errorMessageIssuer}
                            errorMessage={errorMessageIssuer}
                            variant="outlined"
                            height={40}
                            placeholder={t('admin.loginSetting.issuer.placeholder')}
                            sx={textFieldSx}
                        />
                    </FormControl>
                </Stack>

                {/* 로그인 페이지 URL */}
                <Stack direction="row" gap="32px" alignItems="flex-start">
                    <FormControl
                        fullWidth
                        sx={{ m: 0, display: 'flex', flexDirection: 'column', gap: '10px' }}
                    >
                        <FormLabel required sx={formLabelSx}>
                            {t('adminLogin.jwt.errorRedirectUrl')}
                        </FormLabel>
                        <Box sx={{ display: 'flex', flexDirection: 'column', gap: '7px' }}>
                            <StyledTextField
                                name="errorRedirectUrl"
                                value={formData.errorRedirectUrl}
                                onChange={handleChange}
                                placeholder={t('admin.loginSetting.loginPageUrl.placeholder')}
                                error={!!errorMessageErrorRedirectUrl}
                                errorMessage={errorMessageErrorRedirectUrl}
                                variant="outlined"
                                height={40}
                                sx={textFieldSx}
                            />
                            <Typography variant="body7" color="text_60">
                                {t('adminLogin.jwt.errorRedirectUrlDesc')}
                            </Typography>
                        </Box>
                    </FormControl>
                </Stack>

                {/* Audience + Email Claim Name */}
                <Stack direction={{ xs: 'column', md: 'row' }} gap="10px" alignItems="flex-start">
                    <FormControl
                        fullWidth
                        sx={{ m: 0, display: 'flex', flexDirection: 'column', gap: '10px' }}
                    >
                        <FormLabel sx={formLabelSx}>{t('adminLogin.jwt.audience')}</FormLabel>
                        <StyledTextField
                            name="audience"
                            value={formData.audience}
                            onChange={handleChange}
                            variant="outlined"
                            height={40}
                            placeholder={t('admin.loginSetting.audience.placeholder')}
                            sx={textFieldSx}
                        />
                    </FormControl>

                    <FormControl
                        fullWidth
                        sx={{ m: 0, display: 'flex', flexDirection: 'column', gap: '10px' }}
                    >
                        <FormLabel sx={formLabelSx}>{t('adminLogin.jwt.emailClaimName')}</FormLabel>
                        <StyledTextField
                            name="emailClaimName"
                            value={formData.emailClaimName}
                            onChange={handleChange}
                            variant="outlined"
                            height={40}
                            placeholder={t('admin.loginSetting.emailClaimName.placeholder')}
                            sx={textFieldSx}
                        />
                    </FormControl>
                </Stack>

                {/* Key Source Type */}
                <FormControl
                    fullWidth
                    sx={{ m: 0, display: 'flex', flexDirection: 'column', gap: '10px' }}
                >
                    <FormLabel required sx={formLabelSx}>
                        {t('adminLogin.jwt.keySourceType')}
                    </FormLabel>
                    <StyledSelect
                        select
                        name="keySourceType"
                        value={formData.keySourceType}
                        onChange={handleChange}
                        height={40}
                        variant="outlined"
                        sx={{
                            ...selectSx,
                        }}
                    >
                        {KEY_SOURCE_TYPE_OPTIONS.map(opt => (
                            <MenuItem key={opt} value={opt}>
                                <Typography variant="body5">{opt}</Typography>
                            </MenuItem>
                        ))}
                    </StyledSelect>
                </FormControl>

                {/* Public Key */}
                {formData.keySourceType === 'public_key' && (
                    <FormControl
                        fullWidth
                        sx={{ m: 0, display: 'flex', flexDirection: 'column', gap: '10px' }}
                    >
                        <FormLabel required sx={formLabelSx}>
                            {t('adminLogin.jwt.publicKey')}
                        </FormLabel>
                        <StyledTextField
                            name="publicKey"
                            value={formData.publicKey}
                            onChange={handleChange}
                            error={!!errorMessagePublicKey}
                            errorMessage={errorMessagePublicKey}
                            multiline
                            rows={4}
                            variant="outlined"
                            placeholder={t('admin.loginSetting.publicKey.placeholder')}
                            sx={{
                                ...textFieldSx,
                                '& .MuiInputBase-inputMultiline': {
                                    pt: '8px',
                                    px: '12px',
                                },
                            }}
                        />
                    </FormControl>
                )}

                {/* JWKS URI */}
                {formData.keySourceType === 'jwks' && (
                    <FormControl
                        fullWidth
                        sx={{ m: 0, display: 'flex', flexDirection: 'column', gap: '10px' }}
                    >
                        <FormLabel required sx={formLabelSx}>
                            {t('adminLogin.jwt.jwksUri')}
                        </FormLabel>
                        <StyledTextField
                            name="jwksUri"
                            value={formData.jwksUri}
                            onChange={handleChange}
                            placeholder="https://example.com/.well-known/jwks.json"
                            error={!!errorMessageJwksUri}
                            errorMessage={errorMessageJwksUri}
                            variant="outlined"
                            height={40}
                            sx={textFieldSx}
                        />
                    </FormControl>
                )}
            </Box>
        </Box>
    );
}

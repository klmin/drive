import { Stack, Typography, FormControl, RadioGroup, FormControlLabel } from '@mui/material';
import Box from '@mui/material/Box';
import palette from '@/theme/palette';
import React, { useEffect, useRef, useState } from 'react';
import api from '@/utils/api';
import { useTranslations } from 'next-intl';
import { useUser } from '@/context/UserContext';
import StyledButton from '@/components/common/button/StyledButton';
import { useDialog } from '@/provider/DialogProvider';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import StyledTextField from '@/components/common/input/StyledTextField';
import typography from '@/theme/typography';
import StyledRadioButton from '@/components/common/button/StyledRadioButton';
import BrandingImageUploadRow from './BrandingImageUploadRow';
import { useRouter } from '@/hooks/useRouter';

export const BRANDING_IMAGE_TYPE = {
    FAVICON: 'favicon',
    SYMBOL_LOGO: 'symbolLogo',
    DEFAULT_LOGO: 'defaultLogo',
    EMAIL_LOGO: 'emailLogo',
};

export default function Branding() {
    const t = useTranslations();
    const router = useRouter();
    const user = useUser();
    const { openDialog } = useDialog();
    const { showSnackbar } = useSnackbarContext();

    const [brandingSeq, setBrandingSeq] = useState('');
    const [tabTitle, setTabTitle] = useState('');
    const [serviceName, setServiceName] = useState('');
    const [logoSetting, setLogoSetting] = useState('default'); // 'default' or 'custom'
    const [faviconFile, setFaviconFile] = useState(null);
    const [faviconPreview, setFaviconPreview] = useState('');
    const [faviconError, setFaviconError] = useState('');
    const faviconInputRef = useRef(null);

    const [symbolLogoFile, setSymbolLogoFile] = useState(null);
    const [symbolLogoPreview, setSymbolLogoPreview] = useState('');
    const [symbolLogoError, setSymbolLogoError] = useState('');
    const symbolLogoInputRef = useRef(null);

    const [basicLogoFile, setBasicLogoFile] = useState(null);
    const [basicLogoPreview, setBasicLogoPreview] = useState('');
    const [basicLogoError, setBasicLogoError] = useState('');
    const basicLogoInputRef = useRef(null);

    const [emailLogoFile, setEmailLogoFile] = useState(null);
    const [emailLogoPreview, setEmailLogoPreview] = useState('');
    const [emailLogoError, setEmailLogoError] = useState('');
    const emailLogoInputRef = useRef(null);

    const hasImageError = faviconError || symbolLogoError || basicLogoError || emailLogoError;
    const hasTextError = tabTitle.trim().length < 2 || serviceName.trim().length < 2;
    const allFilesUploaded =
        logoSetting === 'default' ||
        (faviconFile && symbolLogoFile && basicLogoFile && emailLogoFile);

    const mimeToExt = {
        'image/png': '.png',
        'image/svg+xml': '.svg',
        'image/vnd.microsoft.icon': '.ico',
        'image/x-icon': '.ico',
        'image/jpeg': '.jpg',
        'image/gif': '.gif',
        'image/webp': '.webp',
    };

    const fetchImagePreview = async urlPath => {
        try {
            const res = await api.Get(urlPath, { responseType: 'blob' });
            if (res?.status === 200 && res.data instanceof Blob) {
                const disposition = res.headers?.['content-disposition'] ?? '';
                const dispositionMatch = disposition.match(
                    /filename\*?=(?:UTF-8'')?["']?([^"';\n]+)/i
                );
                let fileName;
                if (dispositionMatch) {
                    fileName = decodeURIComponent(dispositionMatch[1].trim());
                } else {
                    const typeMatch = urlPath.match(/\/images\/([^?]+)/);
                    const typeName = typeMatch ? typeMatch[1] : 'image';
                    const ext = mimeToExt[res.data.type] ?? '';
                    fileName = typeName + ext;
                }
                const file = new File([res.data], fileName, { type: res.data.type });
                const preview = URL.createObjectURL(res.data);
                return { file, preview };
            }
        } catch {
            // 이미지 없으면 null 반환
        }
        return { file: null, preview: '' };
    };

    const getBrandingInfo = async () => {
        const res = await api.Get('admins/branding', {});
        if (res?.data?.code === 200) {
            setBrandingSeq(res.data?.data?.brandingSeq ?? '');
            setTabTitle(res.data?.data?.tabTitle ?? '');
            setServiceName(res.data?.data?.serviceName ?? '');

            const useDefault = res.data?.data?.useDefault ?? false;
            setLogoSetting(useDefault ? 'default' : 'custom');

            const [favicon, logoIcon, logoImage, emailLogo] = await Promise.all([
                fetchImagePreview('admins/branding/images/favicon?source=custom'),
                fetchImagePreview('admins/branding/images/logo-icon?source=custom'),
                fetchImagePreview('admins/branding/images/logo-image?source=custom'),
                fetchImagePreview('admins/branding/images/email-logo?source=custom'),
            ]);
            // const [favicon, logoIcon, logoImage, emailLogo] = await Promise.all([
            //     { file: null, preview: '' },
            //     { file: null, preview: '' },
            //     { file: null, preview: '' },
            //     { file: null, preview: '' },
            // ]);
            if (favicon.file) {
                setFaviconFile(favicon.file);
                setFaviconPreview(favicon.preview);
            }
            if (logoIcon.file) {
                setSymbolLogoFile(logoIcon.file);
                setSymbolLogoPreview(logoIcon.preview);
            }
            if (logoImage.file) {
                setBasicLogoFile(logoImage.file);
                setBasicLogoPreview(logoImage.preview);
            }
            if (emailLogo.file) {
                setEmailLogoFile(emailLogo.file);
                setEmailLogoPreview(emailLogo.preview);
            }
        }
    };

    const validateImageFile = (file, acceptedExts, maxBytes) => {
        const ext = '.' + file.name.split('.').pop().toLowerCase();
        if (!acceptedExts.includes(ext))
            return { error: t('admin.branding.upload.errorFileSizeExceeded') };
        if (file.size > maxBytes)
            return { error: t('admin.branding.upload.errorFileSizeExceeded') };
        return { error: '' };
    };

    const handleFaviconChange = e => {
        const file = e.target.files?.[0];
        if (!file) return;
        const { error } = validateImageFile(file, ['.ico', '.png'], 500 * 1024);
        setFaviconError(error);
        if (!error) {
            setFaviconFile(file);
            setFaviconPreview(URL.createObjectURL(file));
        }
    };

    const handleSymbolLogoChange = e => {
        const file = e.target.files?.[0];
        if (!file) return;
        const { error } = validateImageFile(file, ['.png', '.svg'], 1 * 1024 * 1024);
        setSymbolLogoError(error);
        if (!error) {
            setSymbolLogoFile(file);
            setSymbolLogoPreview(URL.createObjectURL(file));
        }
    };

    const handleBasicLogoChange = e => {
        const file = e.target.files?.[0];
        if (!file) return;
        const { error } = validateImageFile(file, ['.png', '.svg'], 2 * 1024 * 1024);
        setBasicLogoError(error);
        if (!error) {
            setBasicLogoFile(file);
            setBasicLogoPreview(URL.createObjectURL(file));
        }
    };

    const handleEmailLogoChange = e => {
        const file = e.target.files?.[0];
        if (!file) return;
        const { error } = validateImageFile(file, ['.png', '.svg'], 2 * 1024 * 1024);
        setEmailLogoError(error);
        if (!error) {
            setEmailLogoFile(file);
            setEmailLogoPreview(URL.createObjectURL(file));
        }
    };

    useEffect(() => {
        if (!user) return;
    }, [user]);

    useEffect(() => {
        getBrandingInfo();
    }, []);

    const handleSave = async () => {
        openDialog('alert', {
            title: t('common.saveConfirmDialog.title'),
            buttonName: t('common.button.save'),
            maxWidth: '400px !important',
            onClick: async () => {
                const res = await api.PutForm('admins/branding', {
                    brandingSeq: brandingSeq, // 현재는 1하나만 존재
                    tabTitle: tabTitle,
                    serviceName: serviceName,
                    useDefault: logoSetting === 'default',
                    favicon: faviconFile,
                    logoIcon: symbolLogoFile,
                    logoImage: basicLogoFile,
                    emailLogo: emailLogoFile,
                });
                if (res?.status === 200) {
                    showSnackbar(t('adminSetting.save.success'), 'success');
                    router.reload();
                } else {
                    showSnackbar(t('adminSetting.save.fail'), 'error');
                }
            },
        });
    };

    const handleLogoSettingChange = event => {
        setLogoSetting(event.target.value);
    };

    return (
        <Box
            sx={{
                maxWidth: { xs: 474, md: 840 },
                width: '100%',
                alignSelf: { xs: 'flex-start', md: 'center' },
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'flex-end',
                gap: '32px',
                pt: {
                    md: 'calc(32px + 30px)',
                    xs: 'calc(32px + 30px)',
                },
            }}
        >
            {/** 콘텐츠 영역 (라벨 + 구분선 + 로고) */}
            <Box
                sx={{
                    alignSelf: 'stretch',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '48px',
                }}
            >
                {/** 라벨 섹션 */}
                <Box
                    sx={{
                        display: 'flex',
                        flexDirection: 'column',
                        gap: { xs: '24px', md: '32px' },
                    }}
                >
                    {/** 타이틀 */}
                    <Typography variant="h5" color="text_100">
                        {t('admin.branding.page.title')}
                    </Typography>

                    {/** 필드 목록 */}
                    <Box
                        sx={{
                            display: 'flex',
                            flexDirection: 'column',
                            gap: '32px',
                        }}
                    >
                        {/* 웹 브라우저 타이틀 */}
                        <Stack
                            direction={{ xs: 'column', md: 'row' }}
                            gap={{ xs: '10px', md: '32px' }}
                        >
                            <Stack sx={{ minWidth: 260 }} gap={'4px'}>
                                <Typography variant="body4" color="text_80">
                                    {t('admin.branding.browserTitle.title')}
                                </Typography>
                                <Typography variant="body7" color="text_80">
                                    {t('admin.branding.browserTitle.desc')}
                                </Typography>
                            </Stack>
                            <Stack sx={{ flex: 1 }} gap={'10px'}>
                                <StyledTextField
                                    value={tabTitle}
                                    onChange={e => setTabTitle(e.target.value)}
                                    onBlur={e => setTabTitle(e.target.value.trim())}
                                    inputProps={{ maxLength: 30 }}
                                    height={40}
                                    sx={{
                                        width: '100%',
                                        '& .MuiInputBase-root': {
                                            backgroundColor: `${palette.btn_white_bg_normal} !important`,
                                            border: `1px solid ${palette.control_border_normal} !important`,
                                        },
                                        '& .MuiInputBase-input': {
                                            ...typography.body3,
                                            color: palette.text_80,
                                            backgroundColor: 'transparent !important',
                                        },
                                    }}
                                />
                                <Typography variant="body7" color="text_60">
                                    {t('admin.branding.label.helper')}
                                </Typography>
                            </Stack>
                        </Stack>

                        {/* 서비스명 */}
                        <Stack
                            direction={{ xs: 'column', md: 'row' }}
                            gap={{ xs: '10px', md: '32px' }}
                        >
                            <Stack sx={{ minWidth: 260 }} gap={'4px'}>
                                <Typography variant="body4" color="text_80">
                                    {t('admin.branding.serviceName.title')}
                                </Typography>
                                <Typography variant="body7" color="text_80">
                                    {t('admin.branding.serviceName.desc')}
                                </Typography>
                            </Stack>
                            <Stack sx={{ flex: 1 }} gap={'10px'}>
                                <StyledTextField
                                    value={serviceName}
                                    onChange={e => setServiceName(e.target.value)}
                                    onBlur={e => setServiceName(e.target.value.trim())}
                                    inputProps={{ maxLength: 30 }}
                                    height={40}
                                    sx={{
                                        width: '100%',
                                        '& .MuiInputBase-root': {
                                            backgroundColor: `${palette.btn_white_bg_normal} !important`,
                                            border: `1px solid ${palette.control_border_normal} !important`,
                                        },
                                        '& .MuiInputBase-input': {
                                            ...typography.body3,
                                            color: palette.text_80,
                                            backgroundColor: 'transparent !important',
                                        },
                                    }}
                                />
                                <Typography variant="body7" color="text_60">
                                    {t('admin.branding.label.helper')}
                                </Typography>
                            </Stack>
                        </Stack>
                    </Box>
                </Box>

                {/** 구분선 */}
                <Box sx={{ height: '1px', background: palette.divider_8 }} />

                {/** 로고 섹션 */}
                <Box
                    sx={{
                        display: 'flex',
                        flexDirection: 'column',
                        gap: { xs: '20px', md: '28px' },
                    }}
                >
                    {/** 타이틀 + 라디오버튼 가로 나열 */}
                    <Stack direction="row" alignItems="center" gap={'32px'}>
                        <Typography variant="h5" color="text_100">
                            {t('admin.branding.logo.title')}
                        </Typography>
                        <FormControl>
                            <RadioGroup
                                name={'logoSetting'}
                                value={logoSetting}
                                onChange={handleLogoSettingChange}
                                sx={{ display: 'flex', flexDirection: 'row', gap: '20px' }}
                            >
                                <FormControlLabel
                                    value="default"
                                    control={<StyledRadioButton value="default" />}
                                    label={
                                        <Typography variant="body5" color="text_80">
                                            {t('admin.branding.logo.default')}
                                        </Typography>
                                    }
                                />
                                <FormControlLabel
                                    value="custom"
                                    control={<StyledRadioButton value="custom" />}
                                    label={
                                        <Typography variant="body5" color="text_80">
                                            {t('admin.branding.logo.custom')}
                                        </Typography>
                                    }
                                />
                            </RadioGroup>
                        </FormControl>
                    </Stack>

                    {logoSetting === 'default' && (
                        <Typography variant="body5" color="text_80">
                            {t('admin.branding.logo.defaultOptionDesc')}
                        </Typography>
                    )}

                    {logoSetting !== 'default' && (
                        <Stack sx={{ gap: '36px' }}>
                            {/** 파비콘 */}
                            <BrandingImageUploadRow
                                imageType={BRANDING_IMAGE_TYPE.FAVICON}
                                title={t('admin.branding.favicon.title')}
                                description={t('admin.branding.favicon.desc')}
                                accept=".ico,.png"
                                formatText={t('admin.branding.favicon.uploadFormat')}
                                preview={faviconPreview}
                                inputRef={faviconInputRef}
                                onChange={handleFaviconChange}
                                errorMessage={faviconError}
                            />

                            {/** 심볼형 로고 */}
                            <BrandingImageUploadRow
                                imageType={BRANDING_IMAGE_TYPE.SYMBOL_LOGO}
                                title={t('admin.branding.symbolLogo.title')}
                                description={t('admin.branding.symbolLogo.desc')}
                                accept=".png,.svg"
                                formatText={t('admin.branding.symbolLogo.uploadFormat')}
                                preview={symbolLogoPreview}
                                inputRef={symbolLogoInputRef}
                                onChange={handleSymbolLogoChange}
                                errorMessage={symbolLogoError}
                            />

                            {/** 기본형 로고 */}
                            <BrandingImageUploadRow
                                imageType={BRANDING_IMAGE_TYPE.DEFAULT_LOGO}
                                title={t('admin.branding.basicLogo.title')}
                                description={t('admin.branding.basicLogo.desc')}
                                accept=".png,.svg"
                                formatText={t('admin.branding.basicLogo.uploadFormat')}
                                preview={basicLogoPreview}
                                inputRef={basicLogoInputRef}
                                onChange={handleBasicLogoChange}
                                errorMessage={basicLogoError}
                            />

                            {/** 이메일 로고 */}
                            <BrandingImageUploadRow
                                imageType={BRANDING_IMAGE_TYPE.EMAIL_LOGO}
                                title={t('admin.branding.emailLogo.title')}
                                description={t('admin.branding.emailLogo.desc')}
                                accept=".png,.svg"
                                formatText={t('admin.branding.emailLogo.uploadFormat')}
                                preview={emailLogoPreview}
                                inputRef={emailLogoInputRef}
                                onChange={handleEmailLogoChange}
                                errorMessage={emailLogoError}
                            />
                        </Stack>
                    )}
                </Box>
            </Box>

            {/** 취소 / 저장 버튼 */}
            <Stack
                direction="row"
                gap={0.5}
                alignItems="center"
                justifyContent={{ xs: 'flex-start', md: 'flex-end' }}
                sx={{ width: '100%' }}
            >
                {/* <StyledButton
                    color={'btn_border'}
                    variant="outlined"
                    onClick={() => router.reload()}
                    sx={{
                        order: { xs: 2, md: 1 },
                        minWidth: { xs: 80, md: 'fit-content' },
                        width: { xs: 80, md: 'auto' },
                        height: { xs: 32, md: 24 },
                        px: { xs: '16px', md: '8px' },
                        py: 0,
                        borderRadius: '4px',
                        backgroundColor: palette.white,
                        overflow: 'hidden',
                        border: `1px solid ${palette.btn_border}`,
                        boxShadow: 'none',
                        justifyContent: 'center',
                        alignItems: 'center',
                        '&:hover': {
                            backgroundColor: palette.white,
                            border: `1px solid ${palette.btn_border}`,
                            boxShadow: 'none',
                        },
                    }}
                >
                    <Typography
                        variant={'body9'}
                        color={'text_100'}
                        sx={{
                            textAlign: 'center',
                            lineHeight: '16px',
                            whiteSpace: 'nowrap',
                        }}
                    >
                        {t('common.button.cancel')}
                    </Typography>
                </StyledButton> */}

                <StyledButton
                    color={'btn_point_bg'}
                    variant="contained"
                    onClick={handleSave}
                    disabled={
                        hasTextError ||
                        (logoSetting !== 'default' && (!!hasImageError || !allFilesUploaded))
                    }
                    sx={{
                        order: { xs: 1, md: 2 },
                        minWidth: { xs: 80, md: 'fit-content' },
                        width: { xs: 80, md: 'auto' },
                        height: { xs: 32, md: 24 },
                        px: { xs: '16px', md: '8px' },
                        py: 0,
                        borderRadius: '4px',
                        backgroundColor: palette.btn_point_bg,
                        overflow: 'hidden',
                        boxShadow: 'none',
                        justifyContent: 'center',
                        alignItems: 'center',
                        '&:hover': {
                            backgroundColor: palette.btn_point_bg,
                            boxShadow: 'none',
                        },
                        '&.Mui-disabled': {
                            boxShadow: 'none',
                        },
                    }}
                >
                    <Typography
                        variant={'body9'}
                        color={'white'}
                        sx={{
                            textAlign: 'center',
                            lineHeight: '16px',
                            whiteSpace: 'nowrap',
                        }}
                    >
                        {t('common.button.save')}
                    </Typography>
                </StyledButton>
            </Stack>
        </Box>
    );
}

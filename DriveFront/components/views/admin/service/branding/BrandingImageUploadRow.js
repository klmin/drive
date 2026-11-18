import { Stack, Typography } from '@mui/material';
import Box from '@mui/material/Box';
import Image from 'next/image';
import { useTranslations } from 'next-intl';
import palette from '@/theme/palette';
import StyledButton from '@/components/common/button/StyledButton';

export const BRANDING_IMAGE_TYPE = {
    FAVICON: 'favicon',
    SYMBOL_LOGO: 'symbolLogo',
    DEFAULT_LOGO: 'defaultLogo',
    EMAIL_LOGO: 'emailLogo',
};

/**
 * 브랜딩 이미지 업로드 행 컴포넌트
 * 좌측: 예시 이미지 / 우측: 업로드 박스
 */
export default function BrandingImageUploadRow({
    imageType,
    title,
    description,
    accept,
    formatText,
    preview,
    inputRef,
    onChange,
    errorMessage,
}) {
    const t = useTranslations();

    const getUploadButtonText = () => {
        if (!errorMessage) return t('admin.branding.upload.action');
        if (!preview) return t('admin.branding.upload.reupload');
        return t('admin.branding.upload.selectAnother');
    };

    const getPreviewSize = () => {
        switch (imageType) {
            case BRANDING_IMAGE_TYPE.FAVICON:
                return { width: 32, height: 32 };
            case BRANDING_IMAGE_TYPE.SYMBOL_LOGO:
                return { width: 64, height: 64 };
            case BRANDING_IMAGE_TYPE.DEFAULT_LOGO:
            case BRANDING_IMAGE_TYPE.EMAIL_LOGO:
                return { width: 140, height: 20 };
            default:
                return { width: 156, height: 86 };
        }
    };

    const previewSize = getPreviewSize();

    // 예시 이미지
    const getMockConfig = () => {
        switch (imageType) {
            case BRANDING_IMAGE_TYPE.FAVICON:
                return {
                    wrapperLeft: 'calc(50% - 100px)',
                    wrapperWidth: 200,
                    imgSrc: '/admin/branding/icon-ic-favicon-ex.png',
                    imgWidth: 200,
                    imgHeight: 126,
                };
            case BRANDING_IMAGE_TYPE.SYMBOL_LOGO:
                return {
                    wrapperLeft: 'calc(50% - 100px)',
                    wrapperWidth: 200,
                    imgSrc: '/admin/branding/icon-ic-symbol-logo-ex.png',
                    imgWidth: 200,
                    imgHeight: 126,
                };
            case BRANDING_IMAGE_TYPE.DEFAULT_LOGO:
                return {
                    wrapperLeft: 'calc(50% - 100px)',
                    wrapperWidth: 200,
                    imgSrc: '/admin/branding/icon-ic-default-logo-ex.png',
                    imgWidth: 200,
                    imgHeight: 126,
                };
            case BRANDING_IMAGE_TYPE.EMAIL_LOGO:
                return {
                    wrapperLeft: 'calc(50% - 90px)',
                    wrapperWidth: 180,
                    imgSrc: '/admin/branding/icon-ic-email-logo-ex.png',
                    imgWidth: 180,
                    imgHeight: 126,
                };
            default:
                return null;
        }
    };

    const mockConfig = getMockConfig();

    const renderMockUI = () => {
        if (!mockConfig) return null;

        return (
            <Box
                sx={{
                    width: mockConfig.wrapperWidth,
                    height: 126,
                    position: 'absolute',
                    left: mockConfig.wrapperLeft,
                    top: 0,
                    overflow: 'hidden',
                }}
            >
                <Image
                    src={mockConfig.imgSrc}
                    alt={title}
                    width={mockConfig.imgWidth}
                    height={mockConfig.imgHeight}
                    style={{
                        position: 'absolute',
                        width: mockConfig.imgWidth,
                        height: mockConfig.imgHeight,
                    }}
                />
            </Box>
        );
    };

    return (
        <Stack direction="column" gap={{ xs: '16px', md: 0 }}>
            <Stack sx={{ width: '100%', pb: { xs: 0, md: '8px' } }} gap={'4px'}>
                <Typography variant="body4" color="text_80">
                    {title}
                </Typography>
                <Typography variant="body7" color="text_80">
                    {description}
                </Typography>
            </Stack>

            <Stack direction={{ xs: 'column', md: 'row' }} gap={{ xs: '8px', md: '16px' }}>
                <Stack
                    sx={{
                        minWidth: { md: 272 },
                        display: 'flex',
                        width: { xs: '100%', md: 272 },
                    }}
                    gap={1}
                >
                    <Box
                        sx={{
                            width: '100%',
                            height: 126,
                            position: 'relative',
                            backgroundColor: '#F1F5F9',
                            overflow: 'hidden',
                            borderRadius: '6px',
                            border: '1px solid #EFF3F8',
                        }}
                    >
                        {renderMockUI()}
                    </Box>
                </Stack>

                <Stack sx={{ flex: 1 }} gap={1}>
                    <input
                        ref={inputRef}
                        type="file"
                        accept={accept}
                        style={{ display: 'none' }}
                        onChange={onChange}
                    />

                    <Stack
                        direction={{ xs: 'column', md: 'row' }}
                        alignItems="center"
                        gap={{ xs: '16px', md: '24px' }}
                        sx={{
                            flex: 1,
                            pt: { xs: '16px', md: '20px' },
                            pb: { xs: '20px', md: '20px' },
                            pl: { xs: '20px', md: '20px' },
                            pr: { xs: '20px', md: '24px' },
                            backgroundColor: palette.white,
                            borderRadius: '6px',
                            border: `1px solid ${palette.control_border_normal}`,
                        }}
                    >
                        <Box
                            sx={{
                                width: 156,
                                height: 86,
                                flexShrink: 0,
                                backgroundColor: preview ? palette.white : '#F9F9F9',
                                borderRadius: '4px',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                overflow: 'hidden',
                                alignSelf: { xs: 'center', md: 'auto' },
                            }}
                        >
                            {preview ? (
                                <Box
                                    sx={{
                                        width: previewSize.width,
                                        height: previewSize.height,
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        flexShrink: 0,
                                    }}
                                >
                                    <Image
                                        src={preview}
                                        alt={`${title} preview`}
                                        width={previewSize.width}
                                        height={previewSize.height}
                                        style={{
                                            width: 'auto',
                                            height: 'auto',
                                            maxWidth: '100%',
                                            maxHeight: '100%',
                                            objectFit: 'contain',
                                        }}
                                    />
                                </Box>
                            ) : (
                                <Image
                                    src="/admin/branding/image-empty.png"
                                    alt={`${title} preview`}
                                    width={156}
                                    height={86}
                                    style={{ objectFit: 'contain', width: '100%', height: '100%' }}
                                />
                            )}
                        </Box>

                        <Stack
                            sx={{
                                flex: 1,
                                width: '100%',
                                alignItems: { xs: 'center', md: 'flex-start' },
                                justifyContent: 'center',
                                textAlign: { xs: 'center', md: 'left' },
                            }}
                            gap={'8px'}
                        >
                            <StyledButton
                                variant="outlined"
                                color="btn_border"
                                onClick={() => inputRef.current?.click()}
                                sx={{
                                    height: 28,
                                    minWidth: 'fit-content',
                                    px: 0,
                                    py: 0,
                                    pl: '6px',
                                    pr: '8px',
                                    gap: '4px',
                                    borderRadius: '4px',
                                    backgroundColor: palette.white,
                                    border: `1px solid ${palette.btn_border}`,
                                }}
                            >
                                <Image
                                    src="/admin/branding/icon-ic-logo-upload.svg"
                                    alt="upload"
                                    width={20}
                                    height={20}
                                />
                                <Typography variant="body9" color="text_80">
                                    {getUploadButtonText()}
                                </Typography>
                            </StyledButton>

                            <Stack
                                sx={{
                                    width: '100%',
                                    alignItems: { xs: 'center', md: 'flex-start' },
                                }}
                                gap={'4px'}
                            >
                                <Typography
                                    variant="caption2"
                                    color="text_40"
                                    sx={{
                                        width: '100%',
                                        textAlign: { xs: 'center', md: 'left' },
                                    }}
                                >
                                    {formatText}
                                </Typography>
                            </Stack>

                            {errorMessage && (
                                <Box
                                    display="flex"
                                    alignItems="center"
                                    justifyContent={{ xs: 'center', md: 'flex-start' }}
                                    gap={1}
                                    sx={{ width: '100%' }}
                                >
                                    <svg
                                        width="16"
                                        height="16"
                                        viewBox="0 0 16 16"
                                        fill="none"
                                        xmlns="http://www.w3.org/2000/svg"
                                    >
                                        <mask
                                            id="branding-err-mask"
                                            maskUnits="userSpaceOnUse"
                                            x="1"
                                            y="1"
                                            width="14"
                                            height="14"
                                        >
                                            <circle cx="8" cy="8" r="6.4" fill="#D9D9D9" />
                                        </mask>
                                        <g mask="url(#branding-err-mask)">
                                            <path
                                                fill={palette.status_error}
                                                d="M1.018 1.018h13.964v13.964H1.018z"
                                            />
                                        </g>
                                        <path
                                            d="M7.678 4.544a.797.797 0 0 1 1.116.802l-.318 3.496a.478.478 0 0 1-.951 0l-.319-3.496a.797.797 0 0 1 .472-.802zM8.762 10.762a.761.761 0 1 1-1.523 0 .761.761 0 0 1 1.523 0z"
                                            fill={palette.white}
                                        />
                                    </svg>
                                    <Typography
                                        variant="body7"
                                        color="status_error"
                                        sx={{ textAlign: { xs: 'center', md: 'left' } }}
                                    >
                                        {errorMessage}
                                    </Typography>
                                </Box>
                            )}
                        </Stack>
                    </Stack>
                </Stack>
            </Stack>
        </Stack>
    );
}

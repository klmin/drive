import React, { useEffect, useState } from 'react';
import {
    Box,
    CircularProgress,
    IconButton,
    Stack,
    Typography,
    styled,
    tooltipClasses,
} from '@mui/material';
import Image from 'next/image';
import StyledButton from '@/components/common/button/StyledButton';
import { useTranslations } from 'next-intl';
import api from '@/utils/api';
import { getFileByFileType, getItemExt, isIntellectFile } from '@/constants/FILES';
import { getOfficeEditUrl, getOfficeViewUrl } from '@/apis/webOfficeApi';
import { registerRecent } from '@/apis/recentApi';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import palette from '@/theme/palette';
import { useRouter } from '@/hooks/useRouter';
import { useDriveDownload } from '@/hooks/useDriveDownload';
import { getIntellectUrl } from '@/apis/intellectApi';
import { closePendingPopup, openDocumentWindow, openPendingPopup } from '@/utils/documentWindow';
import { hasLockedFile } from '@/utils/resourceUtils';
import { BaseTooltip } from '../tooltip/BaseTooltip';
import useClientConfig from '@/hooks/useClientConfig';
import useOfficeFeature from '@/hooks/useOfficeFeature';
import useOfficeViewer from '@/hooks/useOfficeViewer';
import LicenseWatermark from '@/components/common/LicenseWatermark';

export const CustomTooltip = styled(({ className, ...props }) => (
    <BaseTooltip {...props} arrow classes={{ popper: className }} />
))(() => ({
    [`& .${tooltipClasses.popper}`]: {
        zIndex: 10000, // ✅ overlay/iframe 위
    },
    [`& .${tooltipClasses.tooltip}`]: {
        backgroundColor: palette.white,
        color: palette.text_100,
        border: '1px solid #dadce0',
        borderRadius: 6,
        fontSize: 13,
        padding: '8px 12px',
        boxShadow: '0px 2px 6px rgba(60,64,67,0.15)',
    },
    [`& .${tooltipClasses.arrow}`]: {
        color: palette.white,
        '&::before': {
            border: '1px solid #dadce0',
            boxSizing: 'border-box',
        },
    },
}));

const MIN_SCALE = 1; // 원본 크기
const MAX_SCALE = 4; // 4배까지 확대 가능

// 본문 영역 폭 (화면 반응형) — 텍스트·문서는 이 중 95% 를 쓴다
const STAGE_WIDTH = { xs: '100vw', sm: 'clamp(100px, 95vw, 1920px)' };
const CONTENT_WIDTH = 'var(--preview-stage-w) * 0.95';
// 워터마크 우측 여백 — 텍스트·문서는 가운데 정렬된 본문(텍스트는 최대 1320px) 오른쪽 끝에 맞춘다
const OFFICE_WATERMARK_RIGHT = `calc((100vw - ${CONTENT_WIDTH}) / 2)`;
const TXT_WATERMARK_RIGHT = `calc((100vw - min(${CONTENT_WIDTH}, 1320px)) / 2)`;
// 이미지·미리보기 불가는 본문 폭이 일정하지 않아 md 이상은 문서와 같은 선, md 미만은 고정값
const MOBILE_WATERMARK_RIGHT = 16;

export default function PreviewDialog({ open, onClose, hideClose = false, ...props }) {
    const t = useTranslations();
    const router = useRouter();
    const { showSnackbar } = useSnackbarContext();
    const { previewItem, user, preferEdit = false } = props;
    const { intellectEnabled } = useClientConfig();
    const officeFeature = useOfficeFeature();
    const { isOfficeViewerTarget, openOfficeViewer } = useOfficeViewer();

    const isTrash = router.asPath.startsWith('/trash');

    const [item, setItem] = useState(null);
    const [imageSize, setImageSize] = useState({ width: 0, height: 0 });
    const [imageUrl, setImageUrl] = useState('');
    const [txt, setTxt] = useState('');
    const [scale, setScale] = useState(1);
    const [officeUrl, setOfficeUrl] = useState('');
    // 미리보기 준비 중(상세·URL·본문 조회) — 끝나기 전엔 '미리보기 불가' 대신 로딩 표시
    const [loading, setLoading] = useState(true);
    // 오피스·인텔릭트 iframe 문서 로드 완료 여부
    const [frameLoaded, setFrameLoaded] = useState(false);

    const canView = item?.canView === true;
    const canEdit = item?.canEdit === true;
    const canDownload = item?.canDownload === true || isTrash;
    const isIntellect = item => isIntellectFile(getItemExt(item));

    const { singleDownload } = useDriveDownload({ onClose, t });

    const getResourceDetail = async previewItem => {
        try {
            const res = await api.Get(`resources/${previewItem?.resourceSeq}`, {
                params: !isTrash
                    ? {
                          ss: previewItem?.sharedByUserSeq || previewItem?.userSeq || undefined,
                      }
                    : {
                          ss: previewItem?.sharedByUserSeq || previewItem?.userSeq || undefined,
                          d: true,
                      },
            });

            if (res?.status === 200) {
                setItem(res.data.data);
                return res.data.data;
            }
            return null;
        } catch (e) {
            console.error('Failed to fetch resource detail', e);
        }
    };

    const handlePreview = async () => {
        setLoading(true);
        try {
            await loadPreview();
        } finally {
            setLoading(false);
        }
    };

    const loadPreview = async () => {
        let objectUrl = null;
        // 오피스 off 면 오피스 문서도 일반 파일처럼(미리보기 불가·다운로드) 표시한다
        const isOffice = officeFeature.canPreview(getItemExt(item));
        const isIntellect = isIntellectFile(getItemExt(item));
        const fileTypeName = getFileByFileType(getItemExt(item), item?.resourceType)?.name;
        if (fileTypeName !== 'image' && fileTypeName !== 'txt' && !isOffice && !isIntellect) {
            const registered = await registerRecent(
                item.resourceSeq,
                item.sharedByUserSeq || item.userSeq
            );
            if (!registered) {
                showSnackbar(t('preview.fail'), 'error');
            }
            return;
        }

        if ((isIntellect && intellectEnabled) || isOffice) {
            const previewUrl = isIntellect
                ? await getIntellectUrl(item, user, 'ro')
                : await getOfficeViewUrl(item, user);
            setFrameLoaded(false);
            setOfficeUrl(previewUrl);

            const registered = await registerRecent(
                item.resourceSeq,
                item.sharedByUserSeq || item.userSeq
            );
            if (!registered) {
                showSnackbar(t('preview.fail'), 'error');
            }
            return;
        }

        try {
            const res = await api.Get(`files/${item.resourceSeq}/view`, {
                params: { ss: item?.sharedByUserSeq || item?.userSeq || undefined },
                responseType: 'blob',
            });

            if (res.status === 200) {
                if (fileTypeName === 'txt') {
                    const text = await res.data.text();
                    setTxt(text);
                } else if (fileTypeName === 'image') {
                    objectUrl = URL.createObjectURL(res.data);
                    setImageUrl(objectUrl);
                    // console.log('objectUrl', objectUrl);

                    const img = new window.Image();
                    img.onload = () => {
                        setImageSize({ width: img.naturalWidth, height: img.naturalHeight });
                    };
                    img.onerror = () => {
                        console.error('이미지 크기를 읽는 데 실패했습니다.');
                    };
                    img.src = objectUrl;
                } else {
                }
            }
        } catch (error) {
            console.error('Download failed:', error);
        }

        const registered = await registerRecent(
            item.resourceSeq,
            item.sharedByUserSeq || item.userSeq
        );
        if (registered) {
            showSnackbar(t('recent.register.success'), 'success');
        }
        return () => {
            if (objectUrl) {
                URL.revokeObjectURL(objectUrl);
            }
        };
    };

    const handleZoomIn = () => {
        setScale(prev => Math.min(prev + 1, MAX_SCALE));
    };

    const handleZoomOut = () => {
        setScale(prev => Math.max(prev - 1, MIN_SCALE));
    };

    const handleReset = () => {
        setScale(1);
    };

    const handleOpenDocument = async () => {
        const popup = openPendingPopup();
        // '편집' 가능한지 재조회
        const previewResource = await getResourceDetail(item);
        if (!previewResource) {
            closePendingPopup(popup);
            return;
        }

        if (!previewResource.canEdit) {
            let viewUrl = isIntellect(previewResource)
                ? await getIntellectUrl(previewResource, user, 'ro')
                : await getOfficeViewUrl(previewResource, user);
            if (!viewUrl) {
                closePendingPopup(popup);
                return;
            }
            openDocumentWindow(viewUrl, popup);
        } else {
            // tfi 파일이면서 locked인 경우 '편집' 불가능
            if (isIntellect(previewResource) && previewResource.isLocked) {
                closePendingPopup(popup);
                showSnackbar(t('lock.locked'), 'error');
                return;
            }

            let editUrl = isIntellect(previewResource)
                ? await getIntellectUrl(previewResource, user)
                : await getOfficeEditUrl(previewResource, user);
            if (!editUrl) {
                closePendingPopup(popup);
                showSnackbar(t('user.filePreview.toast.openDocumentFail'), 'error');
                return;
            }
            openDocumentWindow(editUrl, popup);
        }

        const registered = await registerRecent(
            previewResource.resourceSeq,
            previewResource.sharedByUserSeq || previewResource.userSeq
        );
        if (!registered) {
            showSnackbar(t('recent.register.fail'), 'error');
        }
        showSnackbar(t('user.filePreview.toast.openDocumentSuccess'), 'success');
        onClose();
    };

    useEffect(() => {
        // 오피스 on/off·TFO 주소 https 여부를 알기 전엔 로딩을 유지한다('미리보기 불가'가 잠깐 보이지 않도록)
        if (!item || !officeFeature.previewReady) return;
        // 설정 확인 전 클릭으로 들어온 http TFO 오피스 — iframe 대신 새 창으로 연다
        if (isOfficeViewerTarget(item)) {
            onClose?.();
            openOfficeViewer(item, { resource: item, preferEdit });
            return;
        }
        if (!officeFeature.canPreview(getItemExt(item))) setOfficeUrl('');
        handlePreview();
    }, [item, officeFeature]);

    useEffect(() => {
        if (!previewItem?.resourceSeq) return;

        // ✅ 이전 상태 초기화
        setItem(null);
        setImageUrl('');
        setTxt('');
        setOfficeUrl('');
        setScale(1);
        setLoading(true);
        setFrameLoaded(false);

        // 상세 조회 실패 시 로딩을 끝내 '미리보기 불가'를 보인다
        getResourceDetail(previewItem).then(detail => {
            if (!detail) setLoading(false);
        });
    }, [previewItem]);

    useEffect(() => {
        const handleEsc = e => {
            if (e.key === 'Escape') {
                onClose?.();
            }
        };

        window.addEventListener('keydown', handleEsc);
        return () => window.removeEventListener('keydown', handleEsc);
    }, [onClose]);

    // 본문 렌더 순서(텍스트 → 이미지 → 문서)와 같게 판정
    const watermarkRight = txt ? TXT_WATERMARK_RIGHT : OFFICE_WATERMARK_RIGHT;
    const isOffice = !txt && !imageUrl && officeUrl;
    const mobileWatermarkRight = txt || isOffice ? watermarkRight : MOBILE_WATERMARK_RIGHT;

    return (
        <Box
            sx={{
                position: 'absolute',
                width: '100vw',
                height: '100vh',
                backgroundColor: '#202020f2',
                zIndex: 9000,
                '--preview-stage-w': STAGE_WIDTH,
                left: 0,
                top: 0,
            }}
        >
            <Stack
                direction="row"
                justifyContent="space-between"
                alignItems="center"
                sx={{
                    position: 'fixed',
                    top: 0,
                    left: 0,
                    width: '100%',
                    backgroundColor: '#1a1a1af2',
                    py: 2,
                    px: 6,
                }}
            >
                {/* 좌측: 아이콘 + 리소스명 */}
                <Stack
                    direction={'row'}
                    alignItems={'center'}
                    gap={2}
                    sx={{
                        minWidth: 0,
                        flex: 1, // 남은 영역 사용
                    }}
                >
                    <Image
                        src={getFileByFileType(getItemExt(item), item?.resourceType)?.src}
                        width={20}
                        height={20}
                        alt={'resource'}
                    />
                    <CustomTooltip
                        title={item?.resourceName}
                        disableHoverListener={item?.resourceName?.length <= 30}
                        PopperProps={{
                            sx: {
                                zIndex: 10000, // 헤더보다 위
                            },
                        }}
                    >
                        <Box
                            sx={{
                                minWidth: 0,
                                flex: 1,
                                maxWidth: '100%',
                                display: 'flex',
                            }}
                        >
                            <Typography
                                variant="body5"
                                noWrap
                                sx={{
                                    color: palette.white,
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis',
                                    whiteSpace: 'nowrap',
                                }}
                            >
                                {item?.resourceName}
                            </Typography>
                        </Box>
                    </CustomTooltip>
                </Stack>

                {/* 우측: 액션 버튼 (고정 영역) */}
                <Stack direction={'row'} gap={1} alignItems={'center'} flexShrink={0}>
                    <IconButton
                        onClick={() => singleDownload(item)}
                        title={t('common.action.download')}
                        disabled={!canDownload}
                    >
                        <Image
                            src={'/preview/icon-ic-download.svg'}
                            width={20}
                            height={20}
                            alt={'download'}
                        />
                    </IconButton>
                    {(officeUrl || (item && isIntellect(item) && !intellectEnabled)) && (
                        <IconButton
                            onClick={handleOpenDocument}
                            title={t('user.filePreview.tooltip.openDocument')}
                            disabled={
                                !canView ||
                                isTrash ||
                                (isIntellect(item) && hasLockedFile([item])) ||
                                (isIntellect(item) && !intellectEnabled)
                            }
                        >
                            <Image
                                src={'/preview/icon-ic-open-document.svg'}
                                width={20}
                                height={20}
                                alt={'open document'}
                            />
                        </IconButton>
                    )}
                    {!hideClose && (
                        <IconButton onClick={onClose} title={t('common.button.close')}>
                            <Image
                                src={'/preview/icon-ic-close.svg'}
                                width={20}
                                height={20}
                                alt={'close'}
                            />
                        </IconButton>
                    )}
                </Stack>
            </Stack>
            <Box
                sx={{
                    position: 'fixed',
                    top: 52, // 헤더 높이
                    bottom: 0,
                    left: 0,
                    right: 0,

                    display: 'flex',
                    justifyContent: 'center',
                    alignItems: 'center',

                    pointerEvents: 'none',
                }}
            >
                <Stack
                    sx={{
                        width: 'var(--preview-stage-w)',
                        height: '100%', // ✅ 부모 기준
                        display: 'flex',
                        justifyContent: 'center',
                        alignItems: 'center',

                        pointerEvents: 'auto',
                    }}
                >
                    {txt ? (
                        <Box
                            sx={{
                                width: '95%',
                                height: '100%',
                                py: '52px', // ✅ 상·하 여백 동일

                                display: 'flex',
                                justifyContent: 'center',
                                alignItems: 'center',
                            }}
                        >
                            <Box
                                sx={{
                                    width: '100%',
                                    maxWidth: '1320px',
                                    maxHeight: '100%',
                                    backgroundColor: '#fff',
                                    borderRadius: 1,
                                    boxShadow: 1,
                                    p: 4,
                                    overflowY: 'auto',
                                }}
                            >
                                <Typography
                                    sx={{
                                        color: '#000',
                                        whiteSpace: 'pre-wrap',
                                        wordBreak: 'break-word',
                                    }}
                                >
                                    {txt}
                                </Typography>
                            </Box>
                        </Box>
                    ) : imageUrl ? (
                        <Box
                            sx={{
                                height: '100%',
                                display: 'flex',
                                flexDirection: 'column',
                            }}
                        >
                            {/* 이미지 영역 */}
                            <Box
                                sx={{
                                    flex: 1, // ✅ 40px 제외한 영역
                                    py: '52px',

                                    display: 'flex',
                                    justifyContent: 'center',
                                    alignItems: 'center',
                                    overflow: 'hidden',
                                }}
                            >
                                <Image
                                    src={imageUrl}
                                    alt="preview"
                                    width={imageSize.width}
                                    height={imageSize.height}
                                    style={{
                                        maxWidth: '100%',
                                        maxHeight: '100%',
                                        objectFit: 'contain',
                                        transform: `scale(${scale})`,
                                        transition: 'transform 0.3s ease',
                                    }}
                                />
                            </Box>
                            {/* 하단 이미지 전용 버튼 */}
                            <Box sx={{ height: '40px' }}>{/* 확대 / 축소 버튼 */}</Box>
                        </Box>
                    ) : officeUrl ? (
                        <Box
                            sx={{
                                width: '95%',
                                height: '100%',
                                py: '52px', // ✅ 상·하 여백 동일

                                display: 'flex',
                                justifyContent: 'center',
                                alignItems: 'center',
                                position: 'relative',
                            }}
                        >
                            <Box
                                component="iframe"
                                src={officeUrl}
                                title="Document Preview"
                                onLoad={() => setFrameLoaded(true)}
                                sx={{
                                    width: '100%',
                                    height: '100%',
                                    border: 'none',
                                    borderRadius: 1,
                                    boxShadow: 1,
                                    // 문서가 뜨기 전 빈 프레임 대신 로딩만 보이게 숨긴다
                                    visibility: frameLoaded ? 'visible' : 'hidden',
                                }}
                            />
                            {!frameLoaded && (
                                <CircularProgress
                                    size={40}
                                    sx={{
                                        position: 'absolute',
                                        top: '50%',
                                        left: '50%',
                                        mt: '-20px',
                                        ml: '-20px',
                                        color: palette.white,
                                    }}
                                />
                            )}
                        </Box>
                    ) : loading ? (
                        <CircularProgress size={40} sx={{ color: palette.white }} />
                    ) : (
                        <Typography variant={'body2'} sx={{ color: palette.white, opacity: 0.5 }}>
                            {t('filePreviewDialog.noPreview')}
                        </Typography>
                    )}

                    {/** 미리보기 불가한 파일인 경우 다운로드 버튼 표시*/}
                    {!loading && !txt && !imageUrl && !officeUrl && canDownload && (
                        <StyledButton
                            variant={'contained'}
                            color={'btn_point_bg'}
                            sx={{
                                color: palette.white,
                                minWidth: { xs: '100%', tablet: 'auto' },
                            }}
                            startIcon={
                                <Image
                                    src={'/preview/icon-ic-download.svg'}
                                    width={20}
                                    height={20}
                                    alt={'download'}
                                />
                            }
                            onClick={() => singleDownload(item)}
                            disabled={!canDownload}
                        >
                            <Typography variant={'body5'} sx={{ color: palette.white }}>
                                {t('common.action.download')}
                            </Typography>
                        </StyledButton>
                    )}
                </Stack>
            </Box>

            {/** ?��?지 ?��?/축소 버튼 */}
            {imageUrl && (
                <Stack
                    direction="row"
                    position="fixed"
                    sx={{
                        right: '50%',
                        bottom: {
                            xs: `calc(32px + env(safe-area-inset-bottom))`,
                            sm: 20,
                        },
                        transform: 'translateX(50%)',
                        gap: 2,
                        zIndex: 10001,
                        background: 'white',
                        borderRadius: 20,
                    }}
                >
                    <IconButton onClick={handleZoomOut} disabled={scale <= MIN_SCALE}>
                        <Image
                            src={'/common/icon-ic-zoom-out.svg'}
                            alt="zoom-out"
                            width={24}
                            height={24}
                        />
                    </IconButton>

                    <IconButton onClick={handleReset} disabled={scale === 1}>
                        <Image
                            src={'/common/icon-ic-search_black.svg'}
                            alt={'search'}
                            width={24}
                            height={24}
                        />
                    </IconButton>

                    <IconButton onClick={handleZoomIn} disabled={scale >= MAX_SCALE}>
                        <Image
                            src={'/common/icon-ic-plus.svg'}
                            alt="zoom-in"
                            width={24}
                            height={24}
                        />
                    </IconButton>
                </Stack>
            )}

            {/** 미리보기는 자체 z-index(9000~10001)가 전역 워터마크보다 높아 별도 배치 */}
            <LicenseWatermark
                offset={{ right: watermarkRight, bottom: 20 }}
                mobileOffset={{ right: mobileWatermarkRight, bottom: 20 }}
                zIndex={10002}
            />
        </Box>
    );
}

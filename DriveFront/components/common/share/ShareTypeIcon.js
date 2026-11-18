import { Box } from '@mui/material';
import Image from 'next/image';
import palette from '@/theme/palette';

const SHARE_TYPE_CONFIG = {
    restricted: {
        icon: '/common/share/icon-ic-share-restricted.svg',
        bg: palette.share_restricted_bg,
        iconSize: 20,
        bottomSheetIconSize: 15,
        padding: '6px',
    },
    internal: {
        icon: '/common/share/icon-ic-share-internal.svg',
        bg: palette.share_internal_bg,
        iconSize: 20,
        bottomSheetIconSize: 15,
        padding: '6.22px',
    },
    public: {
        icon: '/common/share/icon-ic-share-public.svg',
        bg: palette.share_public_bg,
        iconSize: 20,
        bottomSheetIconSize: 15,
        padding: '7px',
    },
};

/**
 * 공유 타입 아이콘 컴포넌트
 * @param {'restricted' | 'internal' | 'public'} type - 공유 타입
 * @param {number} [boxSize=32] - 외부 박스 크기 (px)
 */
export default function ShareTypeIcon({ type, boxSize = 32, isBottomSheet = false }) {
    const config = SHARE_TYPE_CONFIG[type];
    if (!config) return null;
    const iconSize = isBottomSheet ? config.bottomSheetIconSize : config.iconSize;

    return (
        <Box
            sx={{
                width: boxSize,
                height: boxSize,
                bgcolor: config.bg,
                borderRadius: '6px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                p: config.padding,
                overflow: 'hidden',
                flexShrink: 0,
            }}
        >
            <Image src={config.icon} width={iconSize} height={iconSize} alt={type} />
        </Box>
    );
}

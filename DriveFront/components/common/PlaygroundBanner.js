import { forwardRef } from 'react';
import { Box, Typography } from '@mui/material';
import Image from 'next/image';
import { useTranslations } from 'next-intl';
import palette from '@/theme/palette';

/** developers 플레이그라운드 접속 표시 상단 배너 */
const PlaygroundBanner = forwardRef(function PlaygroundBanner(props, ref) {
    const t = useTranslations();

    return (
        <Box
            ref={ref}
            sx={{
                width: '100%',
                // 슈퍼어드민 배너(바로가기 버튼 24px + 상하 3px)와 같은 높이
                minHeight: '30px',
                boxSizing: 'border-box',
                py: '3px',
                backgroundColor: 'rgba(69, 102, 234, 0.15)',
                display: 'flex',
                justifyContent: 'center',
                alignItems: 'center',
                gap: '4px',
            }}
        >
            <Image src="/admin/banner/icon-ic-superadmin-badge.svg" alt="" width={16} height={16} />
            <Typography variant="body9_10" color={palette.text_100} sx={{ opacity: 0.9 }}>
                {t('common.playgroundBanner.desc')}
            </Typography>
        </Box>
    );
});

export default PlaygroundBanner;

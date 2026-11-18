import Box from '@mui/material/Box';
import { CircularProgress, Typography } from '@mui/material';
import Image from 'next/image';
import { useTranslations } from 'next-intl';

/**
 * developers iframe 진입 화면 표시 — 인증 중이면 로딩, 실패면 사유.
 * @param failure 실패 시 `{ titleKey, descKey }`, 인증 중이면 null
 */
export default function ApiKeyEntryStatus({ failure }) {
    const t = useTranslations();

    return (
        <Box
            sx={{
                minHeight: '100vh',
                px: '16px',
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'center',
                alignItems: 'center',
                gap: '16px',
            }}
        >
            {failure ? (
                <>
                    <Image src="/error/img-img-error.png" alt="error" width={120} height={120} />
                    <Typography
                        sx={{
                            textAlign: 'center',
                            color: '#3A3D49',
                            fontSize: '20px',
                            fontWeight: 600,
                            lineHeight: '30px',
                            wordBreak: 'break-word',
                        }}
                    >
                        {t(failure.titleKey)}
                    </Typography>
                    <Typography
                        sx={{
                            textAlign: 'center',
                            color: 'rgba(58, 61, 73, 0.80)',
                            fontSize: '15px',
                            lineHeight: '22px',
                            wordBreak: 'break-word',
                            whiteSpace: 'pre-line',
                        }}
                    >
                        {t(failure.descKey)}
                    </Typography>
                </>
            ) : (
                <CircularProgress size={32} />
            )}
        </Box>
    );
}

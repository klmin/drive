import { Box, Typography } from '@mui/material';

/**
 * @param {{ bg: string, borderColor: string, color: string, children: React.ReactNode, sx?: object }} props
 */
export default function StyledBadge({ bg, borderColor, color, children, sx }) {
    return (
        <Box
            sx={{
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                px: '8px',
                py: '2px',
                bgcolor: bg,
                borderRadius: '100px',
                outline: `1px solid ${borderColor}`,
                outlineOffset: '-1px',
                ...sx,
            }}
        >
            <Typography sx={{ fontSize: 12, fontWeight: 500, lineHeight: '16px', color }}>
                {children}
            </Typography>
        </Box>
    );
}

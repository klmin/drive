import palette from '@/theme/palette';
import { Box, IconButton } from '@mui/material';
import Image from 'next/image';
import React from 'react';

function StyledTag({ label, variant, index, ...props }) {
    return (
        <Box
            sx={{
                position: 'relative',
                [`&:hover #delete-${index || 0}`]: {
                    opacity: '1 !important',
                },
            }}
        >
            <IconButton
                sx={{
                    opacity: 0,
                    position: 'absolute',
                    right: -8,
                    top: -4,
                    p: 0,
                    display: { xs: 'none', md: 'inline-flex' },
                }}
                id={`delete-${index || 0}`}
                onClick={() => {}}
            >
                <Image
                    src={'/common/icon-ic-delete-0.8.svg'}
                    alt={'delete'}
                    width={16}
                    height={16}
                />
            </IconButton>
            <Box
                sx={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 2,
                    fontSize: 12,
                    fontWeight: 'normal',
                    borderRadius: 999,
                    // width: 'min-content',
                    padding: '4px 8px',
                    height: { xs: 32, md: 24 },
                    border: `1px solid ${palette.btn_border} !important`,
                    color: palette.text_80,
                    cursor: 'pointer',
                    '& .MuiChip-label': {
                        padding: 0,
                    },
                }}
            >
                {label}
                <Box sx={{ display: { xs: 'inline-flex', md: 'none' } }}>
                    <Image
                        src={'/close/icon-ic-close.4.svg'}
                        width={14}
                        height={14}
                        alt={'close'}
                    />
                </Box>
            </Box>
        </Box>
    );
}

export default StyledTag;

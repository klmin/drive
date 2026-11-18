import Image from 'next/image';
import { Box } from '@mui/material';
import React from 'react';

export default function UploadBox({ isDragActive }) {
    if (!isDragActive) return;
    return (
        <Box
            sx={{
                width: '100%',
                position: 'absolute',
                backgroundColor: '#6b88ff14',
                borderRadius: 3,
                height: '100%',
                zIndex: 9999,
            }}
        >
            <svg
                width="100%"
                height="100%"
                style={{
                    position: 'absolute',
                    top: 0,
                    left: 0,
                    pointerEvents: 'none',
                    zIndex: 1,
                }}
            >
                <rect
                    x="1"
                    y="1"
                    width="calc(100% - 2px)"
                    height="calc(100% - 2px)"
                    rx="12"
                    ry="12"
                    fill="none"
                    stroke="#5294f7"
                    strokeWidth="2"
                    strokeDasharray="6,4"
                />
            </svg>
            <Image
                src={'/common/upload-multiple.svg'}
                alt={'upload'}
                width={44}
                height={44}
                style={{
                    position: 'absolute',
                    transform: 'translateX(-50%)',
                    left: '50%',
                    bottom: 58,
                }}
            />
        </Box>
    );
}

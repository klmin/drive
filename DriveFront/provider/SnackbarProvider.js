import React, { createContext, useContext, useState, useCallback } from 'react';
import { Snackbar, Alert, Box, Typography } from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import palette from '@/theme/palette';
import Image from 'next/image';

const SnackbarContext = createContext();

export const SnackbarProvider = ({ children }) => {
    const [open, setOpen] = useState(false);
    const [message, setMessage] = useState('');
    const [severity, setSeverity] = useState('info');
    const [duration, setDuration] = useState(1500);
    const [anchorOrigin, setAnchorOrigin] = useState({ vertical: 'top', horizontal: 'center' });

    const showSnackbar = useCallback((msg, sev = 'info', dur = 3000, anchor) => {
        setMessage(msg);
        setSeverity(sev);
        setDuration(dur);
        setAnchorOrigin(anchor ?? { vertical: 'top', horizontal: 'center' });
        setOpen(true);
    }, []);

    const handleClose = () => setOpen(false);

    const iconMap = {
        success: (
            <Image
                src={'/toast/icon-ic-message-confirm.svg'}
                width={20}
                height={20}
                alt={'confirm'}
            />
        ),
        error: (
            <Image
                src={'/toast/icon-ic-message-error.svg'}
                width={20}
                height={20}
                alt={'confirm'}
            />
        ),
    };
    // 경고(일부 실패) 전용 아이콘이 없어 error 아이콘을 함께 사용
    iconMap.warning = iconMap.error;

    return (
        <SnackbarContext.Provider value={{ showSnackbar }}>
            {children}
            <Snackbar
                open={open}
                autoHideDuration={duration}
                onClose={handleClose}
                anchorOrigin={anchorOrigin}
                sx={{
                    zIndex: 11000, // PreviewDialog(9000)보다 크게
                    '&.MuiSnackbar-root': {
                        opacity: 0.8,
                    },
                }}
            >
                <Box
                    sx={{
                        backgroundColor: palette.btn_point_bg,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        gap: 1.5,
                        borderRadius: '6px',
                        padding: '10px 24px',
                        maxWidth: 600,
                        boxShadow: 3,
                    }}
                >
                    <Box
                        sx={{
                            backgroundColor: severity === 'success' ? '#00C88C' : '#FF5C5C',
                            borderRadius: '50%',
                            width: 24,
                            height: 24,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            marginTop: '2px',
                            flexShrink: 0,
                        }}
                    >
                        {iconMap[severity]}
                    </Box>

                    <Typography
                        variant="body2"
                        sx={{
                            fontSize: '14px',
                            fontWeight: 400,
                            color: '#FFFFFF',
                            wordBreak: 'break-word',
                            whiteSpace: 'pre-line',
                        }}
                    >
                        {message}
                    </Typography>
                </Box>
            </Snackbar>
        </SnackbarContext.Provider>
    );
};

export const useSnackbarContext = () => {
    const context = useContext(SnackbarContext);
    if (!context) {
        throw new Error('useSnackbarContext must be used within a SnackbarProvider');
    }
    return context;
};

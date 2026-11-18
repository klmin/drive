'use client';

import { forwardRef } from 'react';
import { styled } from '@mui/material/styles';
import { Button } from '@mui/material';
import palette from '@/theme/palette';

const ButtonStyled = styled(Button)(props => {
    const { theme, color } = props;

    const _color = color?.split('.')?.[0] || color;
    return {
        fontWeight: 600,
        fontSize: '0.875rem',
        lineHeight: 1.5,
        padding: '8px 16px',
        borderRadius: 4,
        color: 'white',
        transition: 'all 150ms ease',
        cursor: 'pointer',
        width: 'min-content',
        textTransform: 'none',
        boxShadow: 'none',
        whiteSpace: 'nowrap',
        minWidth: 0,

        '& .MuiButton-startIcon': {
            marginRight: 4,
        },

        '& .MuiButton-endIcon': {
            marginLeft: 4,
        },

        '&.MuiButton-contained': {
            '&:hover': {
                backgroundColor: palette[_color],
                boxShadow: 'none',
            },
        },

        '&:hover': {
            boxShadow: 'none',
            backgroundColor: theme.palette.btn_white_bg_hover.main || 'inherit',
        },

        '&.active': {
            backgroundColor: theme.palette.btn_white_bg_selected.main,
            transform: 'scale(0.99)',
            boxShadow: 'none',
        },

        '&.focusVisible': {
            boxShadow: `0 0 0 4px ${theme.palette.status_error}`,
            outline: 'none',
        },

        '&.Mui-disabled': {
            color: theme.palette.common.white,
            // border: 0,
            cursor: 'default',
            boxShadow: 'none',
            transform: 'scale(1)',
            '& .MuiTypography-root': {
                color: theme.palette.action.disabled,
            },
            '& svg': {
                '& g, & path': {
                    stroke: theme.palette.action.disabled, // 라인 색 변경
                },
            },
            '& img': {
                opacity: 0.38,
                filter: 'grayscale(1)',
            },
        },
    };
});

const StyledButton = forwardRef((props, ref) => {
    const { size = 'small', variant, ...rest } = props;

    return <ButtonStyled size={size} ref={ref} {...rest} variant={variant || 'contained'} />;
});

StyledButton.displayName = 'StyledButton';

export default StyledButton;

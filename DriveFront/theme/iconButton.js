import palette from '@/theme/palette';

const iconButton = {
    styleOverrides: {
        root: {
            '&:hover': {
                backgroundColor: palette.btn_white_bg_hover,
            },
            '&:focus': {
                backgroundColor: palette.btn_white_bg_selected,
            },
            '&:disabled': {
                opacity: 0.32,
            },
        },
    },
};

export default iconButton;

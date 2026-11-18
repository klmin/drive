import palette from '@/theme/palette';
import typography from '@/theme/typography';
import breakpoints from '@/theme/breakpoints';

const menuItem = {
    styleOverrides: {
        root: {
            [breakpoints.up('md')]: {
                minHeight: 36,
            },
            [breakpoints.down('md')]: {
                minHeight: 40,
            },
            color: palette.text_80,
            ...typography.body5,
            '&:hover': {
                backgroundColor: palette.btn_white_bg_hover,
            },
            '&.Mui-disabled': {
                opacity: 0.6,
            },
            '&.Mui-selected': {
                background: `${palette.btn_white_bg_selected} !important`, //
                '&:hover': {
                    backgroundColor: palette.btn_white_bg_hover,
                },
            },
        },
    },
};

export default menuItem;

import palette from '@/theme/palette';
import typography from '@/theme/typography';
import breakpoints from '@/theme/breakpoints';

//list-button - 36px (AuthLayout New Drop Menu)
const menuListItemButton = {
    styleOverrides: {
        root: {
            [breakpoints.up('md')]: {
                minHeight: 32,
            },
            [breakpoints.down('md')]: {
                minHeight: 40,
            },
            padding: '0 24px',
            '&:hover': {
                backgroundColor: palette.btn_white_bg_hover,
            },
        },
    },
};

export default menuListItemButton;

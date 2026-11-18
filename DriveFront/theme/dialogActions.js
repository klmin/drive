import palette from '@/theme/palette';
import typography from '@/theme/typography';
import breakpoints from '@/theme/breakpoints';

const dialogActions = {
    styleOverrides: {
        root: {
            gap: '6px',
            padding: 0,
            marginTop: '24px',

            [breakpoints.up('tablet')]: {
                flexDirection: 'row',
            },
            [breakpoints.down('tablet')]: {
                flexDirection: 'column',
            },

            '& > :not(:first-of-type)': {
                marginLeft: '0px !important',
            },
        },
    },
};

export default dialogActions;

import palette from '@/theme/palette';

const tab = {
    styleOverrides: {
        root: {
            color: 'black',
            fontWeight: 500,

            '&.Mui-disabled': {
                color: '#3a3d4999',
            },

            '&.Mui-selected': {
                color: 'black',
            },

            '&:hover': {
                color: 'black',
            },

            '&:focus': {
                color: 'black',
            },
        },
    },
};

export const tabs = {
    styleOverrides: {
        indicator: {
            backgroundColor: 'black',
        },
    },
};

export default tab;

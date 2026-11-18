import palette from '@/theme/palette';

const typography = {
    fontFamily:
        '"Pretendard Variable", "Pretendard", -apple-system, BlinkMacSystemFont, system-ui, Roboto, "Helvetica Neue", sans-serif',
    color: '#3a3d49',

    h1: {
        fontSize: '24px',
        fontWeight: 600,
        lineHeight: '36px',
    },
    h1_h2: {
        fontSize: '24px',
        fontWeight: 600,
        lineHeight: '36px',
        '@media (max-width:1024px)': {
            fontSize: '20px',
            lineHeight: '30px',
        },
    },
    h2: {
        fontSize: '20px',
        fontWeight: 600,
        lineHeight: '30px',
    },
    h2_h5: {
        fontSize: '20px',
        fontWeight: 600,
        lineHeight: '30px',
        '@media (max-width:1024px)': {
            fontSize: '16px',
            lineHeight: '22px',
        },
    },
    h3: {
        fontSize: '19px',
        fontWeight: 500,
        lineHeight: '28px',
    },
    h4: {
        fontSize: '18px',
        fontWeight: 600,
        lineHeight: '26px',
    },
    h4_h5: {
        fontSize: '20px',
        fontWeight: 600,
        lineHeight: '30px',
        '@media (max-width:1024px)': {
            fontSize: '16px',
            lineHeight: '22px',
        },
    },
    h5: {
        fontSize: '16px',
        fontWeight: 600,
        lineHeight: '22px',
    },

    // Subtitles
    subtitle1: {
        fontSize: '15px',
        fontWeight: 500,
        lineHeight: '22px',
    },
    subtitle2: {
        fontSize: '13px',
        fontWeight: 700,
        lineHeight: '18px',
    },
    subtitle3: {
        fontSize: '13px',
        fontWeight: 600,
        lineHeight: '18px',
    },

    // Body
    body1: {
        fontSize: '16px',
        fontWeight: 500,
        lineHeight: '22px',
    },
    body2: {
        fontSize: '16px',
        fontWeight: 400,
        lineHeight: '22px',
    },
    body3: {
        fontSize: '15px',
        fontWeight: 400,
        lineHeight: '22px',
    },
    body4: {
        fontSize: '14px',
        fontWeight: 600,
        lineHeight: '20px',
        textTransform: 'none',
    },
    body4_5: {
        fontSize: '14px',
        fontWeight: 500,
        lineHeight: '20px',
    },
    body5: {
        fontSize: '14px',
        fontWeight: 400,
        lineHeight: '20px',
    },
    body5_1: {
        fontSize: '14px',
        fontWeight: 400,
        lineHeight: '22.4px', // 160%
    },
    body6: {
        fontSize: '13px',
        fontWeight: 500,
        lineHeight: '18px',
    },
    body6_tight: {
        fontSize: '13px',
        fontWeight: 500,
        lineHeight: '16px',
    },
    body7: {
        fontSize: '13px',
        fontWeight: 400,
        lineHeight: '18px',
    },
    body7_tight: {
        fontSize: '13px',
        fontWeight: 400,
        lineHeight: '16px',
    },
    body8: {
        fontSize: '12px',
        fontWeight: 600,
        lineHeight: '16px',
    },
    body8_1: {
        fontSize: '12px',
        fontWeight: 600,
        lineHeight: '14px',
    },
    body9: {
        fontSize: '12px',
        fontWeight: 400,
        lineHeight: '16px',
    },
    body9_10: {
        fontSize: '12px',
        fontWeight: 500,
        lineHeight: '16px',
    },
    body10: {
        fontSize: '12px',
        fontWeight: 400,
        lineHeight: '16px',
        '@media (max-width:360px)': {
            fontSize: '10px',
        },
    },
    caption: {
        fontSize: '11px',
        fontWeight: 400,
        lineHeight: '16px',
    },
    caption_tight: {
        fontSize: '11px',
        fontWeight: 400,
        lineHeight: '14px',
    },
    caption_medium: {
        fontSize: '11px',
        fontWeight: 500,
        lineHeight: '16px',
    },
    caption_compact: {
        fontSize: '11px',
        fontWeight: 500,
        lineHeight: '14px',
    },
    caption2: {
        fontSize: '12px',
        fontWeight: 400,
        lineHeight: '13px',
    },
};

export default typography;

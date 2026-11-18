import palette from '@/theme/palette';
import typography from '@/theme/typography';

const tooltip = {
    styleOverrides: {
        popper: {
            '&[data-popper-placement*="right"] .MuiTooltip-tooltip': {
                marginLeft: '4px !important',
            },
            '&[data-popper-placement*="left"] .MuiTooltip-tooltip': {
                marginRight: '4px !important',
            },
        },
        tooltip: {
            background: palette.btn_point_bg,
            opacity: '0.7 !important',
            borderRadius: '4px',
            maxWidth: '320px',
            padding: '4px 8px',
            ...typography.body9,
            fontWeight: 500,
            color: palette.white,
            wordWrap: 'break-word',
        },
    },
};

export default tooltip;

import palette from '@/theme/palette';
import typography from '@/theme/typography';

const ListItemText = {
    styleOverrides: {
        primary: {
            color: palette.text_80,
            ...typography.body5,
        },
    },
};

export default ListItemText;

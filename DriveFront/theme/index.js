// CustomThemeProvider.js
import { createTheme } from '@mui/material/styles';
import palette from '@/theme/palette';
import breakpoints from './breakpoints';
import typography from '@/theme/typography';
import button from '@/theme/button';
import menuItem from '@/theme/menuItem';
import auditLogMenuItem from '@/theme/auditLogMenuItem';
import tab, { tabs } from '@/theme/tab';
import iconButton from '@/theme/iconButton';
import MenuListItemButton from '@/theme/menuListItemButton';
import divider from '@/theme/divider';
import ListItemText from '@/theme/ListItemText';
import dialog from '@/theme/dialog';
import dialogActions from '@/theme/dialogActions';
import tooltip from '@/theme/tooltip';

const muiCompatiblePalette = Object.entries(palette).reduce((acc, [key, value]) => {
    acc[key] = {
        main: value,
    };
    return acc;
}, {});

const theme = createTheme({
    palette: {
        ...muiCompatiblePalette,
    },
    typography,
    breakpoints,
    components: {
        MuiDivider: {
            ...divider,
        },
        // menu
        MuiListItemButton: {
            ...MenuListItemButton,
        },
        // MuiMenuItemButton: {
        //     ...menuItemButton
        // },
        MuiListItemText: {
            ...ListItemText,
        },
        MuiMenuItem: {
            ...menuItem,
        },
        // button
        MuiButton: {
            ...button,
        },
        MuiIconButton: {
            ...iconButton,
        },
        //dialog
        MuiDialog: {
            ...dialog,
        },
        MuiDialogActions: {
            ...dialogActions,
        },
        MuiTab: {
            ...tab,
        },
        MuiTabs: {
            ...tabs,
        },
        MuiTooltip: {
            ...tooltip,
        },
        MuiFormLabel: {
            styleOverrides: {
                asterisk: {
                    color: palette.status_error,
                },
            },
        },
    },
    spacing: 4,
});

/**
 * 감사 로그 화면 전용 — 드롭다운 행 규격만 다르다. ThemeProvider에 그대로 넘긴다.
 *
 * 기본 theme이 아니라 바깥 테마를 받아 합친다. _app이 언어에 따라 달력 헤더 형식을
 * defaultProps로 얹고 있어서, 기본 theme에서 새로 만들면 그게 통째로 날아간다.
 */
export const withAuditLogTheme = outerTheme =>
    createTheme(outerTheme, {
        components: {
            MuiMenuItem: {
                ...auditLogMenuItem,
            },
        },
    });

export default theme;

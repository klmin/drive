import { createTheme } from '@mui/material/styles';

const breakpoints = {
    values: {
        xs: 0,
        sm: 320,
        tablet: 768,
        md: 1025,
        midLg: 1300, // 정보 패널이 문서함 위로 덮어씌우는 경계
        lg: 1468,
    },
};

const theme = createTheme({ breakpoints });

export default theme.breakpoints;

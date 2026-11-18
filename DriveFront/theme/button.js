import palette from '@/theme/palette';

const button = {
    styleOverrides: {
        // MUI 기본 uppercase 해제 — 버튼 문구는 스트링 리소스 값 그대로 보여준다
        root: {
            textTransform: 'none',
        },
        outlined: {
            borderColor: palette.btn_border,
        },
    },
};

export default button;

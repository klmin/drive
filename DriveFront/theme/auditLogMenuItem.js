import breakpoints from '@/theme/breakpoints';

/**
 * 감사 로그 화면 드롭다운 행 — 시안 높이 32 / 패딩 8·12 / 간격 8.
 *
 * 높이는 기본 menuItem.js가 36(md 이상)/40(md 미만)이라 이 화면에서만 덮는다.
 * 평문 minHeight만 주면 안 된다 — createTheme이 기본값과 깊게 합쳐 미디어쿼리 키가 남고,
 * stylis가 @media 블록을 평문 선언 뒤로 빼기 때문에 같은 키를 직접 눌러야 32가 산다.
 */
const auditLogMenuItem = {
    styleOverrides: {
        root: {
            minHeight: 32,
            height: 32,
            [breakpoints.up('md')]: { minHeight: 32 },
            [breakpoints.down('md')]: { minHeight: 32 },
            // MUI 기본은 6·16. 단축 속성이라 네 방향을 한 번에 덮는다
            padding: '8px 12px',
            gap: '8px',
        },
    },
};

export default auditLogMenuItem;

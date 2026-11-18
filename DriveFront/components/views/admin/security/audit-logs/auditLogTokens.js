import palette from '@/theme/palette';

/**
 * 감사 로그 화면 색상 — 색은 모두 palette를 참조하고, 그림자 등 조합 값만 여기서 정의한다.
 * 알파가 미세하게 다른 값(divider_alpha_8)도 palette에 별도 이름으로 있다.
 */
const auditLogTokens = {
    textSecondary: palette.control_text, // 본문 기본색
    textHint: palette.control_text_hint, // 아이콘/보조 텍스트
    textDisabled: palette.text_muted, // 헤더·캡션

    dividerSubtle: palette.border_subtle, // 카드 테두리
    dividerFaint: palette.divider_faint, // 행 구분선
    backgroundPaper: palette.table_header_bg, // 테이블 헤더 배경

    fieldBorder: palette.border_subtle, // 입력 필드 테두리
    buttonOutlinedBorder: palette.border_strong, // outlined 버튼 테두리
    buttonSecondaryBg: palette.btn_dark_bg, // 조회 버튼 배경
    buttonSecondaryBorder: palette.btn_dark_border, // 조회 버튼 안쪽 테두리
    buttonShadow: '0px 1.5px 0px rgba(0, 0, 0, 0.03)',

    footerBorder: palette.divider_alpha_8, // 페이지네이션 푸터 상하단 테두리

    checkboxChecked: palette.checkbox_checked, // 체크/부분선택 채움색
    calendarSelected: palette.checkbox_checked, // 달력 선택 날짜·월 채움색
    menuShadow: '0px 4px 16px rgba(0, 0, 0, 0.08), 0px 2px 6px -1px rgba(0, 0, 0, 0.12)',
    calendarShadow:
        '0px 5px 2px rgba(0, 0, 0, 0.01), 0px 1px 1px rgba(0, 0, 0, 0.07), 0px 0px 1px rgba(0, 0, 0, 0.08)',

    // 이벤트 배지 (카테고리별)
    badge: {
        waiting: {
            bg: palette.tone_blue_bg,
            border: palette.tone_blue_border,
            text: palette.tone_blue_text,
        }, // LOGIN
        success: {
            bg: palette.tone_green_bg,
            border: palette.tone_green_border,
            text: palette.tone_green_text,
        }, // FILE_FOLDER
        warning: {
            bg: palette.tone_amber_bg,
            border: palette.tone_amber_border,
            text: palette.tone_amber_text,
        }, // SHARE
        // 보라 톤이지만 글자색만 시안이 따로 지정돼 있다
        delay: {
            bg: palette.tone_purple_bg,
            border: palette.tone_purple_border,
            text: palette.tone_purple_text_deep,
        }, // USER_MANAGEMENT
        default: {
            bg: palette.tone_gray_bg,
            border: palette.border_subtle,
            text: palette.text_muted,
        }, // SETTING·미분류
    },
};

export default auditLogTokens;

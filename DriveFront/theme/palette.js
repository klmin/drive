// palette.js

// 여러 용도에서 같은 값을 쓰는 색 — 이름 하나로 모아 두고 아래에서 참조한다.
const border_subtle = '#E2E5EC'; // 입력 필드·카드 테두리
const border_strong = '#CBD1DC'; // hover 테두리, 토글 off
const text_muted = '#727B8C'; // 비활성 텍스트, 중립 배지 글자

// 배지·상태 표시 공통 톤 (bg/border/text 한 벌)
const tone_green = { bg: '#EDFAF4', border: '#C8F0E0', text: '#10A870' };
const tone_gray = { bg: '#EFF1F5', border: border_subtle, text: text_muted };
const tone_purple = { bg: '#F3F0FF', border: '#E2DAFF', text: '#8460F6' };
const tone_blue = { bg: '#EAF4FF', border: '#D4E8FF', text: '#1B84FF' };
const tone_amber = { bg: '#FEF6E6', border: '#FBE7BF', text: '#C97E07' };

const palette = {
    border_subtle,
    border_strong,
    text_muted,

    tone_green_bg: tone_green.bg,
    tone_green_border: tone_green.border,
    tone_green_text: tone_green.text,
    tone_gray_bg: tone_gray.bg,
    tone_purple_bg: tone_purple.bg,
    tone_purple_border: tone_purple.border,
    tone_purple_text: tone_purple.text,
    // 보라 톤을 쓰되 글자만 더 진한 시안이 있다
    tone_purple_text_deep: '#7047F5',
    tone_blue_bg: tone_blue.bg,
    tone_blue_border: tone_blue.border,
    tone_blue_text: tone_blue.text,
    tone_amber_bg: tone_amber.bg,
    tone_amber_border: tone_amber.border,
    tone_amber_text: tone_amber.text,

    white: '#ffffff',
    bg_gray: '#fbfbfb',
    bg_subtle: '#3a3d490a',
    divider_8: '#3a3d4914',
    divider_12: '#3a3d491e',

    icon_100: '#3a3d49',
    text_100: '#3a3d49',
    text_80: '#3a3d49cc',
    text_60: '#3a3d4999',
    text_40: '#3a3d4966',
    text_30: '#3a3d494d',
    text_tab_selected: '#4566ea',

    neutral_bg_10: 'rgba(58, 61, 73, 0.1)',

    status_warning: '#FD7940',
    status_warning_bg: 'rgba(253, 121, 64, 0.12)',
    status_error: '#f86c5e',
    status_error_bg: 'rgba(248, 108, 94, 0.15)',
    status_confirm: '#2ebb67',

    divider_faint: '#F2F4F7', // 표 행 구분선
    // divider_8(#3a3d4914)과 알파가 미세하게 달라 별개로 둔다
    divider_alpha_8: 'rgba(58, 61, 73, 0.08)',
    table_header_bg: '#F5F7FA',
    checkbox_checked: '#4364E8',

    // 어두운 배경 버튼 (btn_point_bg보다 밝은 시안)
    btn_dark_bg: '#272C36',
    btn_dark_border: 'rgba(255, 255, 255, 0.10)',

    // 관리자 사이드바 (다크)
    sidebar_bg: '#13161E',
    sidebar_text_primary: '#F2F4F7',
    sidebar_text_secondary: '#CBD1DC',
    sidebar_text_disabled: '#5C667A',
    sidebar_primary: '#4364E8',
    sidebar_divider: 'rgba(255, 255, 255, 0.08)',
    sidebar_hover: 'rgba(255, 255, 255, 0.04)',
    sidebar_text_hint: text_muted,
    sidebar_card_bg: '#22262F',
    sidebar_neutral_bg: 'rgba(92, 102, 122, 0.12)',
    sidebar_warning: '#F5A623',
    sidebar_warning_bg: 'rgba(245, 166, 35, 0.12)',
    sidebar_error: '#F55560',
    sidebar_error_bg: 'rgba(245, 85, 96, 0.12)',
    sidebar_error_bg_strong: 'rgba(245, 85, 96, 0.15)',
    sidebar_btn_border: '#32373F',
    sidebar_btn_bg_hover: '#171B23',

    // 관리자 드롭다운 메뉴 (프로필 메뉴 시안 기준)
    menu_item_selected_bg: '#F5F7FA',
    menu_item_selected_text: '#4364E8',

    // 관리자 필터·입력 컨트롤 (감사 로그 시안 기준)
    // 나머지 색은 공통 border_subtle / border_strong / text_muted 를 그대로 쓴다
    control_text: '#32373F',
    control_text_hint: '#454E5B',

    search_border: '#3a3d491e',
    control_border_normal: '#3a3d491e',
    control_border_hover: '#3a3d4966',
    control_border_selected: '#3a3d49cc',

    btn_point_bg: '#151a2a',
    btn_border: '#3a3d491e',

    btn_white_bg_normal: '#ffffff',
    btn_white_bg_hover: '#3a3d490a',
    btn_white_bg_selected: '#3a3d4914',

    btn_gray_bg_normal: '#3a3d4905',
    btn_gray_bg_hover: '#3a3d4914',
    btn_gray_bg_selected: '#3a3d4928',

    table_bg_normal: '#3a3d4914',
    table_bg_hover: '#3a3d490a',
    table_bg_selected: '#f3f4fc',

    table_divider_nomal: '#3a3d490a',
    table_divider_hover: '#3a3d4914',
    table_divider_selected: '#dee5ff',

    warning_bg: '#fff4e5', // 관리자 > 상단 경고 배너 색상 임시로 설정

    toggle_off: border_strong,

    badge_external_bg: tone_purple.bg,
    badge_external_border: tone_purple.border,
    badge_external_text: tone_purple.text,

    badge_license_active_bg: tone_green.bg,
    badge_license_active_border: tone_green.border,
    badge_license_active_text: tone_green.text,

    badge_license_expired_bg: tone_gray.bg,
    badge_license_expired_border: tone_gray.border,
    badge_license_expired_text: tone_gray.text,

    badge_license_not_issued_bg: 'rgba(255, 255, 255, 1)',
    badge_license_not_issued_border: 'rgba(222, 225, 232, 1)',
    badge_license_not_issued_text: 'rgba(116, 124, 139, 1)',

    badge_license_expiring_bg: tone_purple.bg,
    badge_license_expiring_border: tone_purple.border,
    badge_license_expiring_text: tone_purple.text,

    badge_oidc_connected_bg: tone_green.bg,
    badge_oidc_connected_border: tone_green.border,
    badge_oidc_connected_text: tone_green.text,

    badge_oidc_disconnected_bg: tone_gray.bg,
    badge_oidc_disconnected_border: tone_gray.border,
    badge_oidc_disconnected_text: tone_gray.text,

    share_restricted_bg: '#EBECED',
    share_internal_bg: '#DCE3FF',
    share_public_bg: '#E1F5E1',
};

export default palette;

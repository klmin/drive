/** 감사 로그 필터 바 — 사용자·이벤트·채널·결과·기간. */
import React, { useCallback, useLayoutEffect, useRef, useState } from 'react';
import {
    Box,
    Button,
    Checkbox,
    IconButton,
    ListSubheader,
    MenuItem,
    Stack,
    SvgIcon,
    Typography,
} from '@mui/material';
import Image from 'next/image';
import dayjs from 'dayjs';
import { useLocale, useTranslations } from 'next-intl';
import { DesktopDatePicker } from '@mui/x-date-pickers';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { PickersCalendarHeader } from '@mui/x-date-pickers/PickersCalendarHeader';
import { calendarYearFormatOf, pickerLocaleTextOf } from '@/constants/dateFormats';
import palette from '@/theme/palette';
import typography from '@/theme/typography';
import AdminFilterSelect, { filterControlSx } from '@/components/common/input/AdminFilterSelect';
import auditLogTokens from './auditLogTokens';
import AuditLogEllipsisText from './AuditLogEllipsisText';
import AuditLogUserSearchField from './AuditLogUserSearchField';
import {
    categoryLabel,
    CHANNELS,
    EVENT_CATEGORIES,
    EVENT_TYPE_GROUPS,
    eventTypeLabel,
    OUTCOMES,
    typesOfCategory,
} from './auditLogFormat';

// 필터 팝업 공통 — 이벤트 드롭다운·기간 달력
const POPUP_MAX_HEIGHT = 400;

// 이벤트 드롭다운 폭 — 입력창과 같게 맞춘다
const EVENT_FIELD_WIDTH = 230;

// 달력 팝업은 시안 고정 폭(테두리 포함).
// 안쪽 272px = 날짜 7칸(32px) + 칸 사이 8px×6. 좌우 패딩 8px을 더해 288이 된다
const CALENDAR_WIDTH = 288;

// 입력창과 달력 팝업 사이 간격 — 사용자 검색·채널·결과 드롭다운과 같다
const CALENDAR_OFFSET = 4;

// "오늘" 위 구분선 — 시안 고정 폭
const CALENDAR_DIVIDER_WIDTH = 260;

const menuRowSx = {
    minHeight: 32,
    height: 32,
    // 시안 패딩 — 상하 space/2(8), 좌우 space/3(12). MenuItem 기본 상하 6px을 덮는다.
    // 높이 32는 시안 size 토큰이라 유지하고, 20px짜리 내용은 중앙 정렬로 흘린다
    py: 2,
    px: 3,
    gap: 2,
    lineHeight: 1,
    cursor: 'pointer',
    '&:hover': { backgroundColor: auditLogTokens.backgroundPaper },
    // 선택 표시는 체크박스로 충분하다 — 회색 배경은 쓰지 않는다.
    // 공통 menuItem 테마가 !important로 배경을 박아 둬서 여기도 !important여야 이긴다
    '&.Mui-selected': {
        background: 'transparent !important',
        '&:hover': { background: `${auditLogTokens.backgroundPaper} !important` },
    },
    // 메뉴를 열면 첫 항목에 포커스가 가서 MUI 기본 focus 배경이 깔린다
    '&.Mui-focusVisible': {
        background: 'transparent !important',
        '&:hover': { background: `${auditLogTokens.backgroundPaper} !important` },
    },
    // 카테고리로 잠긴 하위 항목 — MUI 기본 0.38은 너무 흐리다
    '&.Mui-disabled': { opacity: 0.6 },
};

/**
 * 채널·결과 드롭다운 — 사용자 검색 후보 목록과 같은 모양.
 * 이벤트 드롭다운은 체크박스·카테고리가 있어 행 규칙(menuRowSx)이 따로다.
 */
const simpleMenuProps = {
    slotProps: {
        paper: {
            elevation: 0,
            sx: {
                // 시안 — 입력창과 4px 띄운다
                mt: 1,
                maxHeight: POPUP_MAX_HEIGHT,
                borderRadius: '6px',
                backgroundColor: palette.white,
                border: `1px solid ${auditLogTokens.dividerSubtle}`,
                boxShadow: auditLogTokens.menuShadow,
            },
        },
        // 시안 상하 space/1(4). MUI 기본 8px을 덮는다
        list: { sx: { py: 1 } },
    },
};

/** 채널·결과 드롭다운 행 — 사용자 검색 후보와 같은 높이·패딩·호버 */
const simpleMenuRowSx = {
    minHeight: 32,
    height: 32,
    // 시안 패딩 — 상하 space/2(8), 좌우 space/4(16)
    px: 4,
    py: 2,
    gap: 2,
    ...typography.body5,
    color: auditLogTokens.textSecondary,
    // 선택·키보드 포커스·마우스 호버가 모두 같은 배경이다.
    // 공통 menuItem 테마가 !important로 배경을 박아 둬서 여기도 !important여야 이긴다
    '&:hover, &.Mui-selected, &.Mui-focusVisible': {
        background: `${auditLogTokens.backgroundPaper} !important`,
    },
    '&.Mui-selected:hover': { background: `${auditLogTokens.backgroundPaper} !important` },
};

// 아이콘을 세 상태 모두 직접 그리므로 색·크기 규칙은 아이콘 쪽에 있다
const menuCheckboxSx = { p: 0, flexShrink: 0 };

// 체크박스 + 라벨 묶음 — 시안대로 상하 2px을 더해 행의 내용 영역(16px)보다 커진다
const menuLabelGroupSx = {
    display: 'flex',
    alignItems: 'center',
    gap: 2,
    py: '2px',
    minWidth: 0,
};

/** 카테고리 접기/펴기 삼각형 — 시안은 16 자리 안의 8×8이라 MUI 아이콘보다 좁고 뾰족하다. */
function CategoryArrowIcon(props) {
    return (
        <SvgIcon {...props} viewBox="0 0 8 8">
            <path d="M4 5.92847L7 2.07132H1L4 5.92847Z" fill="currentColor" />
        </SvgIcon>
    );
}

// 달력 연·월 이동 화살표 — 목록 푸터(icon-ic-arrow-back/forward.svg)와 같은 도형.
// MUI가 className·ownerState를 슬롯에 넘기므로 SvgIcon으로 받는다. next/image는
// 그 props를 못 받고 Next가 붙이는 스타일 탓에 버튼이 폭을 잃어 두 개가 겹친다
function CalendarPrevIcon(props) {
    return (
        <SvgIcon {...props} viewBox="0 0 20 20">
            <path
                d="M8.9019 9.99784L12.3967 13.4924C12.5154 13.6113 12.5762 13.7606 12.579 13.9405C12.5818 14.1203 12.521 14.2724 12.3967 14.3968C12.2723 14.521 12.1216 14.5832 11.9446 14.5832C11.7675 14.5832 11.6168 14.521 11.4924 14.3968L7.63636 10.5407C7.55614 10.4604 7.49951 10.3756 7.46648 10.2866C7.43345 10.1975 7.41693 10.1012 7.41693 9.99784C7.41693 9.89446 7.43345 9.79822 7.46648 9.70913C7.49951 9.62004 7.55614 9.53531 7.63636 9.45495L11.4924 5.59891C11.6112 5.48023 11.7606 5.41945 11.9405 5.41659C12.1202 5.41387 12.2723 5.47465 12.3967 5.59891C12.521 5.72332 12.5831 5.87404 12.5831 6.05108C12.5831 6.22811 12.521 6.37883 12.3967 6.50324L8.9019 9.99784Z"
                fill="currentColor"
            />
        </SvgIcon>
    );
}

function CalendarNextIcon(props) {
    return (
        <SvgIcon {...props} viewBox="0 0 20 20">
            <path
                d="M11.0981 9.99784L7.60333 13.4924C7.48464 13.6113 7.42387 13.7606 7.42101 13.9405C7.41829 14.1203 7.47906 14.2724 7.60333 14.3968C7.72774 14.521 7.87846 14.5832 8.05549 14.5832C8.23252 14.5832 8.38325 14.521 8.50765 14.3968L12.3637 10.5407C12.4439 10.4604 12.5005 10.3756 12.5336 10.2866C12.5666 10.1975 12.5831 10.1012 12.5831 9.99784C12.5831 9.89446 12.5666 9.79822 12.5336 9.70913C12.5005 9.62004 12.4439 9.53531 12.3637 9.45495L8.50765 5.59891C8.38882 5.48023 8.23946 5.41945 8.05957 5.41659C7.87982 5.41387 7.72774 5.47465 7.60333 5.59891C7.47906 5.72332 7.41693 5.87404 7.41693 6.05108C7.41693 6.22811 7.47906 6.37883 7.60333 6.50324L11.0981 9.99784Z"
                fill="currentColor"
            />
        </SvgIcon>
    );
}

// 체크박스 3종 — 시안 16×16 / radius 4. MUI 기본은 모서리가 각지고 표시 모양도 다르다
const checkboxSvgProps = {
    width: 16,
    height: 16,
    viewBox: '0 0 16 16',
    style: { display: 'block' },
};

const menuCheckboxIcon = (
    <svg {...checkboxSvgProps}>
        {/* 테두리가 안쪽에 그려지도록 0.5 밀고 반지름도 0.5 줄인다 */}
        <rect
            x="0.5"
            y="0.5"
            width="15"
            height="15"
            rx="3.5"
            fill={palette.white}
            stroke={auditLogTokens.fieldBorder}
        />
    </svg>
);

const menuCheckboxCheckedIcon = (
    <svg {...checkboxSvgProps}>
        <rect width="16" height="16" rx="4" fill={auditLogTokens.checkboxChecked} />
        {/* 시안 체크 표시 — 10×7.33, 끝을 둥글린 선이라 굵기 절반만큼 안쪽에서 시작한다 */}
        <path
            d="M3.875 8.165L6.5 10.79L12.125 5.205"
            fill="none"
            stroke={palette.white}
            strokeWidth="1.75"
            strokeLinecap="round"
            strokeLinejoin="round"
        />
    </svg>
);

const menuCheckboxIndeterminateIcon = (
    <svg {...checkboxSvgProps}>
        <rect width="16" height="16" rx="4" fill={auditLogTokens.checkboxChecked} />
        <rect x="3.5" y="7.125" width="9" height="1.75" fill={palette.white} />
    </svg>
);

const outlinedButtonSx = {
    height: 32,
    minWidth: 32,
    p: 1,
    borderRadius: '6px',
    // 테두리를 안쪽에 — border면 높이·폭에 1px씩 더해진다
    outline: `1px solid ${auditLogTokens.buttonOutlinedBorder}`,
    outlineOffset: '-1px',
    backgroundColor: palette.white,
    boxShadow: auditLogTokens.buttonShadow,
    color: auditLogTokens.textSecondary,
    '&:hover': { backgroundColor: palette.btn_white_bg_hover },
};

// 달력 팝업 — MUI 기본 치수를 전부 덮어쓴다
const calendarPaperSx = {
    px: 2,
    pt: 4,
    pb: 4,
    width: CALENDAR_WIDTH,
    maxHeight: POPUP_MAX_HEIGHT,
    overflowY: 'auto',
    borderRadius: '8px',
    // 테두리를 안쪽에 — border면 안쪽 폭이 1px씩 깎여 월 3칸이 76px에서 어긋난다
    outline: `1px solid ${auditLogTokens.dividerSubtle}`,
    outlineOffset: '-1px',
    boxShadow: auditLogTokens.calendarShadow,

    // 팝업 폭에 내부를 맞춘다. 레이아웃이 max-content 그리드라 폭을 좁혀도 내용이 줄지 않고 잘린다
    '& .MuiPickersLayout-root': { width: '100%', minWidth: 0 },
    '& .MuiPickersLayout-contentWrapper': { minWidth: 0 },

    // DateCalendar는 320×336 고정에 margin auto까지 붙어 있다(auto 여백이면 폭을 안 늘린다)
    '& .MuiDateCalendar-root': {
        width: '100%',
        maxWidth: '100%',
        minWidth: 0,
        m: 0,
        height: 'auto',
        maxHeight: 'none',
    },

    '& .MuiPickersCalendarHeader-root': {
        overflow: 'visible',
        p: 0,
        m: 0,
        // 시안 — 헤더와 요일 행 사이 16px
        mb: 4,
        minHeight: 'auto',
    },
    '& .MuiPickersCalendarHeader-labelContainer': {
        px: 2,
        gap: 2,
        overflow: 'visible',
        whiteSpace: 'nowrap',
        ...typography.body4_5,
        color: auditLogTokens.textSecondary,
    },
    // MUI가 라벨에 margin-right 6을 붙여 둬서, 지우지 않으면 시안 간격(8)이 14가 된다
    '& .MuiPickersCalendarHeader-label': { mr: 0 },
    '& .MuiPickersCalendarHeader-switchViewButton': { p: 0 },
    // 시안은 8 자리에 들어간 삼각형 — 아이콘 viewBox가 8이라 글자 크기도 8이다
    '& .MuiPickersCalendarHeader-switchViewIcon': {
        fontSize: 8,
        color: auditLogTokens.textDisabled,
    },
    // 버튼 사이 간격 — MUI 기본은 spacing(3)이라 12가 된다
    '& .MuiPickersArrowSwitcher-spacer': { width: '8px' },
    // MUI가 이전/다음에 edge="end"/"start"를 걸어 좌우 -12px 음수 마진이 붙는다.
    // 기본 패딩(12)을 상쇄하려는 값인데 p:0이라 상쇄할 게 없어 두 버튼이 16px 겹친다
    '& .MuiPickersArrowSwitcher-button': {
        p: 0,
        m: 0,
        color: auditLogTokens.textHint,
    },
    // 아이콘 viewBox가 20이라 글자 크기도 20 — SvgIcon 기본은 24다
    '& .MuiPickersArrowSwitcher-leftArrowIcon, & .MuiPickersArrowSwitcher-rightArrowIcon': {
        fontSize: 20,
    },

    // 날짜 칸 사이 8px. 양끝은 붙여야 하므로 MUI 기본 가운데 정렬을 왼쪽 정렬로 바꾼다
    // 시안 — 요일 행과 첫 날짜 행 사이 12px. MUI 기본은 0이다
    '& .MuiDayCalendar-header': { gap: 2, justifyContent: 'flex-start', mb: 3 },
    '& .MuiDayCalendar-weekDayLabel': {
        width: 32,
        height: 24,
        m: 0,
        ...typography.body7_tight,
        color: auditLogTokens.textHint,
    },

    '& .MuiDayCalendar-slideTransition': { minHeight: 'auto' },
    '& .MuiDayCalendar-monthContainer': { position: 'static' },
    '& .MuiDayCalendar-weekContainer': { m: 0, mb: 1, gap: 2, justifyContent: 'flex-start' },
    '& .MuiDayCalendar-weekContainer:last-of-type': { mb: 0 },
    // 날짜 한 칸은 32×32 원. 요일 라벨·빈칸도 같은 폭이라 열이 어긋나지 않는다
    '& .MuiPickersDay-root, & .MuiPickersDay-hiddenDaySpacingFiller': {
        boxSizing: 'border-box',
        width: 32,
        minWidth: 32,
        maxWidth: 32,
        height: 32,
        m: 0,
        p: 0,
    },
    '& .MuiPickersDay-root': {
        ...typography.body5,
        borderRadius: '50%',
        color: auditLogTokens.textHint,
        '&:hover': {
            backgroundColor: auditLogTokens.backgroundPaper,
            color: auditLogTokens.textSecondary,
        },
        // 이전·다음 달 날짜는 시안대로 위아래 2px 패딩. 칸이 32 고정이라 원 크기는 그대로다
        '&.MuiPickersDay-dayOutsideMonth': {
            color: auditLogTokens.textDisabled,
            py: '2px',
        },
        // 역행 날짜(시작>종료)
        '&.Mui-disabled': {
            color: auditLogTokens.textDisabled,
            opacity: 0.4,
            '&:hover': { backgroundColor: 'transparent' },
        },
        '&.MuiPickersDay-today': {
            border: `1px solid ${auditLogTokens.textSecondary}`,
            color: auditLogTokens.textSecondary,
        },
        // 선택된 날짜는 호버·포커스로 모양이 변하지 않는다 — 배경은 물론 글자색도 그대로 둔다
        // (위 &:hover가 color만 따로 지정해 둬서 여기서 다시 눌러 주지 않으면 회색으로 돌아간다)
        '&.Mui-selected': {
            backgroundColor: auditLogTokens.calendarSelected,
            color: palette.white,
            fontWeight: 500,
            '&:hover, &:focus, &.Mui-focusVisible': {
                backgroundColor: auditLogTokens.calendarSelected,
                color: palette.white,
            },
        },
    },

    // 월 선택 화면 — 3열 그리드. MUI 기본은 320px 고정 폭에 72×36 알약 버튼을 flex로 흘린다
    '& .MuiMonthCalendar-root': {
        display: 'grid',
        gridTemplateColumns: 'repeat(3, 1fr)',
        gap: '16px',
        width: '100%',
        p: 0,
        m: 0,
    },
    '& .MuiMonthCalendar-button': {
        width: '100%',
        minWidth: 0,
        height: 28,
        px: 2,
        py: 1,
        borderRadius: '6px',
        ...typography.body6_tight,
        color: auditLogTokens.textSecondary,
        '&:hover, &:focus': { backgroundColor: auditLogTokens.backgroundPaper },
        '&.Mui-disabled': { color: auditLogTokens.textDisabled },
        '&.Mui-selected': {
            backgroundColor: auditLogTokens.calendarSelected,
            color: palette.white,
            '&:hover, &:focus': {
                backgroundColor: auditLogTokens.calendarSelected,
                color: palette.white,
            },
        },
    },

    // "오늘"은 일 화면에서만 — 월 화면 시안에는 없다
    '& .MuiPickersLayout-contentWrapper:has(.MuiMonthCalendar-root) + .MuiPickersLayout-actionBar':
        {
            display: 'none',
        },

    '& .MuiPickersLayout-actionBar': {
        // 시안 — 마지막 날짜 행과 구분선 사이 16px, 구분선과 버튼 사이 8px
        mt: 4,
        pt: 2,
        px: 0,
        position: 'relative',
        justifyContent: 'flex-end',
        // 구분선은 시안 고정 폭 260이라 내용 폭을 따라가지 않는다 — 가운데 맞춰 깐다
        '&::before': {
            content: '""',
            position: 'absolute',
            top: 0,
            left: '50%',
            transform: 'translateX(-50%)',
            width: CALENDAR_DIVIDER_WIDTH,
            height: '1px',
            backgroundColor: auditLogTokens.dividerSubtle,
        },
        '& .MuiButton-root': {
            height: 28,
            // 시안 패딩 — 상하 space/1(4), 좌우 space/2(8)
            p: '4px 8px',
            minWidth: 'auto',
            borderRadius: '6px',
            ...typography.body6_tight,
            color: auditLogTokens.textSecondary,
        },
    },
};

const yearArrowSx = {
    p: 0,
    width: 20,
    height: 20,
    color: auditLogTokens.textHint,
    '&.Mui-disabled': { color: auditLogTokens.textDisabled, opacity: 0.4 },
};

/**
 * 달력 헤더. 월 선택 화면만 시안대로 "연도 + 좌우 연 이동"으로 그리고, 나머지는 기본 헤더를 쓴다.
 * MUI 기본 헤더는 일 화면에서만 화살표를 보여주고 라벨도 항상 연·월이라 대체가 필요하다.
 */
function CalendarHeader(props) {
    const { view, currentMonth, onMonthChange, minDate, maxDate, disabled } = props;
    const locale = useLocale();

    if (view !== 'month') {
        return <PickersCalendarHeader {...props} />;
    }

    const previousYear = currentMonth.subtract(1, 'year');
    const nextYear = currentMonth.add(1, 'year');

    return (
        <Stack
            direction="row"
            alignItems="center"
            justifyContent="center"
            sx={{ width: '100%', mb: 4 }}
        >
            <IconButton
                disabled={disabled || (minDate && previousYear.endOf('year').isBefore(minDate))}
                onClick={() => onMonthChange(previousYear)}
                sx={yearArrowSx}
            >
                <CalendarPrevIcon sx={{ fontSize: 20 }} />
            </IconButton>
            <Typography variant="body4_5" sx={{ px: 2, color: auditLogTokens.textSecondary }}>
                {currentMonth.format(calendarYearFormatOf(locale))}
            </Typography>
            <IconButton
                disabled={disabled || (maxDate && nextYear.startOf('year').isAfter(maxDate))}
                onClick={() => onMonthChange(nextYear)}
                sx={yearArrowSx}
            >
                <CalendarNextIcon sx={{ fontSize: 20 }} />
            </IconButton>
        </Stack>
    );
}

/**
 * 기간 입력 한 칸. 네이티브 input[type=date]는 값이 세그먼트라 말줄임이 안 먹어
 * 공용 StyledDatePicker와 같은 방식(단일 텍스트 input)을 쓴다.
 */
function DateField({ value, onChange, minDate, maxDate }) {
    const [open, setOpen] = useState(false);

    return (
        <DesktopDatePicker
            value={value ? dayjs(value) : null}
            minDate={minDate}
            maxDate={maxDate}
            onChange={next => onChange(next && next.isValid() ? next.format('YYYY-MM-DD') : '')}
            open={open}
            onOpen={() => setOpen(true)}
            onClose={() => setOpen(false)}
            // 표시만 점 구분 — 저장값은 URL 쿼리·API 파라미터와 같은 YYYY-MM-DD
            format="YYYY.MM.DD"
            enableAccessibleFieldDOMStructure={false}
            // 헤더를 누르면 월 화면, 월을 고르면 다시 일 화면. 연 목록 화면은 쓰지 않고
            // 월 화면 헤더의 좌우 화살표로 연을 옮긴다
            views={['month', 'day']}
            openTo="day"
            slots={{
                calendarHeader: CalendarHeader,
                switchViewIcon: CategoryArrowIcon,
                leftArrowIcon: CalendarPrevIcon,
                rightArrowIcon: CalendarNextIcon,
            }}
            // 항상 6주 — 없으면 달마다 높이가 달라진다
            showDaysOutsideCurrentMonth
            fixedWeekNumber={6}
            // monthContainer가 static이라 슬라이드 중 두 달이 겹치지 않고 위아래로 쌓인다
            reduceAnimations
            slotProps={{
                textField: {
                    size: 'small',
                    placeholder: 'YYYY.MM.DD',
                    onClick: () => setOpen(true),
                    sx: {
                        ...filterControlSx,
                        width: 120,
                        cursor: 'pointer',
                        // 피커 기본 달력 버튼(우측) 숨김. 좌측 아이콘은 Box라 안 걸린다
                        '& .MuiInputAdornment-root': { display: 'none' },
                    },
                    InputProps: {
                        readOnly: true,
                        startAdornment: (
                            <Box sx={{ display: 'flex' }}>
                                <Image
                                    src="/common/icon-ic-calendar.svg"
                                    alt=""
                                    width={16}
                                    height={16}
                                />
                            </Box>
                        ),
                    },
                },
                actionBar: { actions: ['today'] },
                desktopPaper: { sx: calendarPaperSx },
                // 시안 — 입력창과 4px 띄운다. 위로 뒤집혀도 같은 간격이 적용된다
                popper: {
                    modifiers: [{ name: 'offset', options: { offset: [0, CALENDAR_OFFSET] } }],
                },
            }}
        />
    );
}

/**
 * 라벨 + 컨트롤 한 쌍. 컨트롤 폭이 고정이라 좁아지면 Field 단위로 줄바꿈되고,
 * 혼자 한 줄을 넘길 때만 부모 폭까지 줄어든다.
 */
function Field({ label, minWidth, children }) {
    return (
        <Stack
            direction="row"
            alignItems="center"
            gap={2}
            sx={{
                height: 32,
                minWidth: minWidth ? `min(${minWidth}px, 100%)` : 0,
                maxWidth: '100%',
            }}
        >
            <Typography
                variant="body6_tight"
                sx={{
                    color: auditLogTokens.textSecondary,
                    whiteSpace: 'nowrap',
                    flexShrink: 0,
                }}
            >
                {label}
            </Typography>
            <Box sx={{ minWidth: 0, maxWidth: '100%' }}>{children}</Box>
        </Stack>
    );
}

export default function AuditLogFilterBar({
    filters,
    onChange,
    onToggleEventCategory,
    onToggleAllEvents,
    onSearch,
    onReset,
    loading,
}) {
    const t = useTranslations();
    const locale = useLocale();

    const [collapsed, setCollapsed] = useState({});
    const toggleCollapsed = category =>
        setCollapsed(prev => ({ ...prev, [category]: !prev[category] }));

    const handleChange = key => e => onChange(key, e.target.value);

    // 전체 선택 체크 상태 — 카테고리를 모두 켠 경우만 체크, 일부만 고르면 중간 상태
    const allOn = filters.eventCategories.length === EVENT_CATEGORIES.length;
    const anySelected = filters.eventCategories.length > 0 || filters.eventTypes.length > 0;

    // 조회 버튼이 첫 필드와 같은 줄에 있을 때만 우측 끝으로 민다.
    // 줄바꿈돼 기간 필드와 함께 아래로 내려오면 기본 흐름(좌측 정렬 + gap)을 그대로 둔다.
    const filterBarRef = useRef(null);
    const searchBtnRef = useRef(null);
    const [searchBtnPinned, setSearchBtnPinned] = useState(true);

    const measureSearchBtnPin = useCallback(() => {
        const container = filterBarRef.current;
        const btn = searchBtnRef.current;
        const firstField = container?.firstElementChild;
        if (!container || !btn || !firstField) return;
        setSearchBtnPinned(
            Math.abs(firstField.getBoundingClientRect().top - btn.getBoundingClientRect().top) < 1
        );
    }, []);

    useLayoutEffect(() => {
        const container = filterBarRef.current;
        if (!container) return undefined;

        const raf = requestAnimationFrame(measureSearchBtnPin);
        const ro = new ResizeObserver(measureSearchBtnPin);
        ro.observe(container);

        return () => {
            cancelAnimationFrame(raf);
            ro.disconnect();
        };
    }, [measureSearchBtnPin]);

    return (
        <Box
            ref={filterBarRef}
            sx={{
                width: '100%',
                p: 5,
                borderRadius: '12px',
                // 테두리를 안쪽에 — border면 위아래 1px씩 높이에 더해진다
                boxShadow: `inset 0 0 0 1px ${auditLogTokens.dividerSubtle}`,
                backgroundColor: palette.white,
                display: 'flex',
                flexWrap: 'wrap',
                alignItems: 'center',
                columnGap: 4,
                rowGap: 2,
            }}
        >
            <Field label={t('admin.auditLog.filter.userSearch.label')}>
                <AuditLogUserSearchField
                    value={filters.actorEmail}
                    onChange={next => onChange('actorEmail', next)}
                    onSearch={onSearch}
                    width={158}
                />
            </Field>

            <Field label={t('admin.auditLog.filter.event.label')} minWidth={220}>
                <Box sx={{ width: EVENT_FIELD_WIDTH, maxWidth: '100%', minWidth: 0 }}>
                    <AdminFilterSelect
                        width="100%"
                        value={filters.eventTypes}
                        onChange={e => onChange('eventTypes', e.target.value)}
                        slotProps={{
                            select: {
                                multiple: true,
                                displayEmpty: true,
                                // 카테고리는 Select value가 아니라 별도 상태다
                                renderValue: () => {
                                    // 카테고리는 하위 이벤트를 모두 고른 것과 같으므로 타입 수로 센다
                                    const count =
                                        (filters.eventCategories ?? []).reduce(
                                            (sum, category) =>
                                                sum + typesOfCategory(category).length,
                                            0
                                        ) + (filters.eventTypes?.length ?? 0);
                                    return count === 0
                                        ? t('common.label.all')
                                        : t('admin.auditLog.filter.event.selected', { count });
                                },
                                // MUI가 넣는 minWidth(=입력창 폭)는 인라인 style이라 sx로 못 덮는다
                                MenuProps: {
                                    slotProps: {
                                        paper: {
                                            style: {
                                                width: EVENT_FIELD_WIDTH,
                                                maxHeight: POPUP_MAX_HEIGHT,
                                            },
                                            sx: {
                                                // 시안 — 입력창과 4px 띄운다
                                                mt: 1,
                                                borderRadius: '6px',
                                                border: `1px solid ${auditLogTokens.dividerSubtle}`,
                                                boxShadow: auditLogTokens.menuShadow,
                                            },
                                        },
                                        // 시안 — 목록 위 4px, 아래는 여백 없다
                                        list: { sx: { mt: 1, py: 0 } },
                                    },
                                },
                            },
                        }}
                    >
                        <ListSubheader
                            key="__all__"
                            disableGutters
                            disableSticky
                            sx={{ p: 0, backgroundColor: palette.white }}
                        >
                            <Box
                                onClick={e => {
                                    e.stopPropagation();
                                    onToggleAllEvents();
                                }}
                                sx={{ ...menuRowSx, display: 'flex', alignItems: 'center' }}
                            >
                                <Box sx={menuLabelGroupSx}>
                                    <Checkbox
                                        size="small"
                                        readOnly
                                        checked={allOn}
                                        indeterminate={!allOn && anySelected}
                                        icon={menuCheckboxIcon}
                                        checkedIcon={menuCheckboxCheckedIcon}
                                        indeterminateIcon={menuCheckboxIndeterminateIcon}
                                        sx={menuCheckboxSx}
                                    />
                                    <Typography
                                        variant="body5"
                                        sx={{ color: auditLogTokens.textSecondary }}
                                    >
                                        {t('admin.auditLog.filter.event.selectAll')}
                                    </Typography>
                                </Box>
                            </Box>
                        </ListSubheader>
                        {EVENT_TYPE_GROUPS.flatMap(group => {
                            const categoryOn = filters.eventCategories.includes(group.category);
                            const typeCount = group.types.filter(type =>
                                filters.eventTypes.includes(type)
                            ).length;
                            const open = !collapsed[group.category];

                            return [
                                // Select가 메뉴 자식의 onClick을 덮어쓰고 tabindex 없는 ListSubheader에서 조기 반환하므로
                                // 핸들러를 안쪽 Box에 둔다. 전파를 끊어야 value=undefined가 eventTypes에 안 섞인다.
                                <ListSubheader
                                    key={group.category}
                                    disableGutters
                                    sx={{ p: 0, backgroundColor: palette.white }}
                                >
                                    <Box
                                        onClick={e => {
                                            e.stopPropagation();
                                            onToggleEventCategory(group.category);
                                        }}
                                        sx={{
                                            ...menuRowSx,
                                            display: 'flex',
                                            alignItems: 'center',
                                            // 시안: 화살표와 체크박스 묶음 사이만 4px, 체크박스-라벨은 8px
                                            gap: 1,
                                        }}
                                    >
                                        <Box
                                            onClick={e => {
                                                e.stopPropagation();
                                                toggleCollapsed(group.category);
                                            }}
                                            sx={{
                                                display: 'flex',
                                                alignItems: 'center',
                                                justifyContent: 'center',
                                                flexShrink: 0,
                                                width: 16,
                                                height: 16,
                                            }}
                                        >
                                            <CategoryArrowIcon
                                                sx={{
                                                    fontSize: 8,
                                                    color: auditLogTokens.textDisabled,
                                                    transform: open ? 'none' : 'rotate(-90deg)',
                                                }}
                                            />
                                        </Box>
                                        <Box sx={menuLabelGroupSx}>
                                            <Checkbox
                                                size="small"
                                                readOnly
                                                checked={categoryOn}
                                                indeterminate={!categoryOn && typeCount > 0}
                                                icon={menuCheckboxIcon}
                                                checkedIcon={menuCheckboxCheckedIcon}
                                                indeterminateIcon={menuCheckboxIndeterminateIcon}
                                                sx={menuCheckboxSx}
                                            />
                                            <AuditLogEllipsisText
                                                text={categoryLabel(group.category, t)}
                                                variant="body5"
                                                color={auditLogTokens.textSecondary}
                                                sx={{ minWidth: 0 }}
                                            />
                                        </Box>
                                    </Box>
                                </ListSubheader>,
                                ...(open
                                    ? group.types.map(type => (
                                          <MenuItem
                                              key={type}
                                              value={type}
                                              disabled={categoryOn}
                                              sx={{ ...menuRowSx, pl: 12 }}
                                          >
                                              <Box sx={menuLabelGroupSx}>
                                                  <Checkbox
                                                      size="small"
                                                      readOnly
                                                      checked={
                                                          categoryOn ||
                                                          filters.eventTypes.includes(type)
                                                      }
                                                      icon={menuCheckboxIcon}
                                                      checkedIcon={menuCheckboxCheckedIcon}
                                                      sx={menuCheckboxSx}
                                                  />
                                                  <AuditLogEllipsisText
                                                      text={eventTypeLabel(type, t)}
                                                      variant="body5"
                                                      color={auditLogTokens.textSecondary}
                                                      sx={{ minWidth: 0 }}
                                                  />
                                              </Box>
                                          </MenuItem>
                                      ))
                                    : []),
                            ];
                        })}
                    </AdminFilterSelect>
                </Box>
            </Field>

            <Field label={t('admin.auditLog.filter.channel.label')}>
                <AdminFilterSelect
                    width={110}
                    value={filters.channel}
                    onChange={handleChange('channel')}
                    slotProps={{ select: { displayEmpty: true, MenuProps: simpleMenuProps } }}
                >
                    <MenuItem value="" sx={simpleMenuRowSx}>
                        {t('common.label.all')}
                    </MenuItem>
                    {CHANNELS.map(channel => (
                        <MenuItem key={channel} value={channel} sx={simpleMenuRowSx}>
                            {channel}
                        </MenuItem>
                    ))}
                </AdminFilterSelect>
            </Field>

            <Field label={t('admin.auditLog.filter.result.label')}>
                <AdminFilterSelect
                    width={110}
                    value={filters.outcomeStatus}
                    onChange={handleChange('outcomeStatus')}
                    slotProps={{ select: { displayEmpty: true, MenuProps: simpleMenuProps } }}
                >
                    <MenuItem value="" sx={simpleMenuRowSx}>
                        {t('common.label.all')}
                    </MenuItem>
                    {OUTCOMES.map(outcome => (
                        <MenuItem key={outcome} value={outcome} sx={simpleMenuRowSx}>
                            {outcome === 'SUCCESS'
                                ? t('admin.auditLog.result.success')
                                : t('admin.auditLog.result.failed')}
                        </MenuItem>
                    ))}
                </AdminFilterSelect>
            </Field>

            <Field label={t('admin.auditLog.filter.date.label')}>
                <Stack direction="row" alignItems="center" gap={1} sx={{ minWidth: 0 }}>
                    <LocalizationProvider
                        dateAdapter={AdapterDayjs}
                        adapterLocale={locale}
                        localeText={pickerLocaleTextOf(locale)}
                    >
                        {/* 시작일은 종료일을 넘지 못하고, 종료일은 시작일 이전으로 못 간다 */}
                        <DateField
                            value={filters.from}
                            onChange={next => onChange('from', next)}
                            maxDate={filters.to ? dayjs(filters.to) : undefined}
                        />
                        <Typography
                            variant="body6_tight"
                            sx={{ color: auditLogTokens.textSecondary }}
                        >
                            -
                        </Typography>
                        <DateField
                            value={filters.to}
                            onChange={next => onChange('to', next)}
                            minDate={filters.from ? dayjs(filters.from) : undefined}
                            maxDate={filters.from ? dayjs(filters.from).add(3, 'month') : undefined} // 최대 3개월 범위
                        />
                    </LocalizationProvider>
                    <Button
                        onClick={onReset}
                        disabled={loading}
                        title={t('admin.auditLog.filter.date.reset')}
                        sx={{ ...outlinedButtonSx, width: 32 }}
                    >
                        <Image src="/common/icon-ic-reset.svg" alt="" width={20} height={20} />
                    </Button>
                </Stack>
            </Field>

            <Button
                ref={searchBtnRef}
                onClick={onSearch}
                disabled={loading}
                disableRipple
                sx={{
                    height: 32,
                    py: '4px',
                    px: 3,
                    flexShrink: 0,
                    // 첫 필드와 같은 줄일 때만 우측 끝에 붙고, 줄바꿈되면 gap(16px)만 유지한다
                    ml: searchBtnPinned ? 'auto' : 0,
                    borderRadius: '6px',
                    backgroundColor: auditLogTokens.buttonSecondaryBg,
                    boxShadow: auditLogTokens.buttonShadow,
                    // 테두리를 안쪽에 — border면 높이·폭에 1px씩 더해진다
                    outline: `1px solid ${auditLogTokens.buttonSecondaryBorder}`,
                    outlineOffset: '-1px',
                    color: palette.white,
                    '&:hover': { backgroundColor: auditLogTokens.buttonSecondaryBg, opacity: 0.9 },
                    '&.Mui-disabled': {
                        backgroundColor: auditLogTokens.buttonSecondaryBg,
                        opacity: 0.4,
                        color: palette.white,
                    },
                }}
            >
                <Typography variant="body6_tight">
                    {t('admin.auditLog.filter.searchBtn')}
                </Typography>
            </Button>
        </Box>
    );
}

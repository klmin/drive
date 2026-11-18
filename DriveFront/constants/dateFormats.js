/**
 * 날짜 피커(MUI DatePicker) 다국어 설정 모음.
 *
 * 이 모듈을 import 하면 dayjs 로케일(ko·es)이 함께 등록된다(en은 내장).
 * 등록만으로는 활성화되지 않으므로 LocalizationProvider에 adapterLocale로 넘겨야 한다.
 */
import 'dayjs/locale/ko';
import 'dayjs/locale/es';
import { enUS, esES, koKR } from '@mui/x-date-pickers/locales';

/**
 * 달력 헤더의 연·월 순서.
 *
 * MUI는 `${formats.month} ${formats.year}`로 조립해 로케일과 무관하게 항상 "월 년"이 된다
 * (PickersCalendarHeader). 로케일을 바꿔도 토큰만 번역돼 한국어에서 "6월 2026"처럼 어색해지므로,
 * 관습에 맞는 형식을 직접 지정한다. _app에서 테마 defaultProps로 주입해 모든 피커에 적용된다.
 */
export const CALENDAR_HEADER_FORMAT = {
    ko: 'YYYY년 M월',
    es: 'MMMM [de] YYYY',
    en: 'MMMM YYYY',
};

/** 지원하지 않는 로케일은 영어 형식으로 */
export const calendarHeaderFormatOf = locale =>
    CALENDAR_HEADER_FORMAT[locale] ?? CALENDAR_HEADER_FORMAT.en;

/** 월 선택 화면 헤더의 연도 표기 */
export const CALENDAR_YEAR_FORMAT = {
    ko: 'YYYY년',
    es: 'YYYY',
    en: 'YYYY',
};

/** 지원하지 않는 로케일은 영어 형식으로 */
export const calendarYearFormatOf = locale =>
    CALENDAR_YEAR_FORMAT[locale] ?? CALENDAR_YEAR_FORMAT.en;

/** 달력 문구(요일 약자·"오늘"·이동 버튼 aria-label 등) 번역 */
const PICKER_LOCALE = { ko: koKR, es: esES, en: enUS };

export const pickerLocaleTextOf = locale =>
    (PICKER_LOCALE[locale] ?? enUS).components.MuiLocalizationProvider.defaultProps.localeText;

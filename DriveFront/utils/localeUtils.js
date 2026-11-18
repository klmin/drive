/**
 * 백엔드가 지원하는 로케일 코드로 매핑 (AnonymousAnimalNames.java와 동일한 언어셋).
 * short code('ko', 'en' 등) → 'ko-KR', 'en-US' 형태로 변환.
 *
 * 'zh'는 navigator.language를 참조해 zh-CN / zh-TW를 구분.
 */
const LANG_TO_LOCALE = {
    ko: 'ko-KR',
    en: 'en-US',
    de: 'de-DE',
    fr: 'fr-FR',
    es: 'es-ES',
    it: 'it-IT',
    ja: 'ja-JP',
    nl: 'nl-NL',
    pt: 'pt-BR',
    ru: 'ru-RU',
    th: 'th-TH',
    zh: 'zh-CN',
    cs: 'cs-CZ',
    pl: 'pl-PL',
    tr: 'tr-TR',
};

const fromNavigator = () =>
    typeof navigator !== 'undefined' && typeof navigator.language === 'string'
        ? navigator.language
        : 'en-US';

/**
 * 사용자 언어 설정을 백엔드가 인식할 수 있는 로케일 문자열(하이픈)로 변환.
 *
 * @param {string} userLanguage - short code('ko', 'en' 등), 'auto', full locale('ko-KR' / 'ko_KR'), 또는 undefined
 * @returns {string} 예: 'ko-KR'
 */
export const resolveLocale = userLanguage => {
    if (!userLanguage || userLanguage === 'auto') {
        return resolveLocale(fromNavigator());
    }

    // 이미 full locale인 경우 — 구분자만 하이픈으로 정규화
    if (userLanguage.includes('-') || userLanguage.includes('_')) {
        return userLanguage.replace('_', '-');
    }

    // short code: 'zh'는 CN/TW 구분을 위해 navigator.language 참조
    if (userLanguage === 'zh') {
        const nav = fromNavigator().toLowerCase();
        return nav.includes('tw') || nav.includes('hant') ? 'zh-TW' : 'zh-CN';
    }

    return LANG_TO_LOCALE[userLanguage] ?? resolveLocale(fromNavigator());
};

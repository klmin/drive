export const formatInputNumber = (value, toFormat) => {
    if (!value) return '';

    value = value.toString().replace(/[^0-9]/g, '');

    if (toFormat) {
        return value.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    } else {
        return Number(value.toString().replace(/,/g, ''));
    }
};

export function formatInputPhone(input) {
    if (!input) return '';
    const digits = input.replace(/\D/g, '');

    const part1 = digits.slice(0, 3);
    const part2 = digits.slice(3, 7);
    const part3 = digits.slice(7, 11);

    let formatted = part1;

    if (part2) {
        formatted += `-${part2}`;
    }

    if (part3) {
        formatted += `-${part3}`;
    }

    return formatted.slice(0, 13);
}

export function getDialCodeDigits(input) {
    return String(input ?? '').replace(/^\+/, '');
}

export const UNREAD_COUNT_MAX = 999;

/** 안 읽은 알림 개수 표기 (상한 초과 시 `999+`) */
export const formatUnreadCount = count =>
    count > UNREAD_COUNT_MAX ? `${UNREAD_COUNT_MAX}+` : `${count ?? 0}`;

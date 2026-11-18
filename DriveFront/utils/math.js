export function numberFormat(number) {
    if (!number && number !== 0) return;
    var sNumber = typeof number === 'string' ? number : number.toString();
    var [top, bottom] = sNumber.split('.');
    var newTop = top.replace(/(\d)(?=(\d{3})+(?!\d))/g, '$1,');
    return bottom ? `${newTop}.${bottom}` : newTop;
}

const UNIT_DIVISORS = {
    GB: 1024 ** 3,
    MB: 1024 ** 2,
    KB: 1024,
    B: 1,
};

// 관리자 > 사용자 수정 - 스토리지 용량 제한 시 unit(GB), decimals(3) 옵션 사용
export default function convertBytesToReadableSize(bytes, unit, decimals) {
    const formatFloat = value => {
        if (decimals !== undefined) return value.toFixed(decimals);
        return value % 1 === 0 ? value.toFixed(0) : value.toFixed(2);
    };

    if (unit && UNIT_DIVISORS[unit]) {
        return `${formatFloat(bytes / UNIT_DIVISORS[unit])} ${unit}`;
    }

    const autoUnit =
        Object.entries(UNIT_DIVISORS).find(([, divisor]) => bytes >= divisor)?.[0] ?? 'B';
    return `${formatFloat(bytes / UNIT_DIVISORS[autoUnit])} ${autoUnit}`;
}

function formatFloat(value) {
    return value % 1 === 0 ? value.toFixed(0) : value.toFixed(2);
}

export function convertGBtoBytes(gb) {
    if (!gb || isNaN(gb)) return 0;
    return Math.floor(parseFloat(gb) * 1024 ** 3);
}

/** 지정 단위(GB, MB, KB, B) 값을 바이트로 변환 */
export function convertSizeToBytes(value, unit) {
    if (!value || isNaN(value) || !UNIT_DIVISORS[unit]) return 0;
    return Math.floor(parseFloat(value) * UNIT_DIVISORS[unit]);
}

/** 서버가 값을 숨겼다는 표시 — 백엔드 MaskingUtils.MASKED_PLACEHOLDER 와 같은 값 */
export const MASKED_PLACEHOLDER = '__MASKED__';

/** 서버가 숨긴 값인지 */
export const isMaskedValue = value => value === MASKED_PLACEHOLDER;

/**
 * HTTP/HTTPS 모두 대응: 먼저 Clipboard API, 실패 시 execCommand 폴백
 * @param {string} text 복사할 텍스트
 * @param {string} [promptMessage] HTTP 에서 복사 실패 시 수동 복사 안내창 문구
 */
export async function copyTextToClipboard(text, promptMessage) {
    // 1) HTTPS & 지원 브라우저: Clipboard API
    if (typeof navigator !== 'undefined' && navigator.clipboard && window.isSecureContext) {
        try {
            await navigator.clipboard.writeText(text);
            return true;
        } catch (err) {
            console.warn('[copy] Clipboard API failed, fallback to execCommand', err);
        }
    }

    // 2) 폴백: execCommand('copy')
    return new Promise(resolve => {
        const textarea = document.createElement('textarea');
        textarea.value = text;
        // 화면에 보이지 않도록
        textarea.style.position = 'fixed';
        textarea.style.top = '-9999px';
        textarea.style.left = '-9999px';
        textarea.style.opacity = '0';
        textarea.style.pointerEvents = 'none';
        textarea.setAttribute('readonly', ''); // iOS Safari까지 안정적으로 지원을 위해 추가

        document.body.appendChild(textarea);

        textarea.focus({ preventScroll: true });
        textarea.select();
        textarea.setSelectionRange(0, text.length);

        let success = false;
        try {
            success = document.execCommand('copy');
        } catch (err) {
            console.error('[copy] execCommand error:', err);
        } finally {
            document.body.removeChild(textarea);
            if (!success) {
                console.warn('[copy] Clipboard write may have failed');

                // 클립보드 복사 실패 시 안내창 표시
                if (location.protocol === 'http:' && promptMessage) {
                    prompt(promptMessage, text);
                }
                resolve(false);
            } else {
                resolve(true);
            }
        }
    });
}

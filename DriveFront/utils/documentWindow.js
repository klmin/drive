import { isDevelopersEmbed } from '@/utils/apiKeySession';

/** developers iframe 안에서 문서(오피스·인텔릭트)를 여는 팝업 창 옵션 */
const POPUP_FEATURES = 'popup';

/**
 * developers iframe 안이면 클릭 순간 빈 팝업을 먼저 연다 — 주소를 API 응답 뒤에 정하면 팝업이 차단될 수 있음.
 * 그 외는 null.
 */
export function openPendingPopup() {
    return isDevelopersEmbed() ? window.open('', '_blank', POPUP_FEATURES) : null;
}

/** 주소를 만들지 못했을 때 미리 연 팝업을 닫는다 */
export function closePendingPopup(popup) {
    if (popup && !popup.closed) popup.close();
}

/** 문서 창 열기 — 미리 연 팝업이 있으면 그 팝업을 이동, developers iframe 안이면 팝업, 그 외는 새 탭 */
export function openDocumentWindow(url, popup) {
    if (popup && !popup.closed) {
        popup.location.replace(url);
        return;
    }
    if (isDevelopersEmbed()) {
        window.open(url, '_blank', POPUP_FEATURES);
        return;
    }
    window.open(url, '_blank');
}

import { useEffect, useState } from 'react';
import { useRouter } from '@/hooks/useRouter';
import { getResourceDetail } from '@/apis/resourceApi';
import { getOfficeEditUrl, getOfficeViewUrl } from '@/apis/webOfficeApi';
import { registerRecent } from '@/apis/recentApi';
import { getItemExt, isOfficeEditable } from '@/constants/FILES';
import useOfficeFeature from '@/hooks/useOfficeFeature';
import { useUser } from '@/context/UserContext';
import PreviewDialog from '@/components/common/dialog/PreviewDialog';
import { ROUTES } from '@/constants/routes';

/**
 * 파일 미리보기 딥링크(/preview?rs=&ss=) — MCP 툴의 webViewUrl이 이 주소를 준다.
 * 홈 미리보기 플로우와 동일하게 상세 조회 후 PreviewDialog를 띄우되(확장자 분기·
 * 오피스 iframe·다운로드는 다이얼로그 소유 로직 재사용), 홈으로 위임하지 않고
 * 레이아웃(사이드바) 없는 단독 페이지로 렌더한다 — _app.js가 이 라우트만 AuthLayout을 뺀다.
 * 인증 가드는 다른 보호 페이지와 동일(_app.js). rs 누락·조회 실패 시 홈으로, 닫기도 홈으로.
 *
 * mode=edit(MCP 툴의 editUrl) — 편집 버튼(handleOpenDocument)과 동일 패턴으로 진입 즉시
 * 웹오피스 편집기로 보낸다. 권한은 발급 시점이 아니라 이 진입 시점의 상세 조회로 재검증하고,
 * 편집 불가(형식·권한·URL 조립 실패)면 에러 대신 기존 미리보기로 폴백한다.
 * TFO 주소가 http 면 iframe 미리보기 대신 진입 즉시 이 창을 뷰어로 보낸다.
 *
 * user는 반드시 로그인 사용자(useUser)다 — 웹오피스 URL의 loginUserSeq/user_id가 이 값이라
 * 리소스 소유자를 넘기면 남의 계정으로 편집기가 열린다. 공유 파일의 접근 스코프(ss)는
 * getOfficeEditUrl이 item에서 sharedByUserSeq ?? userSeq로 따로 계산한다(docId).
 */
export default function PreviewEntry() {
    const router = useRouter();
    const user = useUser();
    const [previewItem, setPreviewItem] = useState(null);
    const officeFeature = useOfficeFeature();

    useEffect(() => {
        if (!router.isReady || !user || !officeFeature.previewReady) return;
        const { rs, ss, mode } = router.query;
        if (!rs) {
            router.replace(ROUTES.HOME);
            return;
        }
        (async () => {
            try {
                const data = await getResourceDetail(rs, { ss });
                if (data) {
                    const ext = getItemExt(data);
                    if (mode === 'edit' && data.canEdit === true && isOfficeEditable(ext)) {
                        const editUrl = await getOfficeEditUrl(data, user);
                        if (editUrl) {
                            registerRecent(data.resourceSeq, data.sharedByUserSeq || data.userSeq);
                            window.location.replace(editUrl);
                            return;
                        }
                    } else if (officeFeature.previewInNewTab(ext)) {
                        // http TFO 는 iframe 미리보기 대신 이 창을 뷰어로 보낸다(새 창은 팝업 차단)
                        const viewUrl = await getOfficeViewUrl(data, user);
                        if (viewUrl) {
                            registerRecent(data.resourceSeq, data.sharedByUserSeq || data.userSeq);
                            window.location.replace(viewUrl);
                            return;
                        }
                    }
                    setPreviewItem(data);
                    return;
                }
            } catch (err) {
                console.log(err);
            }
            router.replace(ROUTES.HOME); // 권한 없음·삭제됨 등 조회 실패 — 홈으로 폴백
        })();
    }, [router.isReady, router.query?.rs, user?.userSeq, officeFeature.previewReady]);

    if (!previewItem) return null;

    return (
        <PreviewDialog
            open={true}
            onClose={() => router.replace(ROUTES.HOME)}
            previewItem={previewItem}
            user={user}
        />
    );
}

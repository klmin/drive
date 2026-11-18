import { useEffect, useState } from 'react';
import { useRouter } from '@/hooks/useRouter';
import { getResourceDetail } from '@/apis/resourceApi';
import PreviewDialog from '@/components/common/dialog/PreviewDialog';
import { getItemExt, isOfficePreviewable, isIntellectFile } from '@/constants/FILES';
import { getOfficeEditUrl, getOfficeViewUrl } from '@/apis/webOfficeApi';
import { getIntellectUrl } from '@/apis/intellectApi';
import { useUser } from '@/context/UserContext';

export default function SharePreview() {
    const router = useRouter();
    const user = useUser();
    const [previewItem, setPreviewItem] = useState(null);

    useEffect(() => {
        if (!router.isReady) return;
        const { rs, ss } = router.query;
        if (!rs) return;

        (async () => {
            const data = await getResourceDetail(rs, { ss });
            if (!data) return;

            const ext = getItemExt(data);
            if (isOfficePreviewable(ext) || isIntellectFile(ext)) {
                let url;
                if (isIntellectFile(ext)) {
                    url = await getIntellectUrl(data, user, data.canEdit ? undefined : 'ro');
                } else {
                    url = data.canEdit
                        ? await getOfficeEditUrl(data, user)
                        : await getOfficeViewUrl(data, user); // 편집 권한이 없는 공유 문서는 Viewer로 엽니다.
                }
                console.log('preview url', url);
                if (url) {
                    window.open(url, '_self');
                    return;
                }
            }

            setPreviewItem(data);
        })();
    }, [router.isReady, router.query.rs]);

    if (!previewItem) return null;

    console.log('previewItem', previewItem);

    return (
        <PreviewDialog
            open={true}
            onClose={() => {}}
            hideClose={true}
            previewItem={previewItem}
            user={user}
        />
    );
}

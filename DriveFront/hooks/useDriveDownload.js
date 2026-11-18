import { useSnackbarContext } from '@/provider/SnackbarProvider';
import api, { getBaseURL } from '@/utils/api';

export function useDriveDownload({ onClose = () => {}, t }) {
    const { showSnackbar } = useSnackbarContext();

    const singleDownload = async item => {
        try {
            const res = await api.Get(
                item?.resourceType === 'FILE'
                    ? `files/${item.resourceSeq}/download-token`
                    : `folders/${item.resourceSeq}/download-token`,
                {
                    params: {
                        ss: item.sharedByUserSeq || item.userSeq || undefined,
                    },
                }
            );

            // API는 "/api/v1/files/download?dt=xxxxx" 이런 상대경로를 반환
            if (res.status === 200) {
                const signedUrl = getBaseURL() + res.data.data.linkUrl;
                window.location.href = signedUrl;
            } else {
                showSnackbar(t('download.single.fail'), 'error');
            }
        } catch (err) {
            console.log(err);
            showSnackbar(t('download.single.fail'), 'error');
        }
    };

    const multiDownload = async items => {
        try {
            const res = await api.Post('resources/download-token', {
                rl: items.map(i => ({
                    ss: i?.sharedByUserSeq || i?.userSeq || undefined,
                    rs: i?.resourceSeq,
                })),
            });

            if (res.code === 200) {
                // POST 요청은 code로 상태 확인, Status 없음
                const signedUrl = getBaseURL() + res.data.linkUrl;
                console.log('signedUrl', signedUrl);
                window.location.href = signedUrl;
            } else {
                showSnackbar(t('download.multiple.fail'), 'error');
            }
        } catch (err) {
            console.log(err);
            showSnackbar(t('download.multiple.fail'), 'error');
        }
    };

    const handleDownload = async target => {
        const items = Array.isArray(target) ? target : [target];

        if (items.length === 1) {
            await singleDownload(items[0]);
        } else {
            await multiDownload(items);
        }

        onClose();
    };

    return { handleDownload, singleDownload };
}

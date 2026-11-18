// resource 폴더 하위에 위치 (적절한 위치인지 검토 필요)

import { useRouter } from '@/hooks/useRouter';
import ActionIconButton from '../tooltip/ActionIconButton';
import { useTranslations } from 'next-intl';

export const ResourceActionButtons = ({
    item,
    disablePreview,
    disableDownload,
    disableShare,
    onPreview,
    onDownload,
    onShare,
    onRestore,
    onHardDelete,
    onToggleStar,
    shareEnabled,
}) => {
    const router = useRouter();
    const t = useTranslations();

    const getActionContextByPath = pathname => {
        switch (pathname) {
            case '/home':
            case '/drive':
            case '/recent':
            case '/search':
            case '/shared':
                return 'DEFAULT';

            case '/starred':
                return 'STARRED';

            case '/trash':
                return 'TRASH';

            default:
                return 'DEFAULT';
        }
    };
    const actionContext = getActionContextByPath(router.pathname);

    const actionPolicy = {
        DEFAULT: {
            showPreview: true,
            showDownload: true,
            showShare: true,
            showStar: true,
            showRestore: false,
            showHardDelete: false,
        },
        STARRED: {
            showPreview: true,
            showDownload: true,
            showShare: true,
            showStar: true,
            showRestore: false,
            showHardDelete: false,
        },
        TRASH: {
            showPreview: true,
            showDownload: true,
            showShare: false,
            showStar: false,
            showRestore: true,
            showHardDelete: true,
        },
    };

    const policy = actionPolicy[actionContext];

    return (
        <>
            {policy.showPreview && (
                <ActionIconButton
                    title={t('common.action.previewOpen')}
                    disabled={disablePreview(item)}
                    onClick={() => onPreview(item)}
                    icon="/common/icon-ic-preview.svg"
                />
            )}
            {policy.showDownload && (
                <ActionIconButton
                    title={t('common.action.download')}
                    disabled={disableDownload(item)}
                    onClick={() => onDownload(item)}
                    icon="/common/icon-ic-download.svg"
                />
            )}
            {shareEnabled && policy.showShare && (
                <ActionIconButton
                    title={t('common.action.share')}
                    disabled={disableShare(item)}
                    onClick={() => onShare(item)}
                    icon="/common/icon-ic-share-outline.svg"
                />
            )}
            {policy.showStar && (
                <ActionIconButton
                    title={
                        item.isStarred || actionContext === 'STARRED'
                            ? t('actionIconTooltip.removeToStarred')
                            : t('common.action.addStarred')
                    }
                    onClick={() => onToggleStar(item)}
                    icon={
                        item.isStarred || actionContext === 'STARRED'
                            ? '/common/icon-ic-star_active.svg'
                            : '/common/icon-ic-star-outline.svg'
                    }
                />
            )}
            {policy.showRestore && (
                <ActionIconButton
                    title={t('common.action.restore')}
                    onClick={() => onRestore(item)}
                    icon="/common/icon-ic-update.svg"
                />
            )}
            {policy.showHardDelete && (
                <ActionIconButton
                    title={t('common.action.hardDelete')}
                    onClick={() => onHardDelete(item)}
                    icon="/common/icon-ic-trash-outline.svg"
                />
            )}
        </>
    );
};

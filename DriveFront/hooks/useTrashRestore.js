import { useCallback } from 'react';
import { useTranslations } from 'next-intl';
import api from '@/utils/api';
import checkExists from '@/utils/checkExists';
import { useDialog } from '@/provider/DialogProvider';
import { useSnackbarContext } from '@/provider/SnackbarProvider';

/** 원래 부모 폴더가 살아 있는지(pe) 판정, 같은 부모는 한 번만 조회한다. */
const resolveParentExists = async list => {
    const cache = new Map();
    const result = new Map();
    for (const item of list) {
        const parentSeq = item?.parentFolderSeq;
        if (!parentSeq) {
            result.set(item, false);
            continue;
        }
        if (!cache.has(parentSeq)) {
            const res = await api.Get(`resources/${parentSeq}`, {});
            cache.set(parentSeq, res?.status === 200);
        }
        result.set(item, cache.get(parentSeq));
    }
    return result;
};

/**
 * 휴지통 항목 복원 공통 처리. 확인 다이얼로그와 결과 스낵바는 선택 개수와 무관하게 한 번만 띄운다.
 *
 * @param onRestored (restoredSeqs) => Promise|void  복원 성공 항목 반영
 */
export default function useTrashRestore({ onRestored } = {}) {
    const t = useTranslations();
    const { openDialog } = useDialog();
    const { showSnackbar } = useSnackbarContext();

    /** @return 복원 시도 여부 (확인 다이얼로그 취소 시 false) */
    const restoreItems = useCallback(
        async items => {
            const list = (Array.isArray(items) ? items : [items]).filter(Boolean);
            if (!list.length) return false;

            const peMap = await resolveParentExists(list);
            const toRoot = list.filter(item => item?.parentFolderSeq && !peMap.get(item));
            let rootDesc;
            if (toRoot.length) {
                rootDesc =
                    list.length === 1
                        ? t('restoreToRootDialog.text2')
                        : t('restoreToRootDialog.multiple');
            }

            const confirmed = await openDialog('alert', {
                title: t('common.dialog.restore.title'),
                description: rootDesc,
                buttonName: t('common.action.restore'),
            });
            if (!confirmed) return false;

            const restoredSeqs = [];
            let failCount = 0;

            for (const item of list) {
                const pe = peMap.get(item);
                const exists = await checkExists(item, pe ? item?.parentFolderSeq : undefined);
                if (!exists) {
                    failCount++;
                    continue;
                }
                if (exists?.resourceSeq) {
                    const proceed = await openDialog('alert', {
                        title: t('duplicateDialog.text1', { resourceName: item.resourceName }),
                        description: t('duplicateDialog.text2'),
                        buttonName: t('common.button.upload'),
                    });
                    if (!proceed) continue;
                }

                const res = await api.Patch(
                    item?.resourceType === 'FOLDER'
                        ? `trash/folders/${item.resourceSeq}/restore`
                        : `trash/files/${item.resourceSeq}/restore`,
                    { pe }
                );
                if (res?.status === 200) restoredSeqs.push(item.resourceSeq);
                else failCount++;
            }

            if (restoredSeqs.length && !failCount) {
                showSnackbar(t('restore.success'), 'success');
            } else if (restoredSeqs.length) {
                showSnackbar(
                    t('restore.partial', { success: restoredSeqs.length, fail: failCount }),
                    'warning'
                );
            } else if (failCount) {
                showSnackbar(t('restore.fail'), 'error');
            }

            if (restoredSeqs.length) await onRestored?.(restoredSeqs);
            return true;
        },
        [openDialog, showSnackbar, t, onRestored]
    );

    return { restoreItems };
}

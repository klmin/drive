import { useDialog } from '@/provider/DialogProvider';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import api from '@/utils/api';

export function useUserDelete({
    onClose = () => {},
    t,
    setCheckItems,
    getData,
    refreshLicense = () => {},
    listPatch,
}) {
    const { openDialog } = useDialog();
    const { showSnackbar } = useSnackbarContext();

    const handleDeleteAdmin = async items => {
        let successCount = 0;
        let failCount = 0;

        const userName = items[0].userName;
        openDialog('alert', {
            title: t('deleteAdminDialog.title'),
            description:
                items.length > 1
                    ? t('deleteAdminDialog.textMultiple', { count: items.length })
                    : t('deleteAdminDialog.textSingle', { userName: userName }),
            onClick: async () => {
                const deletedSeqs = [];
                for (let item of items) {
                    const res = await api.Patch(`admins/users/${item?.userSeq}/withdraw`, {});

                    if (res?.status === 200) {
                        successCount++;
                        deletedSeqs.push(item?.userSeq);
                    } else {
                        failCount++;
                    }
                }

                if (successCount > 0 && failCount === 0) {
                    showSnackbar(t('adminAdmins.delete.success'), 'success');
                } else if (successCount > 0 && failCount > 0) {
                    showSnackbar(
                        t('adminAdmins.delete.partial', { success: successCount, fail: failCount }),
                        'warning'
                    );
                } else {
                    showSnackbar(t('adminAdmins.delete.fail'), 'error');
                }

                setCheckItems([]);
                // 목록을 다시 읽지 않고 삭제된 행만 제거해 스크롤 위치를 유지한다
                if (listPatch) {
                    listPatch.remove(deletedSeqs);
                } else {
                    await getData(true);
                }
                refreshLicense();
            },
        });

        onClose();
    };

    const handleDeleteUser = async items => {
        let successCount = 0;
        let failCount = 0;

        const userName = items[0].userName;
        openDialog('alert', {
            title: t('deleteUserDialog.title'),
            description:
                items.length > 1
                    ? t('deleteUserDialog.textMultiple', { n: items.length })
                    : t('deleteUserDialog.textSingle'),
            onClick: async () => {
                const deletedSeqs = [];
                for (let item of items) {
                    const res = await api.Patch(`admins/users/${item?.userSeq}/withdraw`, {});

                    if (res?.status === 200) {
                        successCount++;
                        deletedSeqs.push(item?.userSeq);
                    } else {
                        failCount++;
                    }
                }

                if (successCount > 0 && failCount === 0) {
                    showSnackbar(
                        items.length > 1
                            ? t('adminUsers.delete.successMultiple', { n: items.length })
                            : t('adminUsers.delete.successSingle'),
                        'success'
                    );
                } else if (successCount > 0 && failCount > 0) {
                    showSnackbar(
                        t('adminUsers.delete.partial', { success: successCount, fail: failCount }),
                        'warning'
                    );
                } else {
                    showSnackbar(t('adminUsers.delete.fail'), 'error');
                }

                setCheckItems([]);
                // 목록을 다시 읽지 않고 삭제된 행만 제거해 스크롤 위치를 유지한다
                if (listPatch) {
                    listPatch.remove(deletedSeqs);
                } else {
                    await getData(true);
                }
                refreshLicense();
            },
        });

        onClose();
    };

    const handleCompletelyDeleteUser = async items => {
        openDialog('alert', {
            title: t('completelyDeleteAccountDialog.title'),
            description:
                items.length > 1
                    ? t('completelyDeleteAccountDialog.textMultiple', { n: items.length })
                    : t('completelyDeleteAccountDialog.textSingle'),
            buttonName: t('common.action.hardDelete'),
            onClick: async () => {
                const res = await api.Patch(`admins/users/pending-delete/permanent-delete`, {
                    ul: items.map(item => item.userSeq),
                });

                if (res.status === 200) {
                    showSnackbar(
                        items.length > 1
                            ? t('pendingDeletion.delete.successMultiple', { n: items.length })
                            : t('pendingDeletion.delete.successSingle'),
                        'success'
                    );
                } else {
                    showSnackbar(t('pendingDeletion.delete.fail'), 'error');
                }

                setCheckItems([]);
                if (listPatch && res.status === 200) {
                    listPatch.remove(items.map(item => item.userSeq));
                } else {
                    await getData(true);
                }
                refreshLicense();
            },
        });

        onClose();
    };

    return { handleDeleteAdmin, handleDeleteUser, handleCompletelyDeleteUser };
}

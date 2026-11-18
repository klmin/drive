import { useCallback } from 'react';
import api from '@/utils/api';
import checkExists from '@/utils/checkExists';
import { registerRecent } from '@/apis/recentApi';

export function useDrivePaste({
    router,
    getClipboard,
    getData,
    setCheckItems,
    showSnackbar,
    t,
    user,
    openDialog,
}) {
    const onPasteFromClipboard = useCallback(async () => {
        const { items = [] } = getClipboard();
        const pfs = router.query.pfs || '';

        // 1) 클립보드 비어있으면 return
        if (!items.length) {
            showSnackbar(t('clipboard.noItems'), 'error');
            return;
        }
        // 여기부터는 기존 onPaste 내용 그대로...
        // (공유 루트/최근/즐겨찾기 차단, 폴더 금지, 삭제 항목 금지, canEdit 검사 등등)
        // checkExists → copy API → recent 등록 → getData(true) → setCheckItems([])

        // 2) 붙여넣기 가능 여부 검사
        // - 현재 위치가 공유 문서함 root인 경우
        if (
            (router.pathname === '/shared' && !pfs) ||
            router.pathname === '/starred' ||
            router.pathname === '/recent'
        ) {
            return;
        }
        // - 폴더 복사 불가
        const hasFolder = items.some(it => it?.resourceType === 'FOLDER');
        if (hasFolder) {
            showSnackbar(t('clipboard.folderNotSupported'), 'error');
            return;
        }
        // - 휴지통에 있는 파일인 경우
        const hasDeleted = items.some(it => it?.deleteDate);
        if (hasDeleted) {
            showSnackbar(t('clipboard.cannotPasteDeletedItems'), 'error');
            return;
        }
        // - canEdit false 인 항목이 있으면 불가
        const hasNonCopyable = items.some(it => it?.canEdit === false);
        if (hasNonCopyable) {
            showSnackbar(t('clipboard.cannotCopySomeItems'), 'error');
            return;
        }

        // 3) targetFolder - 현재 위치
        const targetFolder = {
            resourceSeq: pfs || '',
            sharedByUserSeq: router.query.ss || '',
            userSeq: router.query.ss || user?.userSeq || '',
        };

        try {
            for (const i of items) {
                // 파일만 복사 대상 (위에서 폴더 차단)
                const copyFunc = async () => {
                    const res = await api.Post(`files/${i.resourceSeq}/copy`, {
                        // 타겟 폴더 ss
                        ts: targetFolder?.sharedByUserSeq || targetFolder?.userSeq,
                        // 원본 ss
                        ss: !targetFolder
                            ? i?.userSeq
                            : i?.sharedByUserSeq || i?.userSeq || undefined,
                        // 붙여넣기 하려는 폴더 (= 현재 위치)
                        pfs:
                            i.resourceSeq === targetFolder?.resourceSeq
                                ? ''
                                : targetFolder?.resourceSeq,
                    });

                    if (res?.code === 201) {
                        // 최근 문서함 등록
                        if (i?.resourceType === 'FILE') {
                            const registered = await registerRecent(
                                res?.data?.resourceSeq,
                                router.query.ss
                            );
                            if (!registered) {
                                showSnackbar(t('clipboard.pasteFail'), 'error');
                            }
                        }
                        showSnackbar(
                            t('clipboard.pasteSuccess', { count: items.length }),
                            'success'
                        );
                    } else {
                        showSnackbar(t('clipboard.pasteFail'), 'error');
                    }
                };

                // 4) 중복 검사 (원본이 아닌 붙여넣을 위치의 드라이브 기준으로 조회)
                const exists = await checkExists(
                    {
                        resourceName: i.resourceName,
                        resourceType: i.resourceType,
                        ss: targetFolder?.sharedByUserSeq || targetFolder?.userSeq,
                    },
                    pfs
                );

                if (exists?.resourceSeq) {
                    const userConfirmed = await openDialog('alert', {
                        title: t('duplicateDialog.text1', {
                            resourceName: i.resourceName,
                            resourceType: i.resourceType,
                            ss: router.query.ss || '',
                        }),
                        description: t('duplicateDialog.text2'),
                        buttonName: t('common.button.upload'),
                    });

                    if (userConfirmed) {
                        await copyFunc();
                    } else {
                        continue;
                    }
                } else if (exists) {
                    await copyFunc();
                }
            }
        } catch (err) {
            console.error(err);
            showSnackbar(t('clipboard.pasteFail'), 'error');
        } finally {
            await getData(true);
            setCheckItems([]);
        }
    }, [router.query.pfs, getClipboard, getData, setCheckItems, showSnackbar, t, user]);

    return { onPasteFromClipboard };
}

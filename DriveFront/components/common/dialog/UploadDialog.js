import React, { useEffect, useMemo, useState, useRef } from 'react';
import { useTranslations } from 'next-intl';
import api from '@/utils/api';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { useRouter } from '@/hooks/useRouter';
import { Box, Collapse, List, ListItemText, Paper, Stack, Typography } from '@mui/material';
import Image from 'next/image';
import { getFileByFileType, getFileExtension } from '@/constants/FILES';
import palette from '@/theme/palette';
import checkExists from '@/utils/checkExists';
import { useDialog } from '@/provider/DialogProvider';
import { registerRecent } from '@/apis/recentApi';
import StyledButton from '@/components/common/button/StyledButton';
import { useUser } from '@/context/UserContext';
import { v4 as uuidv4 } from 'uuid';
import { useDrivePreview } from '@/hooks/useDrivePreview';
import { BaseTooltip } from '../tooltip/BaseTooltip';
import {
    CHUNK_UPLOAD_THRESHOLD,
    ChunkUploadError,
    isBelowServerChunkThreshold,
    listPendingUploads,
    uploadInChunks,
} from '@/utils/chunkUpload';

// 컴포넌트 내부에 두면 매 렌더마다 새 Symbol이 생성되어
// 비동기 흐름에서 "e === STOP" 비교가 실패할 수 있음 → 컴포넌트 외부로 이동
const STOP = Symbol('STOP_UPLOAD');

// 단일 요청 업로드(POST /files) 재시도 — 서버가 업로드 동시 상한을 넘으면 본문을 읽기 전에 503(+ 연결 끊김)으로 거절한다.
// 청크 파트(chunkUpload.js)와 같이 1·2·4초 간격으로 3번 더 보낸다.
// 응답 없이 연결이 끊긴 경우는 본문을 다 보내기 전일 때만 재시도한다 — 다 보낸 뒤라면 서버가 이미 저장했을 수 있어 다시 보내면 파일이 둘이 된다.
const SINGLE_UPLOAD_ATTEMPTS = 4;
const SINGLE_UPLOAD_RETRY_BASE_MS = 1000;
const isBusyUploadResponse = (res, signal, bodyFullySent) =>
    !signal?.aborted &&
    (res?.code === 503 || res?.code === 429 || (res === undefined && !bodyFullySent()));
const postSingleUploadWithRetry = async (send, signal, bodyFullySent) => {
    let res = await send();
    for (
        let attempt = 1;
        attempt < SINGLE_UPLOAD_ATTEMPTS && isBusyUploadResponse(res, signal, bodyFullySent);
        attempt++
    ) {
        // 지터(50~100%) — 같은 순간 거절된 업로드들이 같은 순간 다시 몰리지 않게. 서버 Retry-After(1초)보다는 길다
        const backoff = SINGLE_UPLOAD_RETRY_BASE_MS * 2 ** (attempt - 1);
        await new Promise(resolve =>
            setTimeout(resolve, Math.max(1000, Math.round(backoff * (0.5 + Math.random() * 0.5))))
        );
        if (signal?.aborted) break;
        res = await send();
    }
    return res;
};

const UPLOAD_STATUS = {
    WAITING: '대기',
    UPLOADING: '진행중',
    DONE: '완료',
    FAILED: '실패',
    CANCELLED: '취소',
    USER_CANCELLED: '업로드취소',
    CAPACITY_LIMIT_EXCEEDED: '용량제한초과',
};

const addUniqueIdsToTree = nodes => {
    return nodes.map(node => ({
        ...node,
        id: uuidv4(),
        children: node.children ? addUniqueIdsToTree(node.children) : [],
    }));
};

const flattenTree = (nodes, parentPath = '', rootId = null) => {
    let list = [];
    for (const node of nodes) {
        const currentPath = parentPath ? `${parentPath}/${node.name}` : node.name;
        const currentRootId = rootId || node.id; // 최상위 폴더의 id를 rootId로 설정

        list.push({
            id: node.id,
            rootId: currentRootId,
            path: currentPath,
            name: node.name,
            type: node.type,
            status: UPLOAD_STATUS.WAITING,
        });
        if (node.children && node.children.length > 0) {
            list = list.concat(flattenTree(node.children, currentPath, currentRootId));
        }
    }
    return list;
};

/**
 * 폴더 노드의 하위 파일 수를 재귀적으로 집계
 *
 * 사용자가 중복 폴더 업로드를 거부할 때, 해당 폴더 하위 파일 수만큼
 * onUploadComplete를 호출하여 completedUploads 카운터를 보정하는 데 사용
 *
 * (폴더 자체는 totalFilesToUpload에 포함되지 않으므로
 *  하위 파일 수만큼만 카운터를 올려야 함)
 */
const countFilesInSubtree = node => {
    if (node.type === 'file') return 1;
    return (node.children || []).reduce((sum, child) => sum + countFilesInSubtree(child), 0);
};

/**
 * 큰 파일(CHUNK_UPLOAD_THRESHOLD 이상)을 청크 API 로 보내고, 결과를 단일 요청(api.PostForm)과 같은 모양으로 돌려준다
 * — 호출부의 상태 분기(201·QUOTA_003·SYSTEM_CONFIG_007·!res)를 그대로 쓰기 위해.
 *
 * - 성공: { status: 201, data: { data: { resourceSeq, resourceName } } }
 * - 서버 거절: { status, errorCode }
 * - 취소·네트워크 실패: undefined (호출부가 cancelMapRef 로 둘을 구분)
 * - 서버의 단일 요청 기준이 더 커서 청크 세션을 거절: USE_SINGLE_REQUEST (호출부가 POST /files 로 보낸다)
 */
const USE_SINGLE_REQUEST = Symbol('USE_SINGLE_REQUEST');

async function sendChunked(
    file,
    { accountKey, parentPfs, currentSs, existingResourceSeq, saveType, signal, onProgress, onSent }
) {
    try {
        const result = await uploadInChunks(
            file,
            {
                accountKey,
                parentFolderSeq: parentPfs ?? undefined,
                resourceSeq: existingResourceSeq ?? undefined,
                saveType:
                    existingResourceSeq && saveType ? String(saveType).toUpperCase() : undefined,
                sharedByUserSeq: currentSs ? Number(currentSs) : undefined,
            },
            { signal, onProgress, onPartsSent: onSent }
        );
        if (!result) return undefined;
        return { status: 201, data: { data: result } };
    } catch (e) {
        if (isBelowServerChunkThreshold(e)) return USE_SINGLE_REQUEST;
        if (e instanceof ChunkUploadError && e.status) {
            return { status: e.status, errorCode: e.errorCode };
        }
        return undefined;
    }
}

// 동시 업로드 요청 개수 제한
function createLimiter(limit) {
    let active = 0; // 현재 실행 중인 작업 수
    const queue = []; // 대기 중인 작업 큐

    // 큐에서 다음 작업 꺼내서 실행 (실행 중인 작업 수가 limit 미만일 때)
    const runNext = () => {
        if (active >= limit) return;
        const job = queue.shift();
        if (!job) return; // 대기 중인 작업 없음 → 종료

        active++;
        job()
            // 개별 작업 실패 시 전체 흐름을 막지 않도록 무시
            .catch(() => {})
            .finally(() => {
                active--;
                runNext(); // 다음 작업 실행
            });
    };

    // 작업을 큐에 등록하고 실행 시도
    const run = fn =>
        new Promise((resolve, reject) => {
            queue.push(async () => {
                try {
                    const res = await fn();
                    resolve(res);
                } catch (e) {
                    reject(e);
                }
            });
            runNext();
        });

    // 큐 비움 (이미 실행 중인 작업에는 영향 없음)
    const clearQueue = () => {
        queue.length = 0;
    };

    return { run, clearQueue };
}

function UploadStatusIcon({
    item,
    isHovered,
    isSentLocked,
    onGoToLocation,
    onCancel,
    onCancelFolder,
    onRetry,
    onRetryFolder,
    t,
}) {
    const { status, isGroup, canCancel } = item;

    const stopAndCall = (e, fn) => {
        e.preventDefault();
        e.stopPropagation();
        fn();
    };

    if (isHovered && status === UPLOAD_STATUS.DONE) {
        return (
            <BaseTooltip title={t('uploadProgressDialog.goToUploadLocation')}>
                <Box sx={{ height: 20 }} onClick={e => stopAndCall(e, onGoToLocation)}>
                    <Image
                        src={'/common/icon-ic-folder-outline.svg'}
                        alt={'folder'}
                        width={20}
                        height={20}
                    />
                </Box>
            </BaseTooltip>
        );
    }

    if (status === UPLOAD_STATUS.DONE) {
        return (
            <Box sx={{ height: 20 }}>
                <Image
                    src={'/upload/icon-ic-upload-confirm.svg'}
                    alt={'confirm'}
                    width={20}
                    height={20}
                />
            </Box>
        );
    }

    if (status === UPLOAD_STATUS.UPLOADING) {
        if (isGroup && canCancel) {
            // 취소 가능한 파일 있음 → 취소 버튼 표시
            return (
                <BaseTooltip title={t('uploadProgressDialog.cancel')}>
                    <Box
                        sx={{ p: 0, cursor: 'pointer', height: 20 }}
                        onClick={e => stopAndCall(e, onCancelFolder)}
                    >
                        <Image
                            src={'/upload/icon-ic-upload-stoppable.svg'}
                            alt={'confirm'}
                            width={20}
                            height={20}
                        />
                    </Box>
                </BaseTooltip>
            );
        }
        // 모든 파일이 이미 서버로 전송됨 → 취소 불가, 진행 중 표시
        return (
            <BaseTooltip title={t('uploadProgressDialog.uploading')}>
                <Box sx={{ height: 20 }}>
                    <Image
                        src={'/upload/icon-ic-upload-unstoppable.svg'}
                        alt={'confirm'}
                        width={20}
                        height={20}
                    />
                </Box>
            </BaseTooltip>
        );
    }

    if (status === UPLOAD_STATUS.WAITING) {
        if (isGroup) {
            // 폴더 대기 중 - 취소 버튼 표시
            return (
                <BaseTooltip title={t('uploadProgressDialog.cancel')}>
                    <Box
                        sx={{ p: 0, cursor: 'pointer', height: 20 }}
                        onClick={e => stopAndCall(e, onCancelFolder)}
                    >
                        <Image
                            src={'/upload/icon-ic-upload-stoppable.svg'}
                            alt={'confirm'}
                            width={20}
                            height={20}
                        />
                    </Box>
                </BaseTooltip>
            );
        }
        if (isSentLocked) {
            return (
                <BaseTooltip title={t('uploadProgressDialog.uploading')}>
                    <Box sx={{ height: 20 }}>
                        <Image
                            src={'/upload/icon-ic-upload-unstoppable.svg'}
                            alt={'confirm'}
                            width={20}
                            height={20}
                        />
                    </Box>
                </BaseTooltip>
            );
        }
        // completedUploads는 withUploadWrapper.finally에서 올라가므로
        // 여기서 올리면 중복될 수 있어 올리지 않음
        return (
            <BaseTooltip title={t('uploadProgressDialog.cancel')}>
                <Box
                    sx={{ p: 0, cursor: 'pointer', height: 20 }}
                    onClick={e => stopAndCall(e, onCancel)}
                >
                    <Image
                        src={'/upload/icon-ic-upload-stoppable.svg'}
                        alt={'confirm'}
                        width={20}
                        height={20}
                    />
                </Box>
            </BaseTooltip>
        );
    }

    if (status === UPLOAD_STATUS.USER_CANCELLED) {
        // 폴더: 취소된 파일들 재업로드 / 개별 파일: 재업로드
        return (
            <BaseTooltip title={t('uploadProgressDialog.restart')}>
                <Box
                    sx={{ p: 0, cursor: 'pointer', height: 20 }}
                    onClick={e => stopAndCall(e, isGroup ? onRetryFolder : onRetry)}
                >
                    <Image
                        src={'/upload/icon-ic-upload-resume.svg'}
                        alt={'confirm'}
                        width={20}
                        height={20}
                    />
                </Box>
            </BaseTooltip>
        );
    }

    if (status === UPLOAD_STATUS.CAPACITY_LIMIT_EXCEEDED) {
        return (
            <BaseTooltip title={t('user.fileList.tooltip.quotaExceeded')}>
                <Box sx={{ p: 0, height: 20 }}>
                    <Image
                        src={'/upload/icon-ic-upload-error.svg'}
                        alt={'error'}
                        width={20}
                        height={20}
                    />
                </Box>
            </BaseTooltip>
        );
    }

    // FAILED 또는 그 외 상태
    return (
        <BaseTooltip title={t('uploadProgressDialog.uploadFailedRetry')}>
            <Box sx={{ p: 0, height: 20 }}>
                <Image
                    src={'/upload/icon-ic-upload-error.svg'}
                    alt={'error'}
                    width={20}
                    height={20}
                />
            </Box>
        </BaseTooltip>
    );
}

export default function UploadDialog({ open, onClose, uploadSource = 'input', ...props }) {
    const { uploadItems, reload } = props;
    const LIMIT = 3;
    const limiterRef = useRef(null);

    const t = useTranslations();
    const user = useUser();
    const { showSnackbar } = useSnackbarContext();
    const router = useRouter();
    const { openDialog } = useDialog();
    const initialUploadRef = useRef(router.query.pfs);

    const [collOpen, setCollOpen] = useState(true);
    const [uploadList, setUploadList] = useState([]);
    const [hover, setHover] = useState(null);
    const [updateSuccess, setUpdateSuccess] = useState(false);

    // totalFilesToUpload: 연속 업로드 시 덮어쓰지 않고 누적
    // completedUploads: 리셋 없이 누적 (handleClose 시 둘 다 초기화)
    const totalFilesToUpload = useRef(0);
    const [completedUploads, setCompletedUploads] = useState(0);

    const cancelMapRef = useRef(new Map()); // id -> true (큐 대기 중 취소)
    const controllerMapRef = useRef(new Map()); // id -> AbortController
    const sentLockRef = useRef(new Map()); // id -> true (request body 전송 완료 → 취소 불가)
    const stoppedNodeMapRef = useRef(new Map()); // id -> { node, parentPfs } (취소된 파일 재업로드용)
    // QUOTA_003 발생 시 true로 설정 → 이후 대기 중인 파일들이 checkExists/업로드를 시도하지 않고 즉시 실패 처리
    const quotaExceededRef = useRef(false);
    // 용량 부족 때 "진행 중 업로드" 안내는 배치당 한 번만
    const pendingNoticeShownRef = useRef(false);

    /**
     * 용량 부족(QUOTA_003) 안내. 사용량 막대는 확정값만 보여 주므로, 진행 중 업로드가 공간을 잡고 있으면
     * 막대에는 여유가 있어 보여도 거절된다 — 그때 내가 취소할 수 있는 진행 중 업로드가 있으면 목록을 띄운다(설계 규칙 12).
     * 내 사용량(users/quota 의 uploadingByte)으로 판단하지 않는다 — 공유 폴더에 올릴 때 용량 검사 대상은 폴더 주인이라,
     * 내 uploadingByte 가 0 이어도 내가 그 폴더에 올리던 세션이 주인의 공간을 잡고 있을 수 있다.
     */
    const notifyQuotaExceeded = async () => {
        showSnackbar(t('upload.fail.quotaExceeded'), 'error');
        if (pendingNoticeShownRef.current) return;
        pendingNoticeShownRef.current = true;
        const sessions = await listPendingUploads();
        if (sessions?.length > 0) {
            openDialog('pendingUploads', { sessions });
        }
    };

    /** 업로드 세션 오류 코드(UPLOAD_*)별 안내 — 처리했으면 true */
    const notifyChunkFailure = res => {
        if (res?.errorCode === 'UPLOAD_002') {
            showSnackbar(t('upload.fail.sessionLimit'), 'error');
            return true;
        }
        if (res?.errorCode === 'UPLOAD_001' || res?.errorCode === 'UPLOAD_006') {
            showSnackbar(t('upload.fail.expired'), 'error');
            return true;
        }
        // 매직바이트 검사 — 내용이 확장자와 다르거나 실행 파일(단일 요청·청크 공통)
        if (res?.errorCode === 'UPLOAD_010') {
            showSnackbar(t('upload.fail.contentMismatch'), 'error');
            return true;
        }
        return false;
    };

    // 진행률(%) — 정수 단위로 바뀔 때만 상태 갱신(파트 전송 이벤트마다 목록 전체를 다시 그리지 않게)
    const progressRef = useRef(new Map());
    const updateProgress = (id, loaded, total) => {
        const percent = total > 0 ? Math.floor((loaded / total) * 100) : 0;
        if (progressRef.current.get(id) === percent) return;
        progressRef.current.set(id, percent);
        setUploadList(prev =>
            prev.map(item => (item.id === id ? { ...item, progress: percent } : item))
        );
    };

    const updateStatus = (id, pfs, status, parent, url, query, uploadName) => {
        setUploadList(prev => {
            const list = Array.isArray(prev) ? prev : [];
            return list.map(item =>
                item.id === id
                    ? {
                          ...item,
                          pfs,
                          status,
                          parent,
                          url,
                          query,
                          ...(uploadName ? { name: uploadName } : {}),
                      }
                    : item
            );
        });
    };

    const removeItemFromList = idToRemove => {
        setUploadList(prevList => {
            const itemToRemove = prevList.find(i => i.id === idToRemove);
            if (!itemToRemove) {
                return prevList;
            }

            if (itemToRemove.type === 'folder') {
                // 같은 rootId를 가진 모든 아이템 제거 (폴더 + 하위 파일)
                return prevList.filter(item => item.rootId !== itemToRemove.rootId);
            } else {
                // 파일은 자기 자신만 제거
                return prevList.filter(item => item.id !== idToRemove);
            }
        });
    };

    // STOP 신호 수신 시 남은 대기/진행중 아이템을 일괄 취소 상태로 변경
    const cancelRemainingUploads = () => {
        setUploadList(prev =>
            prev.map(item => {
                if (
                    item.status === UPLOAD_STATUS.UPLOADING ||
                    item.status === UPLOAD_STATUS.WAITING
                ) {
                    return { ...item, status: UPLOAD_STATUS.CANCELLED };
                }
                return item;
            })
        );
    };

    // 모든 ref 및 카운터 초기화
    // 닫힌 후에는 재전송 경로가 없으므로 stoppedNodeMapRef를 포함해 전부 정리
    const handleClose = () => {
        cancelMapRef.current.clear();
        controllerMapRef.current.clear();
        sentLockRef.current.clear();
        stoppedNodeMapRef.current.clear();
        quotaExceededRef.current = false;
        pendingNoticeShownRef.current = false;
        progressRef.current.clear();
        totalFilesToUpload.current = 0;
        setCompletedUploads(0);
        setUploadList([]);
        onClose();
    };

    useEffect(() => {
        const processUploads = async () => {
            if (uploadItems && uploadItems.length > 0) {
                // 새 배치가 시작될 때 quota 플래그 리셋
                // 이전 배치에서 QUOTA_003이 발생했더라도 새로 올리는 파일은 업로드를 시도해야 함
                quotaExceededRef.current = false;
                pendingNoticeShownRef.current = false;

                const itemsWithIds = addUniqueIdsToTree(uploadItems);
                const flatListForUi = flattenTree(itemsWithIds);
                initialUploadRef.current = router.query.pfs;

                const newFileCount = flatListForUi.filter(item => item.type === 'file').length;

                // 누적 방식으로 변경
                // 이전 배치를 덮어쓰면 completedUploads와 불일치가 발생해
                // updateSuccess가 너무 일찍 또는 영원히 발동 안 되는 문제가 생김
                totalFilesToUpload.current += newFileCount;
                // completedUploads는 리셋하지 않음 - handleClose 시 초기화

                setUploadList(prevList => [...prevList, ...flatListForUi]);

                // 파일이 없는 배치(폴더만)는 카운터 완료 조건 없이 즉시 성공 처리
                if (newFileCount === 0) {
                    setUpdateSuccess(true);
                }

                const rootPfs = router.query.pfs || null;
                try {
                    await uploadNodeRecursive(itemsWithIds, rootPfs, '', () => {
                        setCompletedUploads(prev => prev + 1);
                    });
                } catch (e) {
                    if (e === STOP) {
                        stopAllQueuedJobs();
                        cancelRemainingUploads();
                        setUpdateSuccess(true);
                    } else {
                        throw e;
                    }
                }
            }
        };

        processUploads();
    }, [uploadItems]);

    // 파일 카운터 기반 완료 감지
    useEffect(() => {
        if (totalFilesToUpload.current > 0 && completedUploads === totalFilesToUpload.current) {
            setUpdateSuccess(true);
        }
    }, [completedUploads]);

    // 업로드 완료 시 파일 목록 갱신
    useEffect(() => {
        if (!updateSuccess) return;
        setUpdateSuccess(false);
        if (initialUploadRef.current === router.query.pfs) {
            reload(true);
            window.postMessage('quota_reload', '*');
        }
    }, [updateSuccess, router.query.pfs]);

    useEffect(() => {
        limiterRef.current = createLimiter(LIMIT);
        return () => {
            limiterRef.current?.clearQueue();
        };
    }, []);

    const stopAllQueuedJobs = () => {
        limiterRef.current?.clearQueue(); // 아직 시작 안 한 작업만 제거
    };

    /**
     * 취소된 파일 재업로드
     *
     * stoppedNodeMapRef에 저장된 파일(노드 + 부모 폴더 정보)을 꺼내
     * 다시 limiter 큐에 등록함
     *
     * completedUploads는 최초 취소 시 이미 증가했으므로 여기서 다시 증가시키지 않음
     * 재시도 완료 후 setUpdateSuccess(true)를 직접 호출해 파일 목록을 갱신함
     */
    const retryUpload = async item => {
        const entry = stoppedNodeMapRef.current.get(item.id);

        if (!entry?.node?.file) {
            // stoppedNodeMapRef에 없는 경우 = 파일이 아직 limiter 큐 또는 checkExists 단계에 있음
            // 취소 플래그만 제거 -> limiter 잡이 실행될 때 자동으로 업로드 재개됨
            if (cancelMapRef.current.has(item.id)) {
                cancelMapRef.current.delete(item.id);
                updateStatus(
                    item.id,
                    undefined,
                    UPLOAD_STATUS.WAITING,
                    undefined,
                    item.url,
                    item.query
                );
            }
            return;
        }

        const { node, parentPfs } = entry;
        const currentSs = router.query.ss;
        const url = router.pathname.includes('shared') ? 'shared' : 'drive';
        let query = {};
        if (router.query.pfs) query.pfs = router.query.pfs;
        if (currentSs) query.ss = currentSs;

        // 상태 및 플래그 초기화
        cancelMapRef.current.delete(node.id);
        sentLockRef.current.set(node.id, false);
        stoppedNodeMapRef.current.delete(node.id);
        updateStatus(node.id, undefined, UPLOAD_STATUS.WAITING, undefined, url, query);

        const createFunc = async () => {
            const fileToSend = new File([node.file], node.name, {
                type: node.file.type,
                // 청크 업로드 이어 올리기 지문에 쓴다 — 빼면 지금 시각이 되어 같은 파일을 다시 골라도 못 찾는다
                lastModified: node.file.lastModified,
            });
            const controller = new AbortController();
            controllerMapRef.current.set(node.id, controller);
            let sentMarked = false;
            const markSent = () => {
                if (sentMarked) return;
                sentMarked = true;
                sentLockRef.current.set(node.id, true);
                updateStatus(node.id, undefined, UPLOAD_STATUS.UPLOADING, undefined, url, query);
            };

            let res =
                fileToSend.size >= CHUNK_UPLOAD_THRESHOLD
                    ? await sendChunked(fileToSend, {
                          accountKey: user?.userSeq,
                          parentPfs,
                          currentSs,
                          signal: controller.signal,
                          onProgress: (loaded, total) => updateProgress(node.id, loaded, total),
                          onSent: markSent,
                      })
                    : USE_SINGLE_REQUEST;
            if (res === USE_SINGLE_REQUEST) {
                res = await postSingleUploadWithRetry(
                    () =>
                        api.PostForm(
                            'files',
                            {
                                file: fileToSend,
                                pfs: parentPfs,
                                ss: currentSs || undefined,
                            },
                            {
                                signal: controller.signal,
                                onUploadProgress: event => {
                                    if (!event) return;
                                    if (event.total && event.loaded >= event.total) markSent();
                                },
                            }
                        ),
                    controller.signal,
                    () => sentMarked
                );
            }

            if (res?.status === 201) {
                const uploadName = res?.data?.data?.resourceName;
                updateStatus(
                    node.id,
                    res?.data?.data?.resourceSeq,
                    UPLOAD_STATUS.DONE,
                    parentPfs,
                    url,
                    query,
                    uploadName
                );
                // 재시도 완료 -> 파일 목록 갱신
                setUpdateSuccess(true);
                const registered = await registerRecent(
                    res?.data?.data?.resourceSeq,
                    currentSs || undefined
                );
                if (!registered) showSnackbar(t('recent.register.fail'), 'error');
            } else if (res?.errorCode === 'QUOTA_003') {
                updateStatus(node.id, undefined, UPLOAD_STATUS.FAILED, undefined, url, query);
                notifyQuotaExceeded();
            } else if (res?.errorCode === 'SYSTEM_CONFIG_007') {
                updateStatus(
                    node.id,
                    undefined,
                    UPLOAD_STATUS.CAPACITY_LIMIT_EXCEEDED,
                    undefined,
                    url,
                    query
                );
            } else if (!res) {
                // 재시도 중 다시 취소된 경우 → stoppedNodeMapRef에 재등록해 다음 재시도 가능하게 유지
                if (cancelMapRef.current.get(node.id)) {
                    stoppedNodeMapRef.current.set(node.id, { node, parentPfs });
                    return;
                }
                updateStatus(node.id, undefined, UPLOAD_STATUS.FAILED, undefined, url, query);
                showSnackbar(t('upload.fail.general'), 'error');
            } else {
                updateStatus(node.id, undefined, UPLOAD_STATUS.FAILED, undefined, url, query);
                if (!notifyChunkFailure(res)) showSnackbar(t('upload.fail.general'), 'error');
            }
        };

        await limiterRef.current.run(async () => {
            // limiter 슬롯 진입 시 재취소 여부 재확인
            if (cancelMapRef.current.get(node.id)) {
                stoppedNodeMapRef.current.set(node.id, { node, parentPfs });
                return;
            }
            await createFunc();
        });
    };

    const cancelFolderUpload = folderId => {
        setUploadList(prev => {
            const childFiles = prev.filter(
                item => item.rootId === folderId && item.type === 'file'
            );

            childFiles.forEach(file => {
                // 이미 서버에 전송 완료된 파일은 건드리지 않음
                if (sentLockRef.current.get(file.id)) return;

                // 취소 플래그 설정 (limiter 대기 중 실행 방지)
                cancelMapRef.current.set(file.id, true);

                // 전송 중인 요청 abort (아직 전송 완료 전인 파일)
                controllerMapRef.current.get(file.id)?.abort();
            });

            // UI 업데이트: 취소 대상 파일들을 '업로드취소' 상태로 변경
            return prev.map(item => {
                if (
                    item.rootId === folderId &&
                    item.type === 'file' &&
                    !sentLockRef.current.get(item.id) &&
                    (item.status === UPLOAD_STATUS.WAITING ||
                        item.status === UPLOAD_STATUS.UPLOADING)
                ) {
                    return { ...item, status: UPLOAD_STATUS.USER_CANCELLED };
                }
                return item;
            });
        });
    };

    const retryFolderUpload = folderId => {
        const cancelledFiles = uploadList.filter(
            item =>
                item.rootId === folderId &&
                item.type === 'file' &&
                item.status === UPLOAD_STATUS.USER_CANCELLED
        );
        if (cancelledFiles.length === 0) return;

        // 취소 플래그 해제
        cancelledFiles.forEach(file => {
            cancelMapRef.current.delete(file.id);
        });

        // stoppedNodeMapRef에 있는 파일 → 이미 limiter 큐를 떠난 파일 → retryUpload로 재등록
        // stoppedNodeMapRef에 없는 파일 → 아직 limiter 큐에 남아있는 파일 → cancelMapRef 삭제만으로 재개
        const filesToRetry = cancelledFiles.filter(file => stoppedNodeMapRef.current.has(file.id));
        const filesStillQueued = cancelledFiles.filter(
            file => !stoppedNodeMapRef.current.has(file.id)
        );

        // 큐에 남아있는 파일들 UI 상태 복원 ('업로드취소' → '대기')
        if (filesStillQueued.length > 0) {
            setUploadList(prev =>
                prev.map(item =>
                    filesStillQueued.some(f => f.id === item.id)
                        ? { ...item, status: UPLOAD_STATUS.WAITING }
                        : item
                )
            );
        }

        // stoppedNodeMapRef에 있는 파일들 재업로드
        filesToRetry.forEach(file => {
            retryUpload(file);
        });
    };

    /**
     * 업로드 함수를 실행하고 완료 콜백을 보장하는 래퍼
     *
     * - STOP 예외만 상위로 전파 (QUOTA 초과 등 전체 중단이 필요한 경우)
     * - 일반 실패는 내부에서 처리하고 전파하지 않음
     * - finally에서 onUploadComplete를 호출해 카운터를 증가시킴
     *   단, 폴더 업로드 시에는 onUploadComplete를 전달하지 않음
     *   (폴더 자체는 totalFilesToUpload 카운터 포함 대상x)
     */
    const withUploadWrapper = async (uploadFunc, onUploadComplete) => {
        try {
            await uploadFunc();
        } catch (e) {
            if (e === STOP) throw STOP;
            // 일반 실패는 전파하지 않음
        } finally {
            onUploadComplete?.();
        }
    };

    const uploadNodeRecursive = async (nodes, parentPfs, parentPath = '', onUploadComplete) => {
        const url = router.pathname.includes('shared') ? 'shared' : 'drive';
        const currentSs = router.query.ss;
        let query = {};

        if (router.query.pfs) query.pfs = router.query.pfs;
        if (currentSs) query.ss = currentSs;

        const fileJobs = []; // 파일 작업만 따로 모아둠 (동시 업로드 요청을 위해)

        for (const node of nodes) {
            const currentPath = parentPath ? `${parentPath}/${node.name}` : node.name;

            if (node.type === 'folder') {
                updateStatus(node.id, undefined, UPLOAD_STATUS.UPLOADING, undefined, url, query);

                const createFunc = async () => {
                    const res = await api.Post('folders', {
                        fn: node.name,
                        pfs: parentPfs,
                        ss: currentSs || undefined,
                    });
                    if (res?.code === 201) {
                        const newPfs = res.data.resourceSeq;
                        const uploadName = res.data.resourceName;
                        updateStatus(
                            node.id,
                            newPfs,
                            UPLOAD_STATUS.DONE,
                            undefined,
                            url,
                            query,
                            uploadName
                        );

                        if (node.children && node.children.length > 0) {
                            const ok = await uploadNodeRecursive(
                                node.children,
                                newPfs,
                                currentPath,
                                onUploadComplete
                            );
                            // 자식 업로드 실패 또는 QUOTA 초과 시 전체 중단
                            if (!ok) throw STOP;
                        }
                        setUpdateSuccess(true);
                    } else if (res?.errorCode === 'QUOTA_003') {
                        // 저장 용량 초과: 남은 모든 업로드를 중단
                        // 플래그를 세팅해 checkExists 단계에 있는 파일도 즉시 실패 처리되도록 함
                        quotaExceededRef.current = true;
                        updateStatus(
                            node.id,
                            undefined,
                            UPLOAD_STATUS.FAILED,
                            undefined,
                            url,
                            query
                        );
                        notifyQuotaExceeded();
                        throw STOP;
                    } else {
                        // 일반 실패(권한 없음, 부모 폴더 삭제 등): 이 폴더만 실패 처리
                        // throw STOP을 던지지 않으므로 다른 형제 업로드는 계속 진행됨
                        // 하위 파일들은 업로드되지 않으므로 카운터 보정
                        updateStatus(
                            node.id,
                            undefined,
                            UPLOAD_STATUS.FAILED,
                            undefined,
                            url,
                            query
                        );
                        showSnackbar(t('upload.fail.general'), 'error');
                        const skipCount = countFilesInSubtree(node);
                        for (let i = 0; i < skipCount; i++) onUploadComplete?.();
                    }
                };

                try {
                    const exists = await checkExists(
                        {
                            resourceName: node.name,
                            resourceType: 'FOLDER',
                            ss: currentSs || undefined,
                        },
                        parentPfs || undefined
                    );

                    if (exists?.resourceSeq) {
                        const userConfirmed = await openDialog('alert', {
                            title: t('duplicateDialog.text1', {
                                resourceName: node.name,
                            }),
                            description: t('duplicateDialog.text2'),
                            buttonName: t('common.button.upload'),
                            cancelButtonName: t('common.button.cancel'),
                        });
                        if (userConfirmed) {
                            // 폴더는 onUploadComplete 전달x -> 폴더 자체는 totalFilesToUpload 카운터에 포함 대상x
                            // withUploadWrapper.finally에서 카운트를 올리면 completedUploads가 초과됨
                            // 하위 파일들의 onUploadComplete는 uploadNodeRecursive 재귀에서 처리됨
                            await withUploadWrapper(createFunc);
                        } else {
                            // 업로드 거부 시 하위 파일 수만큼 카운터 보정
                            // 이 폴더의 파일들은 totalFilesToUpload에 포함되어 있으나 업로드되지 않으므로
                            // 그 수만큼 onUploadComplete를 호출해 카운터가 완료 조건에 도달하게 함
                            const skipCount = countFilesInSubtree(node);
                            for (let i = 0; i < skipCount; i++) onUploadComplete?.();
                            removeItemFromList(node.id);
                            continue;
                        }
                    } else if (exists === true) {
                        // 중복 없음 → 새로 업로드 (폴더 onUploadComplete 전달하지 않음)
                        await withUploadWrapper(createFunc);
                    } else {
                        // checkExists API 요청 실패 → 실패 처리
                        // 하위 파일들은 업로드되지 않으므로 카운터 보정
                        updateStatus(
                            node.id,
                            undefined,
                            UPLOAD_STATUS.FAILED,
                            undefined,
                            url,
                            query
                        );
                        showSnackbar(t('upload.fail.general'), 'error');
                        const skipCount = countFilesInSubtree(node);
                        for (let i = 0; i < skipCount; i++) onUploadComplete?.();
                    }
                } catch (error) {
                    // STOP은 전체 중단이므로 카운터 보정 없이 상위로 전파
                    if (error === STOP) throw STOP;
                    // 예외로 폴더 업로드 중단 - 하위 파일 카운터 보정
                    updateStatus(node.id, undefined, UPLOAD_STATUS.FAILED, undefined, url, query);
                    const skipCount = countFilesInSubtree(node);
                    for (let i = 0; i < skipCount; i++) onUploadComplete?.();
                }
            } else if (node.type === 'file') {
                // 실제 업로드 시작 전 (프론트 → 서버 전송 중)일 때는 '대기'로 표시
                // 이미 취소된 파일은 USER_CANCELLED를 덮어쓰지 않음
                // (폴더 생성 완료 후 재귀 호출 시 취소 상태가 WAITING으로 되돌아가는 버그 방지)
                if (!cancelMapRef.current.get(node.id)) {
                    updateStatus(node.id, undefined, UPLOAD_STATUS.WAITING, undefined, url, query);
                }

                const createFunc = async (existingResourceSeq = null, saveType = null) => {
                    const fileToSend = new File([node.file], node.name, {
                        type: node.file.type,
                        // 청크 업로드 이어 올리기 지문에 쓴다 — 빼면 지금 시각이 되어 같은 파일을 다시 골라도 못 찾는다
                        lastModified: node.file.lastModified,
                    });

                    const controller = new AbortController();
                    controllerMapRef.current.set(node.id, controller);
                    sentLockRef.current.set(node.id, false);

                    let sentMarked = false;
                    // 요청 본문(청크면 모든 파트)을 다 보냈다 → 이 뒤로는 취소 불가
                    const markSent = () => {
                        if (sentMarked) return;
                        sentMarked = true;
                        sentLockRef.current.set(node.id, true);
                        updateStatus(
                            node.id,
                            undefined,
                            UPLOAD_STATUS.UPLOADING,
                            undefined,
                            url,
                            query
                        );
                    };

                    let res =
                        fileToSend.size >= CHUNK_UPLOAD_THRESHOLD
                            ? await sendChunked(fileToSend, {
                                  accountKey: user?.userSeq,
                                  parentPfs,
                                  currentSs,
                                  existingResourceSeq,
                                  saveType,
                                  signal: controller.signal,
                                  onProgress: (loaded, total) =>
                                      updateProgress(node.id, loaded, total),
                                  onSent: markSent,
                              })
                            : USE_SINGLE_REQUEST;
                    if (res === USE_SINGLE_REQUEST) {
                        res = await postSingleUploadWithRetry(
                            () =>
                                api.PostForm(
                                    'files',
                                    {
                                        file: fileToSend,
                                        pfs: parentPfs,
                                        ss: currentSs || undefined,
                                        ...(existingResourceSeq && {
                                            rs: existingResourceSeq,
                                            st: saveType,
                                        }),
                                    },
                                    {
                                        signal: controller.signal,
                                        onUploadProgress: event => {
                                            if (!event) return;
                                            if (event.total && event.loaded >= event.total) {
                                                markSent();
                                            }
                                        },
                                    }
                                ),
                            controller.signal,
                            () => sentMarked
                        );
                    }

                    if (res?.status === 201) {
                        const uploadName = res?.data?.data?.resourceName;
                        updateStatus(
                            node.id,
                            res?.data?.data?.resourceSeq,
                            UPLOAD_STATUS.DONE,
                            parentPfs,
                            url,
                            query,
                            uploadName
                        );
                        setUpdateSuccess(true);
                        const registered = await registerRecent(
                            res?.data?.data?.resourceSeq,
                            currentSs || undefined
                        );
                        if (!registered) {
                            showSnackbar(t('recent.register.fail'), 'error');
                        }
                    } else if (res?.errorCode === 'QUOTA_003') {
                        // 저장 용량 초과: 플래그 세팅 후 전체 중단
                        quotaExceededRef.current = true;
                        updateStatus(
                            node.id,
                            undefined,
                            UPLOAD_STATUS.FAILED,
                            undefined,
                            url,
                            query
                        );
                        notifyQuotaExceeded();
                        throw STOP;
                    } else if (res?.errorCode === 'SYSTEM_CONFIG_007') {
                        // 관리자가 설정한 용량 제한 초과 → 이 파일만 실패 처리, 다른 업로드는 계속 진행
                        updateStatus(
                            node.id,
                            undefined,
                            UPLOAD_STATUS.CAPACITY_LIMIT_EXCEEDED,
                            undefined,
                            url,
                            query
                        );
                    } else if (!res) {
                        // 사용자가 직접 업로드 취소한 경우 → stoppedNodeMapRef에 저장해 재시도 가능하게 유지
                        if (cancelMapRef.current.get(node.id)) {
                            stoppedNodeMapRef.current.set(node.id, { node, parentPfs });
                            return;
                        }
                        // 일반 실패: 이 파일만 실패 처리, 다른 업로드는 계속 진행
                        updateStatus(
                            node.id,
                            undefined,
                            UPLOAD_STATUS.FAILED,
                            undefined,
                            url,
                            query
                        );
                        showSnackbar(t('upload.fail.general'), 'error');
                    } else {
                        // 일반 실패: 이 파일만 실패 처리, 다른 업로드는 계속 진행
                        updateStatus(
                            node.id,
                            undefined,
                            UPLOAD_STATUS.FAILED,
                            undefined,
                            url,
                            query
                        );
                        if (!notifyChunkFailure(res)) {
                            showSnackbar(t('upload.fail.general'), 'error');
                        }
                    }
                };

                // 파일 업로드 작업을 즉시 실행하지 않고 fileJobs에 등록
                // Promise.all로 일괄 관리하며, 실제 전송은 limiter가 제어
                const job = (async () => {
                    try {
                        // [Fix] 용량 초과가 이미 발생한 경우 checkExists 없이 즉시 실패 처리
                        // QUOTA_003 이후 checkExists 단계에 있는 파일이 불필요한 API 호출 및
                        // 중복 다이얼로그를 띄우는 것을 방지하고, unhandled rejection도 차단
                        if (quotaExceededRef.current) {
                            updateStatus(
                                node.id,
                                undefined,
                                UPLOAD_STATUS.FAILED,
                                undefined,
                                url,
                                query
                            );
                            onUploadComplete?.();
                            return;
                        }

                        // 폴더 업로드에 속한 파일은 중복 검사 제외, 개별로 올린 파일만 중복 검사 필요
                        if (!node.pfs) {
                            // [Fix] checkExists 호출 전에 취소 여부 먼저 확인
                            // 취소 버튼 클릭 후 checkExists가 완료되어 중복 다이얼로그가 뜨는 문제 방지
                            if (cancelMapRef.current.get(node.id)) {
                                stoppedNodeMapRef.current.set(node.id, { node, parentPfs });
                                onUploadComplete?.();
                                return;
                            }

                            const exists = await checkExists(
                                {
                                    resourceName: node.name,
                                    resourceType: 'FILE',
                                    ss: currentSs || undefined,
                                },
                                parentPfs || undefined
                            );

                            // checkExists 완료 후 다시 취소 여부 확인
                            // checkExists 대기 중 취소된 경우 이름 중복 확인 다이얼로그가 뜨지 않도록 차단
                            if (cancelMapRef.current.get(node.id)) {
                                stoppedNodeMapRef.current.set(node.id, { node, parentPfs });
                                onUploadComplete?.();
                                return;
                            }

                            if (exists?.resourceSeq) {
                                const existingResourceSeq = exists.resourceSeq;

                                const choice = await openDialog('fileDuplicate', {
                                    title: t('duplicateFileDialog.title'),
                                    description: t('duplicateFileDialog.description', {
                                        fileName: node.name,
                                    }),
                                    keepBothText: t('duplicateFileDialog.keepBoth'),
                                    replaceText: t('duplicateFileDialog.replace'),
                                });

                                if (choice === 'keepBoth') {
                                    await withUploadWrapper(createFunc, onUploadComplete);
                                    return;
                                } else if (choice === 'replace') {
                                    await withUploadWrapper(
                                        () => createFunc(existingResourceSeq, 'version'),
                                        onUploadComplete
                                    );
                                    return;
                                } else {
                                    onUploadComplete();
                                    removeItemFromList(node.id);
                                    return;
                                }
                            } else if (exists !== true) {
                                updateStatus(
                                    node.id,
                                    undefined,
                                    UPLOAD_STATUS.FAILED,
                                    undefined,
                                    url,
                                    query
                                );
                                showSnackbar(t('upload.fail.general'), 'error');
                                onUploadComplete();
                                return;
                            }
                        }

                        // limiter.run() 내부에서만 업로드를 실행 (동시 업로드 요청 최대 LIMIT개 유지)
                        await limiterRef.current.run(async () => {
                            // limiter 슬롯 진입 직전에도 취소/용량초과 여부 재확인
                            // checkExists 완료 후 슬롯 대기 중 QUOTA_003이 발생한 경우도 차단
                            if (cancelMapRef.current.get(node.id)) {
                                stoppedNodeMapRef.current.set(node.id, { node, parentPfs });
                                onUploadComplete?.();
                                return;
                            }
                            if (quotaExceededRef.current) {
                                updateStatus(
                                    node.id,
                                    undefined,
                                    UPLOAD_STATUS.FAILED,
                                    undefined,
                                    url,
                                    query
                                );
                                onUploadComplete?.();
                                return;
                            }

                            await withUploadWrapper(createFunc, onUploadComplete);
                        });
                    } catch (e) {
                        updateStatus(node.id, undefined, UPLOAD_STATUS.FAILED);
                        onUploadComplete(); // 에러 발생 파일도 카운터 완료 처리
                        if (e === STOP) throw STOP;
                    }
                })();
                fileJobs.push(job);
            }
        }

        // 모든 파일 작업 완료 대기 (폴더는 이미 순차적으로 await 완료됨)
        if (fileJobs.length > 0) {
            await Promise.all(fileJobs);
        }

        return true;
    };

    const totalUpload = useMemo(() => {
        const seen = new Set();
        return uploadList.filter(item => {
            if (!item || !item.rootId) return false;
            if (seen.has(item.rootId)) {
                return false;
            } else {
                seen.add(item.rootId);
                return true;
            }
        });
    }, [uploadList]);

    const displayList = useMemo(() => {
        const resultList = [];
        const allGroups = new Map();

        uploadList.forEach(item => {
            const groupKey = item.rootId || item.id;

            if (!allGroups.has(groupKey)) {
                allGroups.set(groupKey, []);
            }
            allGroups.get(groupKey).push(item);
        });

        for (const [groupKey, group] of allGroups.entries()) {
            const isFolderUpload = group.some(item => item.type === 'folder');

            if (isFolderUpload) {
                const representativeItem = { ...group.find(i => i.type === 'folder') };
                representativeItem.isGroup = true;

                const filesInGroup = group.filter(item => item.type === 'file');
                representativeItem.totalFileCount = filesInGroup.length;
                representativeItem.successFileCount = filesInGroup.filter(
                    i => i.type === 'file' && i.status === UPLOAD_STATUS.DONE
                ).length;

                // 폴더 그룹 상태 우선순위: 대기 > 진행중 > 실패/취소 > 완료 > 업로드취소
                // 진행 중인 파일이 남아있으면 FAILED보다 WAITING/UPLOADING을 우선 표시
                // → 일부 실패 + 나머지 진행 중일 때 폴더가 FAILED로 보여 닫기 버튼이 차단되는
                //   UX 불일치 방지 (닫기 버튼은 uploadList 기준으로 UPLOADING/WAITING을 감지함)
                if (filesInGroup.some(item => item.status === UPLOAD_STATUS.WAITING))
                    representativeItem.status = UPLOAD_STATUS.WAITING;
                else if (filesInGroup.some(item => item.status === UPLOAD_STATUS.UPLOADING))
                    representativeItem.status = UPLOAD_STATUS.UPLOADING;
                else if (
                    group.some(
                        item =>
                            item.status === UPLOAD_STATUS.FAILED ||
                            item.status === UPLOAD_STATUS.CANCELLED
                    )
                )
                    representativeItem.status = UPLOAD_STATUS.FAILED;
                else if (group.every(item => item.status === UPLOAD_STATUS.DONE))
                    representativeItem.status = UPLOAD_STATUS.DONE;
                else if (group.some(item => item.status === UPLOAD_STATUS.USER_CANCELLED))
                    representativeItem.status = UPLOAD_STATUS.USER_CANCELLED;
                else representativeItem.status = UPLOAD_STATUS.WAITING;

                // 아직 취소 가능한 파일이 있는지 (sentLock=false인 대기/진행중 파일)
                representativeItem.canCancel = filesInGroup.some(
                    f =>
                        (f.status === UPLOAD_STATUS.WAITING ||
                            f.status === UPLOAD_STATUS.UPLOADING) &&
                        !sentLockRef.current.get(f.id)
                );

                resultList.push(representativeItem);
            } else {
                group.forEach(fileItem => {
                    resultList.push(fileItem);
                });
            }
        }

        return resultList;
    }, [uploadList]);

    const successUpload = useMemo(() => {
        return displayList.filter(item => item.status === UPLOAD_STATUS.DONE);
    }, [displayList]);

    const { handleUploadDialogPreview } = useDrivePreview({ t });

    if (!open) return null;
    return (
        <Paper
            elevation={3}
            sx={{
                position: 'absolute',
                bottom: 24,
                right: { md: 24, xs: '50%' },
                transform: { xs: 'translateX(50%)', md: 'none' },
                width: { sm: 380, xs: '90vw' },
                minHeight: 'min-content',
                borderRadius: 2.5,
                overflow: 'hidden',
                border: `1px solid #6b88ffcc`,
                boxShadow: 'none',
                zIndex: 1204,
            }}
            PaperProps={{
                sx: {
                    boxShadow: 'none',
                },
            }}
        >
            <Box
                display="flex"
                alignItems="center"
                justifyContent="space-between"
                px={4}
                sx={{
                    cursor: 'pointer',
                    backgroundColor: palette.table_bg_selected,
                    borderBottom: `1px solid ${'#D1D8F5'}`,
                }}
                onClick={() => setCollOpen(!collOpen)}
            >
                <Box display="flex" alignItems="center" gap={2} sx={{ py: 2 }}>
                    {collOpen ? (
                        <BaseTooltip title={t('uploadProgressDialog.minimize')}>
                            <Image
                                src={'/common/chevron-chevron-stroke-16-down.svg'}
                                alt={'down'}
                                width={16}
                                height={16}
                            />
                        </BaseTooltip>
                    ) : (
                        <BaseTooltip title={t('uploadProgressDialog.maximize')}>
                            <Image
                                src={'/common/chevron-chevron-stroke-16-up.svg'}
                                alt={'up'}
                                width={16}
                                height={16}
                            />
                        </BaseTooltip>
                    )}
                    <Typography variant={'body5'} component={'div'} color={'text_tab_selected'}>
                        {t('uploadProgressDialog.uploadComplete', {
                            allCount: totalUpload.length,
                            doneCount: successUpload.length,
                        })}
                    </Typography>
                </Box>

                <BaseTooltip
                    title={
                        uploadList.find(
                            u =>
                                u?.status === UPLOAD_STATUS.UPLOADING ||
                                u?.status === UPLOAD_STATUS.WAITING
                        )
                            ? t('uploadProgressDialog.closeDisabledWhileUploading')
                            : t('common.button.close')
                    }
                >
                    <Image
                        onClick={e => {
                            e.stopPropagation();
                            // 대기(전송 중) 또는 진행중(서버 처리 중)인 파일이 있으면 닫기 차단
                            if (
                                uploadList.find(
                                    u =>
                                        u?.status === UPLOAD_STATUS.UPLOADING ||
                                        u?.status === UPLOAD_STATUS.WAITING
                                )
                            )
                                return;
                            handleClose();
                        }}
                        src={
                            uploadList.find(
                                u =>
                                    u?.status === UPLOAD_STATUS.UPLOADING ||
                                    u?.status === UPLOAD_STATUS.WAITING
                            )
                                ? '/common/icon-ic-table-close.4.svg'
                                : '/common/icon-ic-table-close.svg'
                        }
                        alt={'down'}
                        width={20}
                        height={20}
                    />
                </BaseTooltip>
            </Box>

            <Collapse in={collOpen}>
                <List
                    dense
                    sx={{
                        py: 3,
                        maxHeight: '300px',
                        overflowY: 'auto',
                    }}
                >
                    {displayList.map((item, index) => {
                        const itemName = item.name;
                        const currentStatus = item.status;
                        const isGroup = item.isGroup;
                        const type = item.type;
                        const itemKey = item.id || `${itemName}-${index}`;

                        return (
                            <StyledButton
                                variant={'text'}
                                size="small"
                                color="btn_white_bg_selected"
                                key={itemKey}
                                onMouseEnter={() => setHover(index + 1)}
                                onMouseLeave={() => setHover(null)}
                                onClick={async () => {
                                    if (
                                        currentStatus === UPLOAD_STATUS.UPLOADING ||
                                        currentStatus === UPLOAD_STATUS.FAILED
                                    )
                                        return;

                                    await handleUploadDialogPreview(item);
                                }}
                                sx={{
                                    width: '100%',
                                    height: 32,
                                    gap: 4,
                                    px: 4,
                                    display: 'flex',
                                    justifyContent: 'space-between',
                                }}
                            >
                                {/** 리소스 이름 영역 */}
                                <Stack
                                    direction={'row'}
                                    gap={2}
                                    alignItems="center"
                                    onClick={async e => {
                                        if (
                                            currentStatus === UPLOAD_STATUS.UPLOADING ||
                                            currentStatus === UPLOAD_STATUS.FAILED
                                        )
                                            return;
                                        e.preventDefault();
                                        e.stopPropagation();
                                        await handleUploadDialogPreview(item);
                                    }}
                                    sx={{
                                        flex: 1,
                                        minWidth: 0,
                                    }}
                                >
                                    <Image
                                        src={
                                            getFileByFileType(getFileExtension(itemName), type)?.src
                                        }
                                        alt={
                                            getFileByFileType(getFileExtension(itemName), type)
                                                ?.name
                                        }
                                        width={20}
                                        height={20}
                                    />
                                    <Typography noWrap variant={'body7'} color={'text_80'}>
                                        {itemName}
                                    </Typography>
                                </Stack>

                                {/** 리소스 업로드 상태 영역 */}
                                <Stack
                                    direction={'row'}
                                    alignItems={'center'}
                                    gap={3}
                                    sx={{
                                        flexShrink: 0,
                                        minWidth: 80,
                                        justifyContent: 'flex-end',
                                    }}
                                >
                                    {isGroup && (
                                        <ListItemText
                                            sx={{
                                                textAlign: 'right',
                                            }}
                                            primary={
                                                <Typography
                                                    noWrap
                                                    variant={'subtitle3'}
                                                    color={'text_60'}
                                                    component={'span'}
                                                >
                                                    {item.successFileCount}
                                                    <Typography
                                                        noWrap
                                                        variant={'body7'}
                                                        color={'text_80'}
                                                        component={'span'}
                                                    >
                                                        /{item.totalFileCount}
                                                    </Typography>
                                                </Typography>
                                            }
                                        />
                                    )}
                                    {/* 청크 업로드(큰 파일)만 진행률이 있다 — 파트 전송 중에는 '대기'(취소 가능) 상태 */}
                                    {!isGroup &&
                                        item.progress != null &&
                                        currentStatus === UPLOAD_STATUS.WAITING && (
                                            <Typography noWrap variant={'body7'} color={'text_60'}>
                                                {item.progress}%
                                            </Typography>
                                        )}
                                    <UploadStatusIcon
                                        item={item}
                                        isHovered={hover === index + 1}
                                        isSentLocked={sentLockRef.current.get(item.id)}
                                        onGoToLocation={() => {
                                            const qs = new URLSearchParams(item.query).toString();
                                            router.push(
                                                item.url === 'shared'
                                                    ? qs
                                                        ? `/shared?${qs}`
                                                        : '/shared'
                                                    : qs
                                                      ? `/drive?${qs}`
                                                      : '/drive'
                                            );
                                        }}
                                        onCancel={() => {
                                            // 1) 큐 대기 취소 플래그 설정
                                            cancelMapRef.current.set(item.id, true);
                                            // 2) 이미 요청이 시작된 경우(진행중)에는 abort
                                            controllerMapRef.current.get(item.id)?.abort();
                                            // UI 즉시 반영
                                            updateStatus(
                                                item.id,
                                                undefined,
                                                UPLOAD_STATUS.USER_CANCELLED,
                                                undefined,
                                                item.url,
                                                item.query
                                            );
                                        }}
                                        onCancelFolder={() => cancelFolderUpload(item.id)}
                                        onRetry={() => retryUpload(item)}
                                        onRetryFolder={() => retryFolderUpload(item.id)}
                                        t={t}
                                    />
                                </Stack>
                            </StyledButton>
                        );
                    })}
                </List>
            </Collapse>
        </Paper>
    );
}

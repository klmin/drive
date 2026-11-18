'use client';

import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
    Box,
    Checkbox,
    ClickAwayListener,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Divider,
    Drawer,
    IconButton,
    Menu,
    MenuItem,
    Paper,
    Popper,
    Stack,
    Typography,
    useMediaQuery,
    useTheme,
} from '@mui/material';
import Image from 'next/image';
import StyledButton from '@/components/common/button/StyledButton';
import ProfileImage from '@/components/common/input/ProfileImage';
import ShareTypeIcon from '@/components/common/share/ShareTypeIcon';
import palette from '@/theme/palette';
import { useRouter } from '@/hooks/useRouter';
import { useTranslations } from 'next-intl';
import { useDialog } from '@/provider/DialogProvider';
import { useSnackbarContext } from '@/provider/SnackbarProvider';
import { useUser } from '@/context/UserContext';
import api from '@/utils/api';
import { getResourceDetail } from '@/apis/resourceApi';
import debounce from 'just-debounce-it';
import { ArrowFilledDown } from '@/icon/Arrow';
import { copyTextToClipboard } from '@/utils/clipboard';
import { ExpandMore } from '@mui/icons-material';
import typography from '@/theme/typography';

const PERM_LABELS = {
    cv: 'user.sharePermission.view',
    ce: 'user.sharePermission.edit',
    cd: 'user.sharePermission.download',
    cu: 'user.sharePermission.upload',
    cs: 'user.sharePermission.share',
};

export default function FileShareDialog({ open, onClose, ...props }) {
    const SHARE_TYPES = {
        RESTRICTED: 'SPECIFIC_USERS',
        INTERNAL: 'ORG_LINK',
        PUBLIC: 'PUBLIC_LINK',
    };

    const theme = useTheme();
    const isTablet = useMediaQuery(theme.breakpoints.down('tablet'));

    const t = useTranslations();
    const router = useRouter();
    const { openDialog } = useDialog();
    const { item, reload, listPatch } = props;
    const { showSnackbar } = useSnackbarContext();
    const user = useUser();

    // ── 공유 유형 ───────────────────────────────────────────────────────────
    const [shareType, setShareType] = useState(SHARE_TYPES.RESTRICTED); // 현재 선택된 공유 유형
    const [initialShareType, setInitialShareType] = useState(null); // 변경 감지용 초기값
    const [availableShareTypes, setAvailableShareTypes] = useState([]); // 상위/하위 폴더 공유 설정에 의해 선택 가능한 공유 유형 목록

    // ── 링크 접근 권한 ──────────────────────────────────────────────────────
    const [selectedLinkPermission, setSelectedLinkPermission] = useState({
        cv: true,
        ce: false,
        cd: false,
        cu: false,
        cs: false,
    }); // 링크로 접근하는 사용자에게 부여되는 권한
    const [initialSelectedLinkPermission, setInitialSelectedLinkPermission] = useState({
        cv: true,
        ce: false,
        cd: false,
        cu: false,
        cs: false,
    }); // 변경 감지용 초기값

    // ── 공유 사용자 ─────────────────────────────────────────────────────────
    const [selectedUserList, setSelectedUserList] = useState([]); // 공유 대상 사용자 정보 목록 (이름, 이메일 등)
    const [initialSelectedUserList, setInitialSelectedUserList] = useState([]); // 변경 감지용 초기값
    const [selectedUserPermission, setSelectedUserPermission] = useState([]); // 공유 대상 사용자별 권한 목록 ({ us, cv, ce, cd, cu, cs })
    const [initialSelectedUserPermission, setInitialSelectedUserPermission] = useState([]); // 변경 감지용 초기값

    // ── 리소스 / 공유 정보 ──────────────────────────────────────────────────
    const [fileInfo, setFileInfo] = useState({}); // 공유 대상 리소스 상세 정보
    const [shareSeq, setShareSeq] = useState(null); // 공유 seq (신규 공유 시 null, 저장 후 발급)
    const [canShare, setCanShare] = useState(false); // 현재 로그인 사용자의 공유 권한 여부
    const [canView, setCanView] = useState(false); // 현재 로그인 사용자의 보기 권한 여부
    const [shareTypePermissions, setShareTypePermissions] = useState({}); // 공유 유형별 허용 권한 키 목록 ({ ORG_LINK: ['cv', 'ce', ...], ... })
    const [editable, setEditable] = useState(true); // 공유 유형 변경 가능 여부 (상위 폴더 공유 설정에 의해 제한될 수 있음)

    // ── 관리자 > 공유 유형별 on/off 설정값 ─────────────────────────────────
    const [allowSpecificUsers, setAllowSpecificUsers] = useState(false); // SPECIFIC_USERS(제한됨) 허용 여부
    const [allowOrgLink, setAllowOrgLink] = useState(false); // ORG_LINK(조직 내부 공개) 허용 여부
    const [allowPublic, setAllowPublic] = useState(false); // PUBLIC_LINK(전체 공개) 허용 여부

    // ── 이메일 알림 ─────────────────────────────────────────────────────────
    const [sendEmailNotification, setSendEmailNotification] = useState(true);

    // ── 사용자 검색 ─────────────────────────────────────────────────────────
    const [searchValue, setSearchValue] = useState(''); // 검색 입력값
    const [filteredUsers, setFilteredUsers] = useState([]); // 검색 API 결과 (raw); 렌더 시점에 selectedUserList와 비교해 이미 추가된 사용자 제외
    const [searchAnchorEl, setSearchAnchorEl] = useState(null); // 검색 드롭다운 Popper 앵커
    const searchFieldRef = useRef(null);

    // ── 메뉴 / 드로어 상태 ──────────────────────────────────────────────────
    const [userMenuAnchor, setUserMenuAnchor] = useState({}); // 사용자별 권한 메뉴 앵커 ({ [index]: el })
    const [activeMenuIndex, setActiveMenuIndex] = useState(null); // 현재 열린 사용자 권한 메뉴의 인덱스
    const [shareTypeMenuAnchor, setShareTypeMenuAnchor] = useState(null); // 공유 유형 선택 메뉴 앵커
    const [linkPermMenuAnchor, setLinkPermMenuAnchor] = useState(null); // 링크 권한 선택 메뉴 앵커
    const [linkAccessSheetOpen, setLinkAccessSheetOpen] = useState(false); // 태블릿 링크 접근 권한 바텀시트 열림 여부

    const isFile = item?.resourceType === 'FILE';
    const isShareCreator = fileInfo?.userSeq === user?.userSeq; // 공유를 등록한 사람이 현재 로그인 사용자인지 여부 (fileInfo 로딩 후 확정)

    const hasNewUsers = selectedUserList.some(
        u => !initialSelectedUserList.some(init => init.userSeq === u.userSeq)
    );

    const activeUser = activeMenuIndex !== null ? selectedUserList[activeMenuIndex] : null; // 현재 권한 메뉴가 열린 사용자
    const activeUserSeq = activeUser?.userSeq;

    // 상위/하위 폴더 공유 설정에 의해 선택 불가능한 공유 유형 여부
    const disableSpecific =
        availableShareTypes.length > 0 && !availableShareTypes.includes(SHARE_TYPES.RESTRICTED);
    const disableOrgLink =
        availableShareTypes.length > 0 && !availableShareTypes.includes(SHARE_TYPES.INTERNAL);
    const disablePublicLink =
        availableShareTypes.length > 0 && !availableShareTypes.includes(SHARE_TYPES.PUBLIC);

    // 사용자 권한 레이블: 사용자에게는 모든 권한 부여가 가능하므로
    // 리소스 타입(파일/폴더) 기준 전체 권한 키가 모두 활성화된 경우 '전체 허용' 표시
    const getPermLabel = (perms, excludeShare = false) => {
        const allKeys = isFile ? ['cv', 'ce', 'cd', 'cs'] : ['cv', 'ce', 'cu', 'cd', 'cs'];
        const keys = excludeShare ? allKeys.filter(k => k !== 'cs') : allKeys;
        const active = keys.filter(k => perms?.[k]);
        if (active.length === 0 || (active.length === 1 && active[0] === 'cv'))
            return t('user.sharePermission.viewOnly');
        if (active.length === keys.length) return t('user.sharePermission.full');
        return active
            .filter(k => k !== 'cv')
            .map(k => t(PERM_LABELS[k]))
            .join(', ');
    };

    // 링크 접근 권한 레이블: shareType별로 허용 불가 권한(isCheckboxDisabled)과
    // 백엔드 allowedPermissions 필터를 적용한 실제 허용 가능한 권한 기준으로 '전체 허용' 판단
    const getLinkPermLabel = perms => {
        const allKeys = isFile ? ['cv', 'ce', 'cd', 'cs'] : ['cv', 'ce', 'cu', 'cd', 'cs'];
        const availableKeys = allKeys
            .filter(key => {
                const allowed = shareTypePermissions[shareType];
                if (shareType === SHARE_TYPES.PUBLIC && key === 'ce') return true;
                return !allowed || allowed.includes(key);
            })
            .filter(key => {
                if (key === 'cv') return true;
                return !isCheckboxDisabled(key, shareType);
            });
        const active = availableKeys.filter(k => perms?.[k]);
        if (active.length === 0 || (active.length === 1 && active[0] === 'cv'))
            return t('user.sharePermission.viewOnly');
        if (active.length === availableKeys.length) return t('user.sharePermission.full');
        return active
            .filter(k => k !== 'cv')
            .map(k => t(PERM_LABELS[k]))
            .join(', ');
    };

    // ── 사용자 검색 ──────────────────────────────────────────────────────────

    const fetchFilteredUsers = async value => {
        try {
            const _shareSeq = item?.shareSeq || shareSeq;
            const endpoint = _shareSeq ? `shares/${_shareSeq}/users/search` : 'shares/users/search';
            const res = await api.Get(endpoint, {
                params: {
                    ss: item?.sharedByUserSeq || item?.userSeq || undefined,
                    sk: value,
                },
            });
            if (res?.data?.data) {
                setFilteredUsers(res.data.data?.items || []);
            }
        } catch (err) {
            console.error(err);
        }
    };

    const debouncedFetch = useMemo(() => debounce(val => fetchFilteredUsers(val), 300), []);

    useEffect(() => () => debouncedFetch.cancel?.(), [debouncedFetch]);

    const handleSearchChange = e => {
        const value = e.target.value;
        setSearchValue(value);
        if (!value.trim()) {
            setFilteredUsers([]);
            debouncedFetch.cancel?.();
            setSearchAnchorEl(null);
            return;
        }
        setSearchAnchorEl(
            e.currentTarget.closest('.MuiFormControl-root') || searchFieldRef.current
        );
        debouncedFetch(value);
    };

    const handleSelectUser = u => {
        if (!selectedUserList.some(x => x.userSeq === u.userSeq)) {
            setSelectedUserList(prev => [...prev, u]);
        }
        setSearchValue('');
        setFilteredUsers([]);
        setSearchAnchorEl(null);
    };

    // 리소스 유형에 따른 권한 키 목록 (파일: 업로드 없음, 폴더: 업로드 포함)
    const permKeys = isFile ? ['cv', 'ce', 'cd', 'cs'] : ['cv', 'ce', 'cu', 'cd', 'cs'];

    // 공유 타입에 따라 특정 권한 키의 체크박스 비활성화 여부
    const isCheckboxDisabled = (key, forShareType) => {
        if (key === 'cv') return true; // 보기는 항상 활성화
        if (forShareType === SHARE_TYPES.INTERNAL && key === 'cs') return true; // 조직 내부 공개: 공유 불가
        if (forShareType === SHARE_TYPES.PUBLIC && (key === 'cs' || key === 'cu')) return true; // 전체 공개: 공유·업로드 불가
        // if (forShareType === SHARE_TYPES.PUBLIC && !isFile && key === 'ce') return true; // 전체 공개 폴더: 편집 불가
        return false;
    };

    const isUserCheckboxDisabled = key => key === 'cv';

    // ── 사용자 권한 메뉴 ────────────────────────────────────────────────────

    const handleUserMenuOpen = (e, index) => {
        const target = e.currentTarget;
        setUserMenuAnchor(prev => ({ ...prev, [index]: target }));
        setActiveMenuIndex(index);
    };

    const handleUserMenuClose = index => {
        setUserMenuAnchor(prev => {
            const next = { ...prev };
            delete next[index];
            return next;
        });
        setActiveMenuIndex(null);
    };

    const handleUserPermChange = (key, value, userSeq) => {
        setSelectedUserPermission(prev => {
            const idx = prev.findIndex(p => p.us === userSeq);
            if (idx === -1) return prev;
            const next = [...prev];
            next[idx] = { ...next[idx], [key]: value };
            return next;
        });
    };

    // ── 공유 타입 메뉴 ──────────────────────────────────────────────────────

    const handleShareTypeMenuOpen = e => setShareTypeMenuAnchor(e.currentTarget);
    const handleShareTypeMenuClose = () => setShareTypeMenuAnchor(null);
    const handleShareTypeSelect = type => {
        setShareType(type);
        setShareTypeMenuAnchor(null);
        const allowed = shareTypePermissions[type];
        if (allowed) {
            setSelectedLinkPermission(prev =>
                Object.fromEntries(
                    Object.entries(prev).map(([k, v]) => [k, allowed.includes(k) ? v : false])
                )
            );
        }
    };

    // ── 링크 권한 메뉴 ──────────────────────────────────────────────────────

    const handleLinkPermMenuOpen = e => setLinkPermMenuAnchor(e.currentTarget);
    const handleLinkPermMenuClose = () => setLinkPermMenuAnchor(null);
    const handleLinkPermChange = (key, value) => {
        setSelectedLinkPermission(prev => ({ ...prev, [key]: value }));
    };

    // ── 사용자 제거 ─────────────────────────────────────────────────────────

    const handleRemoveUser = u => {
        setSelectedUserList(prev => prev.filter(x => x.userSeq !== u.userSeq));
    };

    // ── API 요청 ────────────────────────────────────────────────────────────

    const buildSafeUserPermission = () => {
        const initialSeqSet = new Set(initialSelectedUserList.map(u => u.userSeq));
        return selectedUserPermission.map(({ us, cv, ce, cu, cd, cs }) => ({
            us,
            cv,
            ce,
            cu,
            cd,
            cs,
            se: !initialSeqSet.has(us) ? sendEmailNotification : false,
        }));
    };

    /**
     * 공유 결과를 목록에 반영.
     *
     * 상세 조회(listPatch.refresh)는 isShared·shareSeq를 돌려주지 않아 공유 아이콘이 갱신되지
     * 않았다. 응답으로 받은 값으로 해당 행만 직접 갱신한다.
     * listPatch가 없는 화면(홈)은 기존처럼 전체 재조회한다.
     *
     * @param patch 성공한 경우의 부분 갱신값. 실패했으면 undefined
     */
    const applyRowChange = async patch => {
        if (!listPatch?.patch) {
            await reload(true);
            return;
        }
        if (patch) listPatch.patch(item?.resourceSeq, patch);
    };

    const handleSubmit = async () => {
        let patch;
        try {
            const _shareSeq = item?.shareSeq || shareSeq;
            const safeUserPermission = buildSafeUserPermission();

            if (!_shareSeq) {
                const res = await api.Post('shares', {
                    rs: item?.resourceSeq || undefined,
                    st: shareType,
                    ...selectedLinkPermission,
                    ts: safeUserPermission,
                });
                // 다시 열었을 때 신규 등록이 아니라 수정으로 가도록 shareSeq도 함께 반영한다
                if (res?.code === 201)
                    patch = { isShared: true, shareSeq: res?.data?.shareSeq ?? null, shareType };
                showSnackbar(
                    t(res?.code === 201 ? 'share.register.success' : 'share.register.fail'),
                    res?.code === 201 ? 'success' : 'error'
                );
            } else {
                if (!canShare) return;
                const res = await api.Patch(`shares/${_shareSeq}`, {
                    st: shareType,
                    ...selectedLinkPermission,
                    ts: safeUserPermission,
                });
                if (res?.status === 200) patch = { isShared: true, shareSeq: _shareSeq, shareType };
                showSnackbar(
                    t(res?.status === 200 ? 'share.update.success' : 'share.update.fail'),
                    res?.status === 200 ? 'success' : 'error'
                );
            }
        } catch (err) {
            console.error(err);
            showSnackbar(t('share.register.fail'), 'error');
        } finally {
            await applyRowChange(patch);
            onClose();
        }
    };

    const handleSubmitForSpecificUsers = async () => {
        let patch;
        try {
            if (!canShare) return;
            const _shareSeq = item?.shareSeq || shareSeq;
            const safeUserPermission = buildSafeUserPermission();
            // 공유 사용자를 모두 제거해 ts가 빈 배열인 경우도 전송해야 제거가 반영됨
            const res = await api.Put(`shares/${_shareSeq}/targets`, {
                ss: item?.sharedByUserSeq || item?.userSeq || undefined,
                ts: safeUserPermission,
            });
            if (res?.status === 200) patch = { isShared: true, shareSeq: _shareSeq };
            showSnackbar(
                t(res?.status === 200 ? 'share.update.success' : 'share.update.fail'),
                res?.status === 200 ? 'success' : 'error'
            );
        } catch (err) {
            console.error(err);
            showSnackbar(t('share.update.fail'), 'error');
        } finally {
            await applyRowChange(patch);
            onClose();
        }
    };

    const handleShareDelete = async () => {
        const _shareSeq = item?.shareSeq || shareSeq;
        try {
            const res = await api.Delete(`shares/${_shareSeq}`, {});
            if (res?.status === 200) {
                // 공유 문서함에서는 해제된 행이 목록에서 빠져야 한다
                if (listPatch?.remove && router.pathname.includes('/shared'))
                    listPatch.remove(item?.resourceSeq);
                else await applyRowChange({ isShared: false, shareSeq: null });
                showSnackbar(t('share.delete.success'), 'success');
                onClose();
            }
        } catch (err) {
            console.error(err);
            showSnackbar(t('share.delete.fail'), 'error');
        }
    };

    const handleCopyLink = async () => {
        let linkUrl = item?.linkUrl;
        try {
            const res = await api.Get(`resources/${item?.resourceSeq}/link`, {
                params: { ss: item?.sharedByUserSeq || item?.userSeq || undefined },
            });
            if (res?.status === 200) linkUrl = res.data?.data?.linkUrl;
        } catch {
            showSnackbar(t('share.linkCopy.fail'), 'error');
            return;
        }
        const success = await copyTextToClipboard(linkUrl, t('shareClipboardPrompt.description'));
        showSnackbar(
            t(success ? 'share.linkCopy.success' : 'share.linkCopy.fail'),
            success ? 'success' : 'error',
            3000,
            { vertical: 'bottom', horizontal: 'center' }
        );
    };

    // ── 데이터 로딩 ─────────────────────────────────────────────────────────

    const getSharedUserList = async () => {
        const _shareSeq = item?.shareSeq || shareSeq;
        if (!_shareSeq) return;
        try {
            const [users, targets] = await Promise.all([
                api.Get(`shares/${_shareSeq}/users`, {
                    params: { ss: item?.sharedByUserSeq || item?.userSeq || undefined },
                }),
                api.Get(`shares/${_shareSeq}`, {
                    params: { ss: item?.sharedByUserSeq || item?.userSeq || undefined },
                }),
            ]);

            if (users?.status === 200) {
                setSelectedUserList(users.data.data);
                setInitialSelectedUserList(users.data.data);
            }

            if (targets?.status === 200) {
                const file = targets.data.data;
                setFileInfo(file);
                setShareSeq(file.shareSeq);
                setShareType(file.shareType);
                setInitialShareType(file.shareType);
                const linkPerm = {
                    cv: file.canView,
                    ce: file.canEdit,
                    cd: file.canDownload,
                    cu: file.canUpload,
                    cs: file.canShare,
                };
                setSelectedLinkPermission(linkPerm);
                setInitialSelectedLinkPermission(linkPerm);

                const target = file.targets || [];
                if (target.length > 0) {
                    const perms = target.map(t => ({
                        us: t.userSeq,
                        cv: t.canView,
                        ce: t.canEdit,
                        cd: t.canDownload,
                        cu: t.canUpload,
                        cs: t.canShare,
                    }));
                    setSelectedUserPermission(perms);
                    setInitialSelectedUserPermission(perms);
                }
            }
        } catch (err) {
            console.error(err);
            showSnackbar(t('share.fetch.fail'), 'error');
        }
    };

    // 가능한 공유 유형 조회 (제한됨, 조직 내부 공개, 전체 공개)
    // ss 파라미터로 비등록자도 호출 가능
    const getShareType = async () => {
        try {
            const res = await api.Get(`shares/${item.resourceSeq}/share-types`, {
                params: { ss: item?.sharedByUserSeq || item?.userSeq || undefined },
            });
            if (res?.data?.code === 200) {
                const data = res.data.data;
                setEditable(data?.editable !== false);
                setAllowSpecificUsers(data?.allowSpecificUsers === true);
                setAllowOrgLink(data?.allowOrgLink === true);
                setAllowPublic(data?.allowPublic === true);
                const API_CODE_MAP = {
                    SPECIFIC_USERS: 'SPECIFIC_USERS',
                    ORG_LINK: 'ORG_LINK',
                    PUBLIC_LINK: 'PUBLIC_LINK',
                };
                const PERM_CODE_MAP = {
                    VIEW: 'cv',
                    EDIT: 'ce',
                    DOWNLOAD: 'cd',
                    UPLOAD: 'cu',
                    SHARE: 'cs',
                };
                const shareTypes = data?.shareTypes || [];
                const codes = shareTypes.map(s => API_CODE_MAP[s?.code]).filter(Boolean);
                setAvailableShareTypes(codes);

                // shareType별 허용 권한 키 목록 저장
                const permMap = {};
                shareTypes.forEach(s => {
                    const type = API_CODE_MAP[s?.code];
                    if (type) {
                        permMap[type] = (s.allowedPermissions || [])
                            .map(p => PERM_CODE_MAP[p])
                            .filter(Boolean);
                    }
                });
                setShareTypePermissions(permMap);

                // 기존 공유가 없을 때 가능한 첫 번째 타입을 기본값으로 설정
                // 우선순위(제한됨 > 조직 내부 공개 > 전체 공개) 중 관리자 설정(allowSpecificUsers 등)이 꺼져있지 않은 첫 항목을 사용
                if (!item?.shareSeq) {
                    const priorityOrder = [
                        SHARE_TYPES.RESTRICTED,
                        SHARE_TYPES.INTERNAL,
                        SHARE_TYPES.PUBLIC,
                    ];
                    const allowMap = {
                        [SHARE_TYPES.RESTRICTED]: data?.allowSpecificUsers === true,
                        [SHARE_TYPES.INTERNAL]: data?.allowOrgLink === true,
                        [SHARE_TYPES.PUBLIC]: data?.allowPublic === true,
                    };
                    const defaultType =
                        priorityOrder.find(c => codes.includes(c) && allowMap[c]) ??
                        codes.find(c => priorityOrder.includes(c)) ??
                        SHARE_TYPES.RESTRICTED;
                    setShareType(defaultType);
                    setInitialShareType(defaultType);
                }
            }
        } catch (err) {
            console.error(err);
            showSnackbar(t('share.fetchShareType.fail'), 'error');
        }
    };

    const getResource = async () => {
        const _shareSeq = item?.shareSeq || shareSeq;
        if (_shareSeq) return;
        try {
            const data = await getResourceDetail(item?.resourceSeq, {
                ss: item?.sharedByUserSeq || item?.userSeq,
            });
            if (data) setFileInfo(data);
        } catch (err) {
            console.error(err);
        }
    };

    const handleSetPermissionsFromUserSeq = userSeq => {
        if (!isShareCreator) {
            const target = selectedUserPermission.find(u => u.us === userSeq);
            if (target) {
                setCanView(target.cv);
                setCanShare(target.cs);
            }
        } else {
            setCanView(true);
            setCanShare(true);
        }
    };

    // ── 변경 감지 ───────────────────────────────────────────────────────────

    const isDirty = (() => {
        if (initialShareType !== shareType) return true;
        if (initialSelectedUserList.length !== selectedUserList.length) return true;
        const initSeqSet = new Set(initialSelectedUserList.map(u => u.userSeq));
        if (selectedUserList.some(u => !initSeqSet.has(u.userSeq))) return true;
        const KEYS = ['cv', 'ce', 'cd', 'cu', 'cs'];
        const initMap = new Map(initialSelectedUserPermission.map(u => [u.us, u]));
        if (
            selectedUserPermission.some(curr => {
                const init = initMap.get(curr.us);
                return init && KEYS.some(k => init[k] !== curr[k]);
            })
        )
            return true;
        return KEYS.some(k => initialSelectedLinkPermission[k] !== selectedLinkPermission[k]);
    })();

    const hasRemovedUsers = () =>
        initialSelectedUserList.some(
            initU => !selectedUserList.some(u => u.userSeq === initU.userSeq)
        );

    const handleCloseWithoutSave = async () => {
        await openDialog('alert', {
            title: t('common.dialog.closeWithoutSave.title'),
            description: t('common.dialog.closeWithoutSave.desc'),
            buttonName: t('common.button.confirm'),
            onClick: async () => onClose(),
        });
    };

    const handleSave = async () => {
        // 비등록자(canShare)는 shareType에 관계없이 PUT /targets 호출
        const saveAction =
            !isShareCreator && canShare ? handleSubmitForSpecificUsers : handleSubmit;

        if (hasRemovedUsers()) {
            await openDialog('alert', {
                title: t('removeShareUserDialog.title'),
                description: t('removeShareUserDialog.desc'),
                buttonName: t('common.button.confirm'),
                onClick: async () => saveAction(),
            });
        } else {
            await saveAction();
        }
    };

    const handleUnshareResource = async () => {
        await openDialog('alert', {
            title: t('unshareResourceDialog.title'),
            description: t('unshareResourceDialog.desc'),
            buttonName: t('unshareResourceDialog.confirm'),
            onClick: async () => handleShareDelete(),
        });
    };

    // ── 사이드 이펙트 ───────────────────────────────────────────────────────

    useEffect(() => {
        getResource();
        getSharedUserList();
    }, [shareSeq]);

    // open 시점에 즉시 호출 — ss 파라미터로 비등록자도 호출 가능 (allowSpecificUsers 등 초기 레이아웃 결정)
    useEffect(() => {
        if (open) getShareType();
    }, [open]);

    useEffect(() => {
        if (selectedUserList.length === 0) {
            setSelectedUserPermission([]);
            return;
        }
        setSelectedUserPermission(prev =>
            selectedUserList.map(u => {
                const existing = prev.find(p => p.us === u.userSeq);
                return (
                    existing ?? {
                        us: u.userSeq,
                        cv: true,
                        ce: false,
                        cd: false,
                        cu: false,
                        cs: false,
                    }
                );
            })
        );
    }, [selectedUserList]);

    useEffect(() => {
        if (!fileInfo || !user) return;
        handleSetPermissionsFromUserSeq(user?.userSeq);
    }, [fileInfo, user]);

    // ── 렌더 ────────────────────────────────────────────────────────────────

    const SHARE_TYPE_OPTIONS = [
        {
            value: SHARE_TYPES.RESTRICTED,
            label: t('common.shareType.restricted'),
            desc: t('common.shareType.restrictedDesc'),
            iconType: 'restricted',
        },
        {
            value: SHARE_TYPES.INTERNAL,
            label: t('common.shareType.internal'),
            desc: t('common.shareType.internalDesc'),
            iconType: 'internal',
        },
        {
            value: SHARE_TYPES.PUBLIC,
            label: t('common.shareType.public'),
            desc: t('common.shareType.publicDesc'),
            iconType: 'public',
        },
    ];

    const currentShareTypeOption =
        SHARE_TYPE_OPTIONS.find(o => o.value === shareType) ?? SHARE_TYPE_OPTIONS[0];

    return (
        <Dialog
            open={open}
            onClose={() => (isDirty ? handleCloseWithoutSave() : onClose())}
            fullScreen={isTablet}
            sx={isTablet ? { '& .MuiDialog-container': { p: 0 } } : {}}
            PaperProps={{
                sx: {
                    borderRadius: isTablet ? 0 : 4,
                    p: { xs: 0, tablet: '32px' },
                    pb: { xs: 0, tablet: '24px' },
                    margin: 0,
                    maxWidth: isTablet ? '100% !important' : '560px !important',
                    width: '100%',
                    overflow: 'visible',
                },
            }}
            disableEnforceFocus
        >
            {/* ── 제목 ── */}
            <DialogTitle
                sx={{
                    p: { xs: '16px', tablet: 0 },
                    pb: '16px',
                    pt: { xs: '32px' },
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '16px',
                }}
            >
                <Box
                    sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}
                >
                    <Typography variant="body1" noWrap>
                        {item?.resourceName} {t('common.action.share')}
                    </Typography>
                    <IconButton
                        sx={{ p: 0 }}
                        onClick={() => (isDirty ? handleCloseWithoutSave() : onClose())}
                    >
                        <Image src="/common/icon-ic-close.svg" width={24} height={24} alt="close" />
                    </IconButton>
                </Box>
            </DialogTitle>

            {/* ── 콘텐츠 ── */}
            <DialogContent
                sx={{
                    px: { xs: '16px', tablet: 0 },
                    pb: 0,
                }}
            >
                {!editable && (
                    <Box
                        sx={{
                            width: '100%',
                            px: '12px',
                            py: '10px',
                            bgcolor: palette.bg_subtle,
                            borderRadius: '4px',
                            display: 'flex',
                            alignItems: 'flex-start',
                            gap: '6px',
                            mt: { xs: 0, tablet: '16px' },
                        }}
                    >
                        <Box
                            sx={{
                                flexShrink: 0,
                                display: 'flex',
                                alignItems: 'center',
                                py: '2px',
                            }}
                        >
                            <Image
                                src="/common/share/icon-ic-share-warning.svg"
                                width={16}
                                height={16}
                                alt="warning"
                            />
                        </Box>
                        <Typography
                            variant="body7"
                            color="text_80"
                            sx={{ flex: '1 1 0', wordBreak: 'break-word' }}
                        >
                            {t('user.fileList.shareDialog.folderConflictWarning')}
                        </Typography>
                    </Box>
                )}
                <Stack
                    sx={{
                        pt: '8px !important',
                        pb: { xs: '172px !important', tablet: '8px !important' },
                        mt: { xs: 0, tablet: '16px' },
                    }}
                >
                    {/* 검색 입력 + 사용자 섹션 (제한됨일 때만 표시) */}
                    {allowSpecificUsers && (
                        <>
                            <ClickAwayListener onClickAway={() => setSearchAnchorEl(null)}>
                                <Box ref={searchFieldRef} sx={{ position: 'relative' }}>
                                    <Box
                                        sx={{
                                            width: '100%',
                                            height: 40,
                                            px: '12px',
                                            bgcolor: palette.white,
                                            overflow: 'hidden',
                                            borderRadius: '4px',
                                            outline: `1px ${palette.divider_12} solid`,
                                            outlineOffset: '-1px',
                                            display: 'inline-flex',
                                            alignItems: 'center',
                                            gap: '6px',
                                            boxSizing: 'border-box',
                                            opacity: !canShare ? 0.4 : 1,
                                            pointerEvents: !canShare ? 'none' : 'auto',
                                        }}
                                    >
                                        <Image
                                            src="/common/icon-ic-search-gray.svg"
                                            width={20}
                                            height={20}
                                            alt="search"
                                            style={{ flexShrink: 0 }}
                                        />
                                        <Box
                                            component="input"
                                            placeholder={t('shareAddUserDialog.search')}
                                            value={searchValue}
                                            onChange={handleSearchChange}
                                            disabled={!canShare}
                                            sx={{
                                                ...typography.body3,
                                                flex: '1 1 0',
                                                border: 'none',
                                                outline: 'none',
                                                bgcolor: 'transparent',
                                                fontFamily: 'inherit',
                                                color: palette.text_80,
                                                '&::placeholder': {
                                                    color: palette.text_40,
                                                    opacity: 1,
                                                },
                                            }}
                                        />
                                    </Box>
                                    <Popper
                                        open={
                                            Boolean(searchAnchorEl) &&
                                            filteredUsers.filter(
                                                u =>
                                                    !selectedUserList.some(
                                                        s => s.userSeq === u.userSeq
                                                    )
                                            ).length > 0
                                        }
                                        anchorEl={searchAnchorEl}
                                        placement="bottom-start"
                                        disablePortal
                                        modifiers={[
                                            { name: 'offset', options: { offset: [0, 4] } },
                                            {
                                                name: 'sameWidth',
                                                enabled: true,
                                                phase: 'beforeWrite',
                                                requires: ['computeStyles'],
                                                fn: ({ state }) => {
                                                    state.styles.popper.width = `${state.rects.reference.width}px`;
                                                },
                                            },
                                        ]}
                                        sx={{ zIndex: 1300 }}
                                    >
                                        <Paper
                                            sx={{
                                                py: '4px',
                                                bgcolor: palette.white,
                                                boxShadow: `0px 1px 10px ${palette.divider_12}`,
                                                borderRadius: '6px',
                                                outline: `1px ${palette.divider_12} solid`,
                                                outlineOffset: '-1px',
                                                maxHeight: 200,
                                                overflow: 'auto',
                                            }}
                                        >
                                            {filteredUsers
                                                .filter(
                                                    u =>
                                                        !selectedUserList.some(
                                                            s => s.userSeq === u.userSeq
                                                        )
                                                )
                                                .map(u => (
                                                    <Box
                                                        key={`${u.userSeq}-${u.userId}`}
                                                        onClick={() => handleSelectUser(u)}
                                                        sx={{
                                                            px: '8px',
                                                            py: '10px',
                                                            display: 'flex',
                                                            alignItems: 'center',
                                                            gap: '20px',
                                                            cursor: 'pointer',
                                                            '&:hover': {
                                                                bgcolor: palette.table_bg_hover,
                                                            },
                                                        }}
                                                    >
                                                        {/* 프로필 + 이름/이메일 */}
                                                        <Box
                                                            sx={{
                                                                flex: '1 1 0',
                                                                px: '4px',
                                                                display: 'flex',
                                                                alignItems: 'center',
                                                                gap: '12px',
                                                            }}
                                                        >
                                                            <Box
                                                                sx={{
                                                                    width: 32,
                                                                    height: 32,
                                                                    borderRadius: '50%',
                                                                    overflow: 'hidden',
                                                                    flexShrink: 0,
                                                                }}
                                                            >
                                                                <ProfileImage
                                                                    src={api.getProfileImage(
                                                                        u.userSeq
                                                                    )}
                                                                    width={32}
                                                                    height={32}
                                                                    alt="user"
                                                                />
                                                            </Box>
                                                            <Box
                                                                sx={{
                                                                    flex: '1 1 0',
                                                                    display: 'flex',
                                                                    flexDirection: 'column',
                                                                    gap: '4px',
                                                                    minWidth: 0,
                                                                }}
                                                            >
                                                                {/* 이름 행: 이름(조직) + 외부 뱃지 */}
                                                                <Box
                                                                    sx={{
                                                                        display: 'flex',
                                                                        alignItems: 'center',
                                                                        gap: '5px',
                                                                    }}
                                                                >
                                                                    <Box
                                                                        sx={{
                                                                            display: 'flex',
                                                                            alignItems: 'center',
                                                                            gap: '4px',
                                                                            minWidth: 0,
                                                                            overflow: 'hidden',
                                                                        }}
                                                                    >
                                                                        <Box
                                                                            sx={{
                                                                                display: 'flex',
                                                                                alignItems:
                                                                                    'center',
                                                                            }}
                                                                        >
                                                                            <Typography
                                                                                variant="body5"
                                                                                color="text_100"
                                                                                noWrap
                                                                            >
                                                                                {u.userName}
                                                                            </Typography>
                                                                        </Box>
                                                                        {u?.tenantSeq !==
                                                                            user?.tenantSeq && (
                                                                            <Typography
                                                                                variant="body7"
                                                                                color="text_60"
                                                                                noWrap
                                                                            >
                                                                                ({u?.tenantName})
                                                                            </Typography>
                                                                        )}
                                                                    </Box>
                                                                    {/**isExternalTenant */}
                                                                    {u?.tenantSeq !==
                                                                        user?.tenantSeq && (
                                                                        <Box
                                                                            sx={{
                                                                                px: '4px',
                                                                                py: '2px',
                                                                                bgcolor:
                                                                                    palette.badge_external_bg,
                                                                                borderRadius: '3px',
                                                                                outline: `1px ${palette.badge_external_border} solid`,
                                                                                outlineOffset:
                                                                                    '-1px',
                                                                                flexShrink: 0,
                                                                                display: 'flex',
                                                                                alignItems:
                                                                                    'center',
                                                                                justifyContent:
                                                                                    'center',
                                                                            }}
                                                                        >
                                                                            <Typography
                                                                                variant="caption_compact"
                                                                                color="badge_external_text"
                                                                            >
                                                                                {t(
                                                                                    'user.fileList.shareDialog.ownerBadge'
                                                                                )}
                                                                            </Typography>
                                                                        </Box>
                                                                    )}
                                                                </Box>
                                                                {/* 이메일 */}
                                                                <Typography
                                                                    variant="body9"
                                                                    color="text_60"
                                                                    noWrap
                                                                >
                                                                    {u.userId}
                                                                </Typography>
                                                            </Box>
                                                        </Box>
                                                    </Box>
                                                ))}
                                        </Paper>
                                    </Popper>
                                </Box>
                            </ClickAwayListener>

                            {/* 추가된 사용자 섹션 */}
                            <Stack
                                spacing={'10px'}
                                sx={{
                                    mt: '16px',
                                }}
                            >
                                <Typography variant="body8" color="text_60">
                                    {t('user.fileList.shareDialog.userDesc')}
                                </Typography>
                                <Box
                                    sx={{
                                        height: 240,
                                        borderRadius: '6px',
                                        ...(selectedUserList.length === 0
                                            ? {
                                                  outline: `1px ${palette.divider_8} solid`,
                                                  outlineOffset: '-1px',
                                                  bgcolor: palette.bg_gray,
                                                  display: 'flex',
                                                  flexDirection: 'column',
                                                  justifyContent: 'center',
                                                  alignItems: 'center',
                                              }
                                            : {
                                                  overflowY: 'auto',
                                                  outline: `1px ${palette.divider_8} solid`,
                                                  outlineOffset: '-1px',
                                                  bgcolor: palette.white, // 사용자 추가 시 배경색 #ffffff 처리
                                              }),
                                    }}
                                >
                                    {selectedUserList.length === 0 ? (
                                        <Typography variant="body5" color="text_60">
                                            {t('user.fileList.shareDialog.emptyUser')}
                                        </Typography>
                                    ) : (
                                        selectedUserList.map((u, index) => {
                                            const perm = selectedUserPermission.find(
                                                p => p.us === u.userSeq
                                            );
                                            const isSelf = u.userSeq === user?.userSeq;
                                            return (
                                                <Stack
                                                    key={u.userSeq}
                                                    direction="row"
                                                    alignItems="center"
                                                    justifyContent="space-between"
                                                    gap="20px"
                                                    sx={{
                                                        px: 2,
                                                        py: 2.5,
                                                    }}
                                                >
                                                    {/* 사용자 정보 */}
                                                    <Stack
                                                        direction="row"
                                                        alignItems="center"
                                                        gap={3}
                                                        sx={{ flex: 1, minWidth: 0, px: 1 }}
                                                    >
                                                        <Box
                                                            sx={{
                                                                width: 32,
                                                                height: 32,
                                                                borderRadius: '50%',
                                                                overflow: 'hidden',
                                                                flexShrink: 0,
                                                            }}
                                                        >
                                                            <ProfileImage
                                                                src={api.getProfileImage(u.userSeq)}
                                                                width={32}
                                                                height={32}
                                                                alt="user"
                                                            />
                                                        </Box>
                                                        <Stack sx={{ minWidth: 0 }}>
                                                            <Stack
                                                                direction="row"
                                                                alignItems="center"
                                                                gap={1.25}
                                                            >
                                                                <Box
                                                                    sx={{
                                                                        display: 'flex',
                                                                        alignItems: 'center',
                                                                        gap: '4px',
                                                                        minWidth: 0,
                                                                        overflow: 'hidden',
                                                                    }}
                                                                >
                                                                    <Box
                                                                        sx={{
                                                                            display: 'flex',
                                                                            alignItems: 'center',
                                                                        }}
                                                                    >
                                                                        <Typography
                                                                            variant="body5"
                                                                            color="text_100"
                                                                            noWrap
                                                                        >
                                                                            {u.userName}
                                                                        </Typography>
                                                                    </Box>
                                                                    {u?.tenantSeq !==
                                                                        user?.tenantSeq && (
                                                                        <Typography
                                                                            variant="body7"
                                                                            color="text_60"
                                                                            noWrap
                                                                        >
                                                                            ({u?.tenantName})
                                                                        </Typography>
                                                                    )}
                                                                </Box>
                                                                {/**isExternalTenant */}
                                                                {u?.tenantSeq !==
                                                                    user?.tenantSeq && (
                                                                    <Box
                                                                        sx={{
                                                                            px: '4px',
                                                                            py: '2px',
                                                                            bgcolor:
                                                                                palette.badge_external_bg,
                                                                            borderRadius: '3px',
                                                                            outline: `1px ${palette.badge_external_border} solid`,
                                                                            outlineOffset: '-1px',
                                                                            flexShrink: 0,
                                                                            display: 'flex',
                                                                            alignItems: 'center',
                                                                            justifyContent:
                                                                                'center',
                                                                        }}
                                                                    >
                                                                        <Typography
                                                                            variant="caption_compact"
                                                                            sx={{
                                                                                color: palette.badge_external_text,
                                                                            }}
                                                                        >
                                                                            {t(
                                                                                'user.fileList.shareDialog.ownerBadge'
                                                                            )}
                                                                        </Typography>
                                                                    </Box>
                                                                )}
                                                            </Stack>
                                                            <Stack
                                                                sx={{
                                                                    mt: isTablet ? '2px' : '4px',
                                                                }}
                                                            >
                                                                <Typography
                                                                    variant="body9"
                                                                    color="text_60"
                                                                    noWrap
                                                                >
                                                                    {u.userId}
                                                                </Typography>
                                                            </Stack>
                                                            {isTablet && (
                                                                <Stack sx={{ mt: '6px' }}>
                                                                    <Typography
                                                                        variant="body9"
                                                                        color="text_80"
                                                                        noWrap
                                                                    >
                                                                        {getPermLabel(perm, isSelf)}
                                                                    </Typography>
                                                                </Stack>
                                                            )}
                                                        </Stack>
                                                    </Stack>

                                                    {/* 권한 버튼 */}
                                                    {isTablet ? (
                                                        <StyledButton
                                                            variant="text"
                                                            disabled={!canShare}
                                                            onClick={e =>
                                                                handleUserMenuOpen(e, index)
                                                            }
                                                            sx={{
                                                                height: 28,
                                                                p: '6px',
                                                                borderRadius: '4px',
                                                                gap: '3px',
                                                                maxWidth: '40%',
                                                                minWidth: 0,
                                                                overflow: 'hidden',
                                                                ...(activeMenuIndex === index && {
                                                                    bgcolor:
                                                                        palette.btn_white_bg_selected,
                                                                }),
                                                            }}
                                                        >
                                                            <ExpandMore
                                                                sx={{
                                                                    width: 20,
                                                                    height: 20,
                                                                    color: palette.text_60,
                                                                }}
                                                            />
                                                        </StyledButton>
                                                    ) : (
                                                        <StyledButton
                                                            variant="text"
                                                            disabled={!canShare}
                                                            onClick={e =>
                                                                handleUserMenuOpen(e, index)
                                                            }
                                                            sx={{
                                                                height: 28,
                                                                pt: '6px',
                                                                pb: '6px',
                                                                pl: '12px',
                                                                pr: '8px',
                                                                borderRadius: '4px',
                                                                gap: '3px',
                                                                maxWidth: '40%',
                                                                minWidth: 0,
                                                                overflow: 'hidden',
                                                                ...(activeMenuIndex === index && {
                                                                    bgcolor:
                                                                        palette.btn_white_bg_selected,
                                                                }),
                                                            }}
                                                        >
                                                            <Typography
                                                                variant="body5"
                                                                color="text_80"
                                                                noWrap
                                                                sx={{ minWidth: 0 }}
                                                            >
                                                                {getPermLabel(perm, isSelf)}
                                                            </Typography>
                                                            {canShare && (
                                                                <ArrowFilledDown opacity=".6" />
                                                            )}
                                                        </StyledButton>
                                                    )}

                                                    {/* 사용자 권한 드롭다운 (데스크톱) */}
                                                    {!isTablet && (
                                                        <Menu
                                                            anchorEl={userMenuAnchor[index]}
                                                            open={Boolean(userMenuAnchor[index])}
                                                            onClose={() =>
                                                                handleUserMenuClose(index)
                                                            }
                                                            anchorOrigin={{
                                                                vertical: 'bottom',
                                                                horizontal: 'right',
                                                            }}
                                                            transformOrigin={{
                                                                vertical: 'top',
                                                                horizontal: 'right',
                                                            }}
                                                            PaperProps={{
                                                                sx: {
                                                                    mt: 1,
                                                                    borderRadius: '4px',
                                                                    boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
                                                                    outline: `1px ${palette.divider_12} solid`,
                                                                },
                                                            }}
                                                            MenuListProps={{
                                                                sx: { py: '4px' },
                                                            }}
                                                        >
                                                            {permKeys
                                                                .filter(
                                                                    key =>
                                                                        !(
                                                                            key === 'cs' &&
                                                                            u.userSeq ===
                                                                                user?.userSeq
                                                                        )
                                                                )
                                                                .map(key => {
                                                                    const currentPerm =
                                                                        selectedUserPermission.find(
                                                                            p => p.us === u.userSeq
                                                                        );
                                                                    const isDisabled =
                                                                        !canShare ||
                                                                        isUserCheckboxDisabled(key);
                                                                    const checked =
                                                                        !!currentPerm?.[key];
                                                                    return (
                                                                        <MenuItem
                                                                            key={key}
                                                                            disabled={isDisabled}
                                                                            onClick={() =>
                                                                                handleUserPermChange(
                                                                                    key,
                                                                                    !checked,
                                                                                    u.userSeq
                                                                                )
                                                                            }
                                                                            sx={{
                                                                                py: '6px',
                                                                                pl: '10px',
                                                                                pr: '48px',
                                                                                minHeight: 'auto',
                                                                                '&:hover': {
                                                                                    bgcolor:
                                                                                        palette.table_bg_hover,
                                                                                },
                                                                            }}
                                                                        >
                                                                            <Box
                                                                                sx={{
                                                                                    display: 'flex',
                                                                                    alignItems:
                                                                                        'center',
                                                                                    gap: '10px',
                                                                                    width: 93,
                                                                                    height: 24,
                                                                                }}
                                                                            >
                                                                                <Checkbox
                                                                                    size="small"
                                                                                    checked={
                                                                                        checked
                                                                                    }
                                                                                    onChange={e =>
                                                                                        handleUserPermChange(
                                                                                            key,
                                                                                            e.target
                                                                                                .checked,
                                                                                            u.userSeq
                                                                                        )
                                                                                    }
                                                                                    onClick={e =>
                                                                                        e.stopPropagation()
                                                                                    }
                                                                                    disabled={
                                                                                        isDisabled
                                                                                    }
                                                                                    sx={{
                                                                                        p: '2px',
                                                                                        color: palette.text_30,
                                                                                        '&.Mui-checked':
                                                                                            {
                                                                                                color: palette.text_80,
                                                                                            },
                                                                                    }}
                                                                                />
                                                                                <Typography
                                                                                    variant="body5"
                                                                                    color="text_80"
                                                                                >
                                                                                    {t(
                                                                                        PERM_LABELS[
                                                                                            key
                                                                                        ]
                                                                                    )}
                                                                                </Typography>
                                                                            </Box>
                                                                        </MenuItem>
                                                                    );
                                                                })}
                                                            <Divider
                                                                sx={{
                                                                    my: '4px',
                                                                    borderColor: palette.divider_8,
                                                                }}
                                                            />
                                                            <MenuItem
                                                                disabled={!canShare}
                                                                onClick={() => {
                                                                    handleRemoveUser(u);
                                                                    handleUserMenuClose(index);
                                                                }}
                                                                sx={{
                                                                    height: 36,
                                                                    pl: '12px',
                                                                    pr: '48px',
                                                                    minHeight: 'auto',
                                                                }}
                                                            >
                                                                <Box
                                                                    sx={{
                                                                        display: 'flex',
                                                                        alignItems: 'center',
                                                                        gap: '8px',
                                                                    }}
                                                                >
                                                                    <Image
                                                                        src={
                                                                            '/common/icon-ic-trash-outline-status-error.svg'
                                                                        }
                                                                        width={20}
                                                                        height={20}
                                                                        alt={'close'}
                                                                    />
                                                                    <Typography
                                                                        variant="body5"
                                                                        sx={{
                                                                            color: palette.status_error,
                                                                        }}
                                                                    >
                                                                        {t(
                                                                            'user.fileList.shareDialog.removePermission'
                                                                        )}
                                                                    </Typography>
                                                                </Box>
                                                            </MenuItem>
                                                        </Menu>
                                                    )}
                                                </Stack>
                                            );
                                        })
                                    )}
                                </Box>
                            </Stack>

                            {/* 사용자 권한 바텀 시트 (태블릿 이하) */}
                            {isTablet && (
                                <Drawer
                                    anchor="bottom"
                                    open={activeMenuIndex !== null}
                                    onClose={() => handleUserMenuClose(activeMenuIndex)}
                                    disablePortal
                                    ModalProps={{ keepMounted: false }}
                                    PaperProps={{
                                        sx: {
                                            borderRadius: '24px 24px 0 0',
                                            outline: `1px ${palette.divider_8} solid`,
                                            outlineOffset: '-1px',
                                            pb: '60px',
                                            gap: '8px',
                                        },
                                    }}
                                >
                                    {/* 드래그 핸들 */}
                                    <Box
                                        sx={{
                                            px: '10px',
                                            py: '8px',
                                            display: 'flex',
                                            flexDirection: 'column',
                                            alignItems: 'center',
                                            gap: '10px',
                                        }}
                                    >
                                        <Box
                                            sx={{
                                                width: 44,
                                                height: 4,
                                                borderRadius: 2,
                                                bgcolor: palette.divider_12,
                                            }}
                                        />
                                    </Box>
                                    {/* 헤더: 타이틀 + 닫기 버튼 */}
                                    <Box
                                        sx={{
                                            pl: '48px',
                                            pr: '16px',
                                            display: 'flex',
                                            justifyContent: 'space-between',
                                            alignItems: 'center',
                                        }}
                                    >
                                        <Typography
                                            variant="body5"
                                            color="text_100"
                                            fontWeight={600}
                                            sx={{ flex: 1, textAlign: 'center' }}
                                        >
                                            {t('user.fileList.shareDialog.permissionSheetTitle')}
                                        </Typography>
                                        <IconButton
                                            sx={{ p: '4px' }}
                                            onClick={() => handleUserMenuClose(activeMenuIndex)}
                                        >
                                            <Image
                                                src="/common/icon-ic-close.svg"
                                                width={24}
                                                height={24}
                                                alt="close"
                                            />
                                        </IconButton>
                                    </Box>
                                    {/* 권한 체크박스 목록 */}
                                    <Box
                                        sx={{
                                            pt: '4px',
                                            px: '20px',
                                            display: 'flex',
                                            flexDirection: 'column',
                                            gap: '8px',
                                        }}
                                    >
                                        <Box
                                            sx={{
                                                display: 'flex',
                                                flexDirection: 'column',
                                                gap: '2px',
                                            }}
                                        >
                                            {permKeys
                                                .filter(
                                                    key =>
                                                        !(
                                                            key === 'cs' &&
                                                            activeUserSeq === user?.userSeq
                                                        )
                                                )
                                                .map(key => {
                                                    const currentPerm = selectedUserPermission.find(
                                                        p => p.us === activeUserSeq
                                                    );
                                                    const isDisabled =
                                                        !canShare || isUserCheckboxDisabled(key);
                                                    const checked = !!currentPerm?.[key];
                                                    return (
                                                        <Box
                                                            key={key}
                                                            onClick={() =>
                                                                !isDisabled &&
                                                                handleUserPermChange(
                                                                    key,
                                                                    !checked,
                                                                    activeUserSeq
                                                                )
                                                            }
                                                            sx={{
                                                                display: 'flex',
                                                                justifyContent: 'space-between',
                                                                alignItems: 'center',
                                                                py: '6px',
                                                                opacity: isDisabled ? 0.4 : 1,
                                                                cursor: isDisabled
                                                                    ? 'default'
                                                                    : 'pointer',
                                                            }}
                                                        >
                                                            <Typography
                                                                variant="body5"
                                                                color="text_100"
                                                            >
                                                                {t(PERM_LABELS[key])}
                                                            </Typography>
                                                            <Checkbox
                                                                size="small"
                                                                checked={checked}
                                                                onChange={e =>
                                                                    handleUserPermChange(
                                                                        key,
                                                                        e.target.checked,
                                                                        activeUserSeq
                                                                    )
                                                                }
                                                                onClick={e => e.stopPropagation()}
                                                                disabled={isDisabled}
                                                                sx={{
                                                                    p: '2px',
                                                                    color: palette.text_30,
                                                                    '&.Mui-checked': {
                                                                        color: palette.text_80,
                                                                    },
                                                                }}
                                                            />
                                                        </Box>
                                                    );
                                                })}
                                        </Box>
                                    </Box>
                                    {/* 제거 섹션 */}
                                    {canShare && (
                                        <>
                                            <Divider sx={{ borderColor: palette.divider_8 }} />
                                            <Box sx={{ px: '20px', borderRadius: '4px' }}>
                                                <Box
                                                    onClick={() => {
                                                        handleRemoveUser(activeUser);
                                                        handleUserMenuClose(activeMenuIndex);
                                                    }}
                                                    sx={{
                                                        height: 36,
                                                        py: '6px',
                                                        pr: '2px',
                                                        display: 'flex',
                                                        alignItems: 'center',
                                                        gap: '10px',
                                                        cursor: 'pointer',
                                                    }}
                                                >
                                                    <Typography
                                                        variant="body5"
                                                        sx={{
                                                            flex: 1,
                                                            color: palette.status_error,
                                                        }}
                                                    >
                                                        {t(
                                                            'user.fileList.shareDialog.removePermission'
                                                        )}
                                                    </Typography>
                                                    <Image
                                                        src="/common/icon-ic-trash-outline-status-error.svg"
                                                        width={20}
                                                        height={20}
                                                        alt="remove"
                                                    />
                                                </Box>
                                            </Box>
                                        </>
                                    )}
                                </Drawer>
                            )}
                        </>
                    )}

                    {/** 이메일 알림 보내기 */}
                    {hasNewUsers && (
                        <Stack
                            sx={{
                                mt: '8px',
                            }}
                        >
                            <Box
                                onClick={() => setSendEmailNotification(prev => !prev)}
                                sx={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '6px',
                                    cursor: 'pointer',
                                }}
                            >
                                <Checkbox
                                    size="small"
                                    checked={sendEmailNotification}
                                    onChange={e => setSendEmailNotification(e.target.checked)}
                                    onClick={e => e.stopPropagation()}
                                    sx={{
                                        p: '2px',
                                        color: palette.text_30,
                                        '&.Mui-checked': {
                                            color: palette.text_80,
                                        },
                                    }}
                                />
                                <Typography variant="body5" color="text_80">
                                    {t('shareAddUserDialog.sendEmail')}
                                </Typography>
                            </Box>
                        </Stack>
                    )}

                    {/* 일반 접근 권한 */}
                    <Stack
                        spacing="10px"
                        sx={{
                            mt: '24px',
                        }}
                    >
                        <Typography variant="body8" color="text_60" fontWeight={600}>
                            {t('user.fileList.shareDialog.accessTitle')}
                        </Typography>
                        <Box
                            sx={{
                                p: '8px',
                                borderRadius: '6px',
                                outline: `1px ${palette.divider_8} solid`,
                                outlineOffset: '-1px',
                                display: 'flex',
                                alignItems: 'center',
                                gap: '20px',
                            }}
                        >
                            {/* 공유 타입 선택 */}
                            <Stack
                                direction="row"
                                alignItems="center"
                                gap="6px"
                                sx={{
                                    flex: '1 1 0',
                                    px: '4px',
                                    borderRadius: '4px',
                                }}
                            >
                                <ShareTypeIcon type={currentShareTypeOption.iconType} />
                                <Stack sx={{ flex: '1 1 0', borderRadius: '4px' }}>
                                    {!isTablet && (
                                        <StyledButton
                                            variant="text"
                                            disabled={
                                                !isShareCreator ||
                                                (disableSpecific &&
                                                    disableOrgLink &&
                                                    disablePublicLink)
                                            }
                                            onClick={
                                                isTablet
                                                    ? () => setLinkAccessSheetOpen(true)
                                                    : handleShareTypeMenuOpen
                                            }
                                            sx={{
                                                height: 28,
                                                px: '8px',
                                                py: '6px',
                                                bgcolor: shareTypeMenuAnchor
                                                    ? palette.btn_white_bg_selected
                                                    : palette.white,
                                                alignSelf: 'flex-start',
                                                gap: '3px',
                                            }}
                                        >
                                            <Typography variant="body5" color="text_100">
                                                {currentShareTypeOption.label}
                                            </Typography>
                                            {!(
                                                disableSpecific &&
                                                disableOrgLink &&
                                                disablePublicLink
                                            ) && (
                                                <ArrowFilledDown
                                                    opacity={isShareCreator ? '.6' : '.3'}
                                                />
                                            )}
                                        </StyledButton>
                                    )}
                                    {isTablet && (
                                        <Box
                                            sx={{
                                                height: 28,
                                                px: '8px',
                                                py: '6px',
                                                bgcolor: shareTypeMenuAnchor
                                                    ? palette.btn_white_bg_selected
                                                    : palette.white,
                                                alignSelf: 'flex-start',
                                                gap: '3px',
                                            }}
                                        >
                                            <Typography variant="body5" color="text_100">
                                                {currentShareTypeOption.label}
                                            </Typography>
                                        </Box>
                                    )}
                                    <Box
                                        sx={{
                                            pb: '4px',
                                            pl: '8px',
                                            display: 'flex',
                                            flexDirection: 'column',
                                            gap: '6px',
                                        }}
                                    >
                                        <Typography variant="body9" color="text_60">
                                            {currentShareTypeOption.desc}
                                        </Typography>
                                        {isTablet && shareType !== SHARE_TYPES.RESTRICTED && (
                                            <Typography variant="body9_10" color="text_80">
                                                {getLinkPermLabel(selectedLinkPermission)}
                                            </Typography>
                                        )}
                                    </Box>
                                </Stack>
                            </Stack>

                            {/* 링크 접근 권한(공유 타입) (데스크톱) */}
                            {/** 공유 유형 API 응답 값(allowSpecificUsers, allowOrgLink, allowPublic)에 따라 공유 타입 메뉴 비노출 처리 */}
                            {!isTablet && (
                                <Menu
                                    anchorEl={shareTypeMenuAnchor}
                                    open={Boolean(shareTypeMenuAnchor)}
                                    onClose={handleShareTypeMenuClose}
                                    anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
                                    transformOrigin={{ vertical: 'top', horizontal: 'left' }}
                                    PaperProps={{
                                        sx: {
                                            mt: 1,
                                            borderRadius: '4px',
                                            border: `1px solid ${palette.table_divider_nomal}`,
                                            boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
                                        },
                                    }}
                                    MenuListProps={{ sx: { p: '0px', pt: '4px' } }}
                                >
                                    {SHARE_TYPE_OPTIONS.filter(opt => {
                                        if (opt.value === SHARE_TYPES.RESTRICTED)
                                            return allowSpecificUsers;
                                        if (opt.value === SHARE_TYPES.INTERNAL) return allowOrgLink;
                                        if (opt.value === SHARE_TYPES.PUBLIC) return allowPublic;
                                        return false;
                                    }).map(opt => (
                                        <MenuItem
                                            key={opt.value}
                                            disabled={
                                                (!editable && opt.value !== shareType) ||
                                                (availableShareTypes.length > 0 &&
                                                    !availableShareTypes.includes(opt.value))
                                            }
                                            onClick={() => handleShareTypeSelect(opt.value)}
                                            sx={{
                                                px: 3,
                                                py: 2.5,
                                                gap: 3,
                                                alignItems: 'center',
                                                bgcolor:
                                                    shareType === opt.value
                                                        ? palette.table_bg_hover
                                                        : palette.white,
                                                '&:hover': {
                                                    bgcolor: palette.table_bg_hover,
                                                },
                                                '&.Mui-disabled': {
                                                    opacity: 0.4,
                                                },
                                            }}
                                        >
                                            <Box
                                                sx={{
                                                    display: 'flex',
                                                    alignItems: 'center',
                                                    gap: '10px',
                                                    width: 312,
                                                }}
                                            >
                                                <ShareTypeIcon type={opt.iconType} />
                                                <Stack spacing={1} sx={{ minWidth: 0 }}>
                                                    <Typography variant="body5" color="text_100">
                                                        {opt.label}
                                                    </Typography>
                                                    <Typography
                                                        variant="body9"
                                                        color="text_60"
                                                        sx={{ whiteSpace: 'normal' }}
                                                    >
                                                        {opt.desc}
                                                    </Typography>
                                                </Stack>
                                            </Box>
                                        </MenuItem>
                                    ))}
                                    {!editable && (
                                        <Box
                                            sx={{
                                                px: '16px',
                                                py: '10px',
                                                bgcolor: palette.bg_gray,
                                                borderTop: `1px solid ${palette.divider_8}`,
                                                display: 'flex',
                                                alignItems: 'center',
                                                gap: '20px',
                                            }}
                                        >
                                            <Typography
                                                variant="body9_10"
                                                color="text_60"
                                                sx={{ flex: '1 1 0', wordBreak: 'break-word' }}
                                            >
                                                {t(
                                                    'user.fileList.shareDialog.disabledShareTypeTooltip'
                                                )}
                                            </Typography>
                                        </Box>
                                    )}
                                </Menu>
                            )}

                            {/* 링크 권한 버튼 */}
                            {isTablet ? (
                                <StyledButton
                                    variant="text"
                                    disabled={!isShareCreator}
                                    onClick={() => setLinkAccessSheetOpen(true)}
                                    sx={{
                                        height: 28,
                                        p: '6px',
                                        borderRadius: '4px',
                                        gap: '3px',
                                        flexShrink: 0,
                                    }}
                                >
                                    <ExpandMore
                                        sx={{
                                            width: 20,
                                            height: 20,
                                            color: palette.text_60,
                                            opacity: isShareCreator ? 1 : 0.3,
                                        }}
                                    />
                                </StyledButton>
                            ) : (
                                shareType !== SHARE_TYPES.RESTRICTED && (
                                    <StyledButton
                                        variant="text"
                                        disabled={!isShareCreator}
                                        onClick={
                                            isTablet
                                                ? () => setLinkAccessSheetOpen(true)
                                                : handleLinkPermMenuOpen
                                        }
                                        sx={{
                                            height: 28,
                                            pt: '6px',
                                            pb: '6px',
                                            pl: '12px',
                                            pr: '8px',
                                            borderRadius: '4px',
                                            gap: '3px',
                                            flexShrink: 0,
                                        }}
                                    >
                                        <Typography variant="body5" color="text_80">
                                            {getLinkPermLabel(selectedLinkPermission)}
                                        </Typography>
                                        <ArrowFilledDown opacity={isShareCreator ? '.6' : '.3'} />
                                    </StyledButton>
                                )
                            )}

                            {/* 링크 권한 메뉴 (데스크톱) */}
                            {/** 공유 유형 API 응답 shareTypes 공유 타입별 권한 목록에 따라 링크 권한 메뉴 비노출 처리 */}
                            {!isTablet && shareType !== SHARE_TYPES.RESTRICTED && (
                                <Menu
                                    anchorEl={linkPermMenuAnchor}
                                    open={Boolean(linkPermMenuAnchor)}
                                    onClose={handleLinkPermMenuClose}
                                    anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                                    transformOrigin={{ vertical: 'top', horizontal: 'right' }}
                                    PaperProps={{
                                        sx: {
                                            mt: 1,
                                            borderRadius: '4px',
                                            boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
                                            outline: `1px ${palette.divider_12} solid`,
                                        },
                                    }}
                                    MenuListProps={{ sx: { py: '4px' } }}
                                >
                                    {permKeys
                                        .filter(key => {
                                            const allowed = shareTypePermissions[shareType];
                                            if (shareType === SHARE_TYPES.PUBLIC && key === 'ce')
                                                return true;
                                            return !allowed || allowed.includes(key);
                                        })
                                        .map(key => {
                                            const isDisabled = isCheckboxDisabled(key, shareType);
                                            const checked = !!selectedLinkPermission?.[key];
                                            return (
                                                <MenuItem
                                                    key={key}
                                                    disabled={isDisabled}
                                                    onClick={() =>
                                                        handleLinkPermChange(key, !checked)
                                                    }
                                                    sx={{
                                                        py: '6px',
                                                        pl: '10px',
                                                        pr: '48px',
                                                        minHeight: 'auto',
                                                        '&:hover': {
                                                            bgcolor: palette.table_bg_hover,
                                                        },
                                                    }}
                                                >
                                                    <Box
                                                        sx={{
                                                            display: 'flex',
                                                            alignItems: 'center',
                                                            gap: '10px',
                                                            width: 93,
                                                            height: 24,
                                                        }}
                                                    >
                                                        <Checkbox
                                                            size="small"
                                                            checked={checked}
                                                            onChange={e =>
                                                                handleLinkPermChange(
                                                                    key,
                                                                    e.target.checked
                                                                )
                                                            }
                                                            onClick={e => e.stopPropagation()}
                                                            disabled={isDisabled}
                                                            sx={{
                                                                p: '2px',
                                                                color: palette.text_30,
                                                                '&.Mui-checked': {
                                                                    color: palette.text_80,
                                                                },
                                                            }}
                                                        />
                                                        <Typography variant="body5" color="text_80">
                                                            {t(PERM_LABELS[key])}
                                                        </Typography>
                                                    </Box>
                                                </MenuItem>
                                            );
                                        })}
                                </Menu>
                            )}
                        </Box>
                    </Stack>
                </Stack>
            </DialogContent>

            {/* ── 버튼 영역 ── */}
            <DialogActions
                sx={{
                    position: { xs: 'fixed', tablet: 'static' },
                    bottom: { xs: 0, tablet: 'auto' },
                    left: { xs: 0, tablet: 'auto' },
                    right: { xs: 0, tablet: 'auto' },
                    zIndex: 1,
                    background: {
                        xs: 'linear-gradient(180deg, rgba(255,255,255,0) 0%, rgba(255,255,255,1) 20%)',
                        tablet: '#fff',
                    },
                    pt: { xs: '40px', tablet: '16px' },
                    pb: { xs: '24px', tablet: '0px' },
                    pl: { xs: '16px', tablet: '0px' },
                    pr: { xs: '16px', tablet: '0px' },
                    m: 0,
                }}
            >
                <Stack
                    direction={{
                        xs: 'column',
                        tablet: 'row',
                    }}
                    justifyContent="space-between"
                    alignItems="center"
                    sx={{ width: '100%', gap: { xs: 2, tablet: 0 } }}
                >
                    <Stack
                        direction={{
                            xs: 'row',
                            tablet: 'row',
                        }}
                        sx={{
                            width: '100%',
                            flexDirection: { xs: 'row-reverse', tablet: 'row' },
                            gap: 1.5,
                        }}
                    >
                        <StyledButton
                            variant="outlined"
                            color="btn_border"
                            onClick={handleCopyLink}
                            sx={{ width: { xs: '100%', tablet: 112 }, gap: 1 }}
                        >
                            <svg
                                width="22"
                                height="22"
                                viewBox="0 0 22 22"
                                fill="none"
                                xmlns="http://www.w3.org/2000/svg"
                            >
                                <g
                                    opacity=".8"
                                    stroke="#3a3d49"
                                    strokeWidth="1.5"
                                    strokeLinecap="round"
                                    strokeLinejoin="round"
                                >
                                    <path d="M13.292 7.18h2.292a3.82 3.82 0 0 1 0 7.639h-2.292m-4.583 0H6.417a3.818 3.818 0 1 1 0-7.64H8.71M7.945 11h6.111" />
                                </g>
                            </svg>
                            <Typography variant="body5" color="text_100">
                                {t('ShareDialog.copyLink')}
                            </Typography>
                        </StyledButton>
                        {/** 최초 공유 다이얼로그 오픈 시 / owner가 아닐 때 공유 해제 버튼 비노출 */}
                        {(item?.shareSeq || shareSeq) && isShareCreator && (
                            <StyledButton
                                variant="outlined"
                                color="btn_border"
                                onClick={handleUnshareResource}
                                sx={{
                                    width: { xs: '100%', tablet: 'auto' },
                                    minWidth: { xs: 0, tablet: 80 },
                                    px: '16px',
                                    height: 40,
                                }}
                            >
                                <Typography variant="body5" color="text_100">
                                    {t('ShareDialog.unshare')}
                                </Typography>
                            </StyledButton>
                        )}
                    </Stack>

                    <StyledButton
                        variant="contained"
                        color="btn_point_bg"
                        onClick={handleSave}
                        sx={{ minWidth: { xs: '100%', tablet: 83 }, height: 40 }}
                    >
                        <Typography variant="body5" color="white">
                            {t('common.button.confirm')}
                        </Typography>
                    </StyledButton>
                </Stack>
            </DialogActions>

            {/* 링크 접근 권한 바텀 시트 (태블릿 이하) */}
            {isTablet && (
                <Drawer
                    anchor="bottom"
                    open={linkAccessSheetOpen}
                    onClose={() => setLinkAccessSheetOpen(false)}
                    disablePortal
                    ModalProps={{ keepMounted: false }}
                    PaperProps={{
                        sx: {
                            borderRadius: '24px 24px 0 0',
                            outline: `1px ${palette.divider_8} solid`,
                            outlineOffset: '-1px',
                            pb: '60px',
                            gap: '8px',
                        },
                    }}
                >
                    {/* 드래그 핸들 */}
                    <Box
                        sx={{
                            px: '10px',
                            py: '8px',
                            display: 'flex',
                            flexDirection: 'column',
                            alignItems: 'center',
                            gap: '10px',
                        }}
                    >
                        <Box
                            sx={{
                                width: 44,
                                height: 4,
                                borderRadius: 2,
                                bgcolor: palette.divider_12,
                            }}
                        />
                    </Box>

                    {/* 헤더: 타이틀 + 닫기 버튼 */}
                    <Box
                        sx={{
                            pl: '48px',
                            pr: '16px',
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                        }}
                    >
                        <Typography
                            variant="body5"
                            color="text_100"
                            fontWeight={600}
                            sx={{ flex: 1, textAlign: 'center' }}
                        >
                            {t('user.fileList.shareDialog.permissionSheetTitle')}
                        </Typography>
                        <IconButton sx={{ p: '4px' }} onClick={() => setLinkAccessSheetOpen(false)}>
                            <Image
                                src="/common/icon-ic-close.svg"
                                width={24}
                                height={24}
                                alt="close"
                            />
                        </IconButton>
                    </Box>

                    {/* 콘텐츠 영역 */}
                    <Box
                        sx={{
                            pt: '4px',
                            display: 'flex',
                            flexDirection: 'column',
                            gap: '16px',
                        }}
                    >
                        {/* 접근 유형 섹션 */}
                        <Box
                            sx={{
                                px: '20px',
                                display: 'flex',
                                flexDirection: 'column',
                                gap: '12px',
                            }}
                        >
                            <Box sx={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                                {SHARE_TYPE_OPTIONS.filter(opt => {
                                    if (opt.value === SHARE_TYPES.RESTRICTED)
                                        return allowSpecificUsers;
                                    if (opt.value === SHARE_TYPES.INTERNAL) return allowOrgLink;
                                    if (opt.value === SHARE_TYPES.PUBLIC) return allowPublic;
                                    return false;
                                }).map(opt => {
                                    const isDisabled =
                                        !isShareCreator ||
                                        (!editable && opt.value !== shareType) ||
                                        (availableShareTypes.length > 0 &&
                                            !availableShareTypes.includes(opt.value));
                                    const isSelected = shareType === opt.value;
                                    return (
                                        <Box
                                            key={opt.value}
                                            onClick={() =>
                                                !isDisabled && handleShareTypeSelect(opt.value)
                                            }
                                            sx={{
                                                display: 'flex',
                                                justifyContent: 'flex-start',
                                                alignItems: 'center',
                                                gap: '16px',
                                                py: '8px',
                                                opacity: isDisabled ? 0.4 : 1,
                                                cursor: isDisabled ? 'default' : 'pointer',
                                            }}
                                        >
                                            <Box
                                                sx={{
                                                    flex: 1,
                                                    display: 'flex',
                                                    alignItems: 'center',
                                                    gap: '12px',
                                                }}
                                            >
                                                <Box sx={{ pt: '2px', alignSelf: 'flex-start' }}>
                                                    <ShareTypeIcon
                                                        type={opt.iconType}
                                                        boxSize={24}
                                                        isBottomSheet={true}
                                                    />
                                                </Box>
                                                <Box
                                                    sx={{
                                                        flex: 1,
                                                        display: 'flex',
                                                        flexDirection: 'column',
                                                        gap: '2px',
                                                    }}
                                                >
                                                    <Typography variant="body5" color="text_100">
                                                        {opt.label}
                                                    </Typography>
                                                    <Typography
                                                        variant="body9"
                                                        color="text_60"
                                                        sx={{ whiteSpace: 'normal' }}
                                                    >
                                                        {opt.desc}
                                                    </Typography>
                                                </Box>
                                            </Box>
                                            {/* 라디오 버튼 */}
                                            <Box
                                                sx={{
                                                    width: 24,
                                                    height: 24,
                                                    display: 'flex',
                                                    alignItems: 'center',
                                                    justifyContent: 'center',
                                                    flexShrink: 0,
                                                }}
                                            >
                                                <Box
                                                    sx={{
                                                        width: 18,
                                                        height: 18,
                                                        borderRadius: '50%',
                                                        border: `1.5px solid ${isSelected ? palette.text_80 : palette.divider_12}`,
                                                        display: 'flex',
                                                        alignItems: 'center',
                                                        justifyContent: 'center',
                                                    }}
                                                >
                                                    {isSelected && (
                                                        <Box
                                                            sx={{
                                                                width: 10,
                                                                height: 10,
                                                                borderRadius: '50%',
                                                                bgcolor: palette.text_80,
                                                            }}
                                                        />
                                                    )}
                                                </Box>
                                            </Box>
                                        </Box>
                                    );
                                })}
                            </Box>
                            {!editable && (
                                <Box
                                    sx={{
                                        px: '8px',
                                        py: '6px',
                                        bgcolor: palette.bg_subtle,
                                        borderRadius: '4px',
                                        display: 'flex',
                                        alignItems: 'flex-start',
                                        gap: '4px',
                                    }}
                                >
                                    <Image
                                        src="/common/share/icon-ic-share-Info.svg"
                                        alt="info"
                                        width={16}
                                        height={16}
                                    />
                                    <Typography
                                        variant="body9_10"
                                        color="text_60"
                                        sx={{ flex: '1 1 0', wordBreak: 'break-word' }}
                                    >
                                        {t('user.fileList.shareDialog.disabledShareTypeTooltip')}
                                    </Typography>
                                </Box>
                            )}
                        </Box>

                        {/* 구분선 */}
                        {shareType !== SHARE_TYPES.RESTRICTED && (
                            <Divider sx={{ borderColor: palette.divider_8 }} />
                        )}

                        {/* 권한 섹션 (RESTRICTED 아닐 때만) */}
                        {shareType !== SHARE_TYPES.RESTRICTED && (
                            <Box
                                sx={{
                                    px: '20px',
                                    display: 'flex',
                                    flexDirection: 'column',
                                    gap: '8px',
                                }}
                            >
                                <Box sx={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                                    {permKeys
                                        .filter(key => {
                                            const allowed = shareTypePermissions[shareType];
                                            if (shareType === SHARE_TYPES.PUBLIC && key === 'ce')
                                                return true;
                                            return !allowed || allowed.includes(key);
                                        })
                                        .map(key => {
                                            const isDisabled =
                                                !isShareCreator ||
                                                isCheckboxDisabled(key, shareType);
                                            const checked = !!selectedLinkPermission?.[key];
                                            return (
                                                <Box
                                                    key={key}
                                                    onClick={() =>
                                                        !isDisabled &&
                                                        handleLinkPermChange(key, !checked)
                                                    }
                                                    sx={{
                                                        display: 'flex',
                                                        justifyContent: 'space-between',
                                                        alignItems: 'center',
                                                        py: '6px',
                                                        opacity: isDisabled ? 0.4 : 1,
                                                        cursor: isDisabled ? 'default' : 'pointer',
                                                    }}
                                                >
                                                    <Typography variant="body5" color="text_100">
                                                        {t(PERM_LABELS[key])}
                                                    </Typography>
                                                    <Checkbox
                                                        size="small"
                                                        checked={checked}
                                                        onChange={e =>
                                                            handleLinkPermChange(
                                                                key,
                                                                e.target.checked
                                                            )
                                                        }
                                                        onClick={e => e.stopPropagation()}
                                                        disabled={isDisabled}
                                                        sx={{
                                                            p: '2px',
                                                            color: palette.text_30,
                                                            '&.Mui-checked': {
                                                                color: palette.text_80,
                                                            },
                                                        }}
                                                    />
                                                </Box>
                                            );
                                        })}
                                </Box>
                            </Box>
                        )}
                    </Box>
                </Drawer>
            )}
        </Dialog>
    );
}

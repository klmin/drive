import api from '../utils/api';
import { useEffect, useRef, useState } from 'react';
import { isLoggedIn, clearAuth, inferRoleFromPath, saveAuth } from '@/utils/authTokens';
import { useRouter } from '@/hooks/useRouter';
// 아이디 : testuser1@thinkfree.com
//
// 비밀번호 : testuser1!

/**
 * 현재 경로 역할의 사용자와 그 결과를 확인한 경로 — probe 면 로그인 표시가 없어도 users 로 한 번 확인하고,
 * 성공하면 표시를 되살린다
 */
export default function useAuth({ probe = false } = {}) {
    const router = useRouter();
    const [auth, setAuth] = useState({ user: undefined, checkedPath: null });

    // 마지막으로 users를 호출했던 role 기억 -> 달라지면 users api 재호출하도록
    const lastAuthRef = useRef({ role: null });
    // 응답 전에 경로가 바뀌면 이전 요청 결과를 버리기 위한 순번
    const requestSeqRef = useRef(0);

    useEffect(() => {
        if (!router.isReady) return;

        const role = inferRoleFromPath();
        const marked = isLoggedIn(role);
        const path = router.asPath;

        if (!marked && !probe) {
            lastAuthRef.current = { role: null };
            requestSeqRef.current += 1;
            setAuth({ user: null, checkedPath: path });
            return;
        }

        // 같은 role 로그인이면 users 재호출x (페이지 단순 이동 시 users 재호출방지)
        if (marked && lastAuthRef.current.role === role) return;

        lastAuthRef.current = { role };
        const seq = ++requestSeqRef.current;
        const isStale = () => seq !== requestSeqRef.current;
        const settle = user => setAuth({ user, checkedPath: path });
        // 표시 없이 확인하는 동안 가드가 로그인 화면으로 보내지 않도록 결과 전 상태로
        if (!marked) setAuth({ user: undefined, checkedPath: null });

        (async () => {
            try {
                const res = await api.Get('users', {}, null, {
                    __roleType: role,
                    __probe: !marked,
                });
                // 응답 전에 경로가 바뀌었으면 쿠키·상태를 건드리지 않음
                if (isStale()) return;
                console.log('useAuth 호출');

                if (res.status === 401) {
                    settle(null);
                    clearAuth(role);
                    return;
                }
                const found = res?.data?.data ?? null;
                if (!marked) {
                    if (found) saveAuth(role, found);
                    else lastAuthRef.current = { role: null };
                }
                settle(found);
            } catch (e) {
                if (!isStale()) settle(null);
            }
        })();
    }, [router.isReady, router.asPath]);
    // route 변경 시 useEfrect 실행, role 로그인 변경 시에만 users 재호출

    return [auth.user, auth.checkedPath];
}

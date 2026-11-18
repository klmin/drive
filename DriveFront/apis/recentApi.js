import api from '@/utils/api';
import { inferRoleFromPath } from '@/utils/authTokens';

/** 최근 문서함 등록 — 성공 여부를 돌려준다(익명은 등록하지 않고 성공 처리) */
export const registerRecent = async (resourceSeq, ss = '') => {
    if (inferRoleFromPath() === 'ANONYMOUS') return true;
    try {
        const res = await api.Post('recent', {
            rs: resourceSeq,
            ss: ss,
        });

        return res?.code === 200;
    } catch (error) {
        console.error('registerRecent', error);
        return false;
    }
};

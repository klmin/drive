import api from '@/utils/api';

// 리소스 상세 정보
export const getResourceDetail = async (resourceSeq, { ss, d } = {}) => {
    try {
        const res = await api.Get(`resources/${resourceSeq}`, {
            // d: 휴지통 리소스 조회
            params: { ss: ss || undefined, d: d || undefined },
        });
        if (res?.status === 200) return res.data.data;
    } catch (e) {
        console.error('Failed to fetch resource detail', e);
    }
};

// 현재 폴더의 부모 폴더 리스트
export const getParentFolderInfoList = async (resourceSeq, ss) => {
    let folderList = [];
    try {
        if (resourceSeq) {
            const res = await api.Get(`resources/${resourceSeq}`, {
                params: {
                    ss: ss || '',
                },
            });

            if (res?.status === 200) {
                const data = res?.data?.data;
                console.log('폴더의 부모 폴더 리스트 가져오기 성공: ', data);

                const {
                    fullPath = '',
                    fullPathSeq = '',
                    resourceSeq,
                    resourceName,
                    userSeq,
                } = data;

                const seqList = (fullPathSeq || '').split('/').filter(Boolean).concat(resourceSeq);
                const nameList = (fullPath || '').split('/').filter(Boolean).concat(resourceName);
                folderList = seqList.map((seq, idx) => ({
                    resourceSeq: seq,
                    resourceName: nameList[idx],
                    ss,
                    userSeq,
                }));
            }
        }
    } catch (err) {
        console.log('폴더의 부모 폴더 리스트 가져오기 실패: ', err);
    } finally {
        return folderList;
    }
};

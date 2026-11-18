import api from '@/utils/api';

export default async function checkExists(item, pfs, onClick = () => {}) {
    const res = await api.Get(`resources/exists`, {
        params: {
            ss: item?.ss || item?.sharedByUserSeq || item?.userSeq,
            pfs: pfs || undefined,
            rn: item?.resourceName || undefined,
            rt: item?.resourceType,
        },
    });

    // 중복 아님 ex) {"result":true,"code":200,"message":"OK"}
    // 중복 ex) {"result":true,"code":200,"message":"OK","data":{"resourceSeq":37432}}

    const resourceSeq = res?.data?.data?.resourceSeq;
    // console.log('res?.data', res?.data);

    if (res?.data?.code === 200 && !resourceSeq) {
        // 중복 조회 통과 -> 바로 업로드 가능
        if (onClick) onClick();
        return true;
    } else if (res?.data?.code === 200 && resourceSeq) {
        // 중복 이름 존재하면 그 resourceSeq 전달
        return { resourceSeq };
    } else {
        // 기타 오류
        return false;
    }
}

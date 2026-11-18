// hooks/useStorageInfo.js
import { useEffect, useState } from 'react';
import api from '@/utils/api';
import { useSnackbarContext } from '@/provider/SnackbarProvider';

export const useStorageInfo = () => {
    const [storageInfo, setStorageInfo] = useState(null);
    const [isCeph, setIsCeph] = useState(false);
    const { showSnackbar } = useSnackbarContext();

    useEffect(() => {
        let mounted = true;

        const fetchStorageInfo = async () => {
            try {
                const res = await api.Get('admins/system-config/storage', {});

                if (!mounted) return;

                if (res?.status === 200) {
                    const data = res?.data?.data;

                    setStorageInfo({
                        storageType: data?.storageType || '',
                        accessKey: data?.accessKey || '',
                        secretKey: data?.secretKey,
                        bucketName: data?.bucketName || '',
                        region: data?.region || '',
                        endPoint: data?.endPoint || '',
                        status: data?.status ? 'OK' : 'FAIL',
                    });

                    setIsCeph(data?.storageType === 'CEPH');
                }
            } catch (err) {
                console.log(err);
            }
        };

        fetchStorageInfo();

        return () => {
            mounted = false;
        };
    }, []);

    return {
        storageInfo,
        isCeph,
    };
};

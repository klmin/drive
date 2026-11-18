import { createContext, useState, useCallback, useContext, useEffect } from 'react';
import api from '@/utils/api';
import { isAdminArea } from '@/utils/authTokens';

const UserProfileContext = createContext(null);

export function UserProfileProvider({ children }) {
    const [previewUrl, setPreviewUrl] = useState('/common/img-img-user.svg');
    const [isFetched, setIsFetched] = useState(false);

    const fetchProfileImage = useCallback(async () => {
        // 관리자 화면 아바타는 users/{userSeq}/profile-image(공개 URL)를 쓴다
        if (isAdminArea()) return;
        try {
            const res = await api.Get('users/profile-image', {}, null, {
                responseType: 'blob',
            });

            if (res?.status === 200) {
                if (res.data.size !== 0) {
                    setIsFetched(true);

                    // 이전 Blob URL 정리
                    if (previewUrl && previewUrl.startsWith('blob:')) {
                        URL.revokeObjectURL(previewUrl);
                    }

                    const newPreviewUrl = URL.createObjectURL(res.data);
                    setPreviewUrl(newPreviewUrl);
                }
            }
        } catch (error) {
            console.error('프로필 이미지 로딩 실패:', error);
        } finally {
            setIsFetched(false);
        }
    }, []);

    useEffect(() => {
        if (isFetched || previewUrl !== '/common/img-img-user.svg') return;

        fetchProfileImage();

        return () => {
            if (previewUrl && previewUrl.startsWith('blob:')) {
                URL.revokeObjectURL(previewUrl);
            }
        };
    }, [isFetched, previewUrl, fetchProfileImage]);

    const refreshProfileImage = useCallback(() => {
        if (previewUrl && previewUrl.startsWith('blob:')) {
            URL.revokeObjectURL(previewUrl);
        }

        setPreviewUrl('/common/img-img-user.svg');
        setIsFetched(false);
    }, [previewUrl]);

    const value = { previewUrl, refreshProfileImage };

    return <UserProfileContext.Provider value={value}>{children}</UserProfileContext.Provider>;
}

export function useUserProfile() {
    const context = useContext(UserProfileContext);
    if (!context) {
        throw new Error('UserProfileProvider 안에서 사용해야 합니다.');
    }
    return context;
}

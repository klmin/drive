import Image from 'next/image';
import { useCallback, useEffect, useState } from 'react';

const PLACEHOLDER = '/common/img-img-user.svg';

export default function ProfileImage({ src, alt, width, height, fill, priority }) {
    const normalizedSrc = src || '';

    const [imgSrc, setImgSrc] = useState(src || '');
    const [isVisible, setIsVisible] = useState(false); // 로드 완료 전까지 숨김

    // src가 진짜 바뀔 때만 초기화
    useEffect(() => {
        setImgSrc(prev => (prev === normalizedSrc ? prev : normalizedSrc));
        setIsVisible(prev => (prev === false ? prev : false)); // 이미 false면 set 안 함
    }, [normalizedSrc]);

    const handleLoad = useCallback(() => {
        setIsVisible(prev => (prev ? prev : true)); //  이미 true면 set 안 함
    }, []);

    const handleError = useCallback(() => {
        setImgSrc(prev => (prev === PLACEHOLDER ? prev : PLACEHOLDER));
        setIsVisible(true);
    }, []);

    const finalSrc = imgSrc || PLACEHOLDER;

    return (
        <div
            style={{
                position: fill ? 'relative' : 'static',
                borderRadius: '50%',
                overflow: 'hidden',
                width: fill ? '100%' : width || 24,
                height: fill ? '100%' : height || 24,
                flexShrink: 0,
            }}
        >
            <Image
                src={finalSrc}
                alt={alt || '프로필 이미지'}
                onLoad={handleLoad}
                onError={handleError}
                priority={priority}
                {...(fill ? { fill: true } : { width: width || 24, height: height || 24 })}
                style={{
                    opacity: isVisible ? 1 : 0,
                    transition: 'opacity 0.2s ease-in-out',
                    objectFit: 'cover',
                }}
            />
        </div>
    );
}

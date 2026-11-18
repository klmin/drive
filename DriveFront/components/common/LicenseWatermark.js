import Box from '@mui/material/Box';
import Image from 'next/image';
import useBrandingConfig from '@/hooks/useBrandingConfig';
import { LICENSE_PLAN } from '@/utils/license';

// 플랜별 워터마크 (md 미만은 높이 22px 비율로 축소), 그 외 플랜·미등록·조회 전은 미노출
const WATERMARK_BY_PLAN = {
    [LICENSE_PLAN.TRIAL]: {
        src: '/watermark/img-watermark-trial.svg',
        width: 180,
        height: 24,
        mobileWidth: 165,
    },
    [LICENSE_PLAN.DEVELOPER]: {
        src: '/watermark/img-watermark-developer.svg',
        width: 310,
        height: 24,
        mobileWidth: 284,
    },
};

// 기본 배치 (md 이상 / md 미만) — right 는 페이지 본문 좌우 패딩(px: { md: 12, xs: 4 })에 맞춘다
const DEFAULT_OFFSET = { right: 48, bottom: 24 };
const DEFAULT_MOBILE_OFFSET = { right: 16, bottom: 20 };

// 숫자는 px, 문자열은 CSS 값 그대로
const toCss = value => (typeof value === 'number' ? `${value}px` : value);
// 공간이 모자라면 좌우 여백(left 미지정 시 right 와 동일)을 남기고 비례 축소
const toMaxWidth = ({ right, left = right }) => `calc(100% - ${toCss(right)} - ${toCss(left)})`;

/**
 * 라이선스 워터마크 — 화면 우하단 최상위 고정, 클릭은 아래 콘텐츠로 통과
 *
 * @param offset       md 이상 배치 {right, bottom}(px 또는 CSS 값), right 는 창 오른쪽 끝부터 본문 패딩 경계까지
 * @param mobileOffset md 미만 배치 {right, bottom, left?}(px 또는 CSS 값), left 는 축소 시 남길 왼쪽 여백
 * @param zIndex       기본값은 MUI tooltip 위, 자체 z-index 가 높은 화면(미리보기 등)에서 지정
 */
const LicenseWatermark = ({
    offset = DEFAULT_OFFSET,
    mobileOffset = DEFAULT_MOBILE_OFFSET,
    zIndex,
}) => {
    const { plan } = useBrandingConfig();
    const watermark = WATERMARK_BY_PLAN[plan];
    if (!watermark) return null;

    return (
        <Box
            sx={{
                position: 'fixed',
                right: { xs: toCss(mobileOffset.right), md: toCss(offset.right) },
                bottom: { xs: toCss(mobileOffset.bottom), md: toCss(offset.bottom) },
                width: { xs: watermark.mobileWidth, md: watermark.width },
                maxWidth: { xs: toMaxWidth(mobileOffset), md: toMaxWidth(offset) },
                zIndex: zIndex ?? (theme => theme.zIndex.tooltip + 1),
                pointerEvents: 'none',
                userSelect: 'none',
                lineHeight: 0,
            }}
        >
            <Image
                src={watermark.src}
                alt=""
                width={watermark.width}
                height={watermark.height}
                draggable={false}
                style={{ width: '100%', height: 'auto' }}
            />
        </Box>
    );
};

export default LicenseWatermark;

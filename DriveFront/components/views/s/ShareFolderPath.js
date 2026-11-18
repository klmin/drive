import { Box, MenuItem, Stack, Menu, Typography } from '@mui/material';
import StyledButton from '@/components/common/button/StyledButton';
import Image from 'next/image';
import { getFile } from '@/constants/FILES';
import React, { useEffect, useRef, useState } from 'react';
import { useTranslations } from 'next-intl';
import palette from '@/theme/palette';
import { BaseTooltip } from '@/components/common/tooltip/BaseTooltip';

/**
 * 공유 폴더 전용 breadcrumb 경로 컴포넌트.
 * Path.js의 UI 구조를 기반으로 하되, 경로 데이터는 API 조회 없이
 * 부모(pages/s/folder)가 관리하는 pathStack으로 구동한다.
 *
 * @param {ReactNode} img        - 좌측 공유 아이콘
 * @param {Array}     pathStack  - [{ resourceSeq, resourceName }, ...] 루트 → 현재 폴더 순
 * @param {Function}  onItemClick(item, index) - breadcrumb 항목 클릭 콜백 (마지막 항목 제외)
 */
export default function ShareFolderPath({ img, pathStack = [], onItemClick }) {
    const t = useTranslations();
    const [anchorEl, setAnchorEl] = useState(null);

    // 화면 크기별 최대 표시 개수. 초과분은 "..." 메뉴로 접힘
    const MAX_VISIBLE_MD = 4;
    const MAX_VISIBLE_XS = 1;
    const total = pathStack.length;

    // md 기준: 앞쪽 항목들을 "..." 메뉴에, 나머지를 직접 표시
    const isTruncatedMd = total > MAX_VISIBLE_MD;
    const truncatedItemsMd = isTruncatedMd ? pathStack.slice(0, total - MAX_VISIBLE_MD) : [];
    const visibleItemsMd = isTruncatedMd ? pathStack.slice(total - MAX_VISIBLE_MD) : pathStack;

    // xs 기준: 동일 원리, 더 공격적으로 접힘
    const isTruncatedXs = total > MAX_VISIBLE_XS;
    const truncatedItemsXs = isTruncatedXs ? pathStack.slice(0, total - MAX_VISIBLE_XS) : [];
    const visibleItemsXs = isTruncatedXs ? pathStack.slice(total - MAX_VISIBLE_XS) : pathStack;

    const handleMenuClose = () => setAnchorEl(null);

    /**
     * 텍스트가 잘릴 때만 tooltip을 표시하는 메뉴 아이템 텍스트.
     * offsetWidth < scrollWidth 로 실제 잘림 여부를 판단한다.
     */
    const EllipsisMenuText = ({ text }) => {
        const textRef = useRef(null);
        const [isTruncated, setIsTruncated] = useState(false);

        useEffect(() => {
            if (!textRef.current) return;
            setIsTruncated(textRef.current.offsetWidth < textRef.current.scrollWidth);
        }, [text]);

        const content = (
            <Typography
                ref={textRef}
                variant="body7"
                color={palette.text_80}
                noWrap
                sx={{ maxWidth: '100%', overflow: 'hidden', textOverflow: 'ellipsis' }}
            >
                {text}
            </Typography>
        );
        return isTruncated ? <BaseTooltip title={text}>{content}</BaseTooltip> : content;
    };

    /**
     * 실제로 텍스트가 잘릴 때만 tooltip을 표시하는 breadcrumb 텍스트.
     * ref로 offsetWidth < scrollWidth 를 감지해 잘림 여부를 판단한다.
     */
    const BreadcrumbText = ({ item, isLast, stackIndex }) => {
        const textRef = useRef(null);
        const [isTruncated, setIsTruncated] = useState(false);

        useEffect(() => {
            if (!textRef.current) return;
            setIsTruncated(textRef.current.offsetWidth < textRef.current.scrollWidth);
        }, [item.resourceName]);

        const text = (
            <Typography
                ref={textRef}
                component="span"
                onClick={() => !isLast && onItemClick?.(item, stackIndex)}
                sx={{
                    maxWidth: 140,
                    display: 'block',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                    cursor: isLast ? 'default' : 'pointer',
                    '&:hover': { textDecoration: isLast ? 'none' : 'underline' },
                }}
                variant={isLast ? 'subtitle3' : 'body7'}
                color="text_80"
            >
                {item.resourceName}
            </Typography>
        );

        return isTruncated ? <BaseTooltip title={item.resourceName}>{text}</BaseTooltip> : text;
    };

    /**
     * 개별 breadcrumb 항목 렌더링.
     * - isFirstVisible: truncate 없이 첫 번째로 표시되는 경우 "/" 생략
     * - isLast: 현재 위치이므로 클릭 불가, 굵은 스타일 적용
     */
    const renderItem = (item, indexInVisible, isFirstVisible) => {
        const stackIndex = pathStack.indexOf(item); // pathStack 전체 기준 인덱스
        const isLast = stackIndex === pathStack.length - 1;

        return (
            <Stack
                key={item.resourceSeq}
                direction="row"
                gap={1.5}
                alignItems="center"
                sx={{ minWidth: 0 }}
            >
                {/* 첫 번째 표시 항목이고 truncate가 없을 때만 "/" 생략 */}
                {!isFirstVisible && (
                    <Typography variant="body9" color="text_40">
                        /
                    </Typography>
                )}
                <Image src={getFile('folder')?.src} alt="folder" width={20} height={20} />
                <BreadcrumbText item={item} isLast={isLast} stackIndex={stackIndex} />
            </Stack>
        );
    };

    /**
     * "..." 축약 버튼 + 드롭다운 메뉴 렌더링.
     * truncate가 있을 때만 호출되므로, 이어지는 visibleItems는 항상 "/" 를 앞에 붙인다.
     */
    const renderEllipsis = truncatedItems => (
        <>
            <BaseTooltip title={t('tooltip.showPath')}>
                <StyledButton
                    variant="text"
                    sx={{ p: 0.5, lineHeight: 1 }}
                    onClick={e => setAnchorEl(e.currentTarget)}
                >
                    <Typography variant="subtitle3" color="text_80">
                        ...
                    </Typography>
                </StyledButton>
            </BaseTooltip>
            <Menu
                anchorEl={anchorEl}
                open={Boolean(anchorEl)}
                onClose={handleMenuClose}
                PaperProps={{
                    sx: {
                        maxWidth: '288px !important',
                        mt: 1,
                        minWidth: 125,
                        boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
                    },
                }}
            >
                {truncatedItems.map(item => {
                    const index = pathStack.indexOf(item);
                    return (
                        <MenuItem
                            key={item.resourceSeq}
                            sx={{ width: '100%' }}
                            onClick={() => {
                                onItemClick?.(item, index);
                                handleMenuClose();
                            }}
                        >
                            <EllipsisMenuText text={item.resourceName} />
                        </MenuItem>
                    );
                })}
            </Menu>
        </>
    );

    return (
        <Stack
            direction="row"
            gap={1.5}
            alignItems="center"
            flexWrap="nowrap"
            sx={{ width: '100%', overflow: 'hidden' }}
        >
            {/* 공유 아이콘 */}
            <Box
                sx={{
                    position: 'relative',
                    width: { xs: 20, md: 24 },
                    height: { xs: 20, md: 24 },
                    flexShrink: 0,
                }}
            >
                {img}
            </Box>

            {/* breadcrumb — md 이상 */}
            <Stack
                direction="row"
                alignItems="center"
                gap={1.5}
                sx={{ display: { xs: 'none', md: 'flex' }, minWidth: 0 }}
            >
                {/* truncate된 항목이 있으면 "..." 버튼 표시, 이후 visibleItems는 "/" 붙음 */}
                {isTruncatedMd && renderEllipsis(truncatedItemsMd)}
                {visibleItemsMd.map((item, i) => renderItem(item, i, !isTruncatedMd && i === 0))}
            </Stack>

            {/* breadcrumb — xs (모바일) */}
            <Stack
                direction="row"
                alignItems="center"
                gap={1.5}
                sx={{ display: { xs: 'flex', md: 'none' }, minWidth: 0 }}
            >
                {isTruncatedXs && renderEllipsis(truncatedItemsXs)}
                {visibleItemsXs.map((item, i) => renderItem(item, i, !isTruncatedXs && i === 0))}
            </Stack>
        </Stack>
    );
}

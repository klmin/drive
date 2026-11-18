import { Box, ListItemText, MenuItem, Stack, Menu, Typography } from '@mui/material';
import StyledButton from '@/components/common/button/StyledButton';
import Image from 'next/image';
import { getFile } from '@/constants/FILES';
import React, { useMemo, useState, useRef, useEffect } from 'react';
import { useTranslations } from 'next-intl';
import palette from '@/theme/palette';
import { BaseTooltip } from '../common/tooltip/BaseTooltip';

const PathItem = ({ item, resources, handlePathClick, maxWidth }) => {
    const isLastItem = resources[resources.length - 1].resourceSeq === item.resourceSeq;
    const typographyRef = useRef(null);
    const [isTruncated, setIsTruncated] = useState(false);

    useEffect(() => {
        if (typographyRef.current) {
            setIsTruncated(typographyRef.current.offsetWidth < typographyRef.current.scrollWidth);
        }
    }, [item]);

    const typographyStyle = {
        ...(maxWidth !== undefined ? { maxWidth } : { flex: 1, minWidth: 0 }),
        cursor: isLastItem ? 'default' : 'pointer',
        '&:hover': { textDecoration: isLastItem ? 'none' : 'underline' },
    };

    return (
        <Stack
            key={item.resourceSeq}
            direction={'row'}
            gap={1.5}
            alignItems={'center'}
            sx={{ minWidth: 0 }}
        >
            <Typography variant={'body9'} color={'text_40'}>
                /
            </Typography>
            <Image src={getFile('folder')?.src} alt={'folder'} width={20} height={20} />
            <BaseTooltip title={item.resourceName} disableHoverListener={!isTruncated}>
                <Typography
                    ref={typographyRef}
                    onClick={() => handlePathClick(item)}
                    sx={typographyStyle}
                    variant={isLastItem ? 'subtitle3' : 'body7'}
                    color={'text_80'}
                    noWrap
                >
                    {item.resourceName}
                </Typography>
            </BaseTooltip>
        </Stack>
    );
};

const EllipsisMenuText = ({ text }) => {
    const textRef = useRef(null);
    const [isTruncated, setIsTruncated] = useState(false);

    useEffect(() => {
        if (!textRef.current) return;
        const el = textRef.current;
        console.log(el.offsetWidth < el.scrollWidth);
        setIsTruncated(el.offsetWidth < el.scrollWidth);
    }, [text]);

    const content = (
        <Typography
            ref={textRef}
            variant="body7"
            color={palette.text_80}
            noWrap
            sx={{
                maxWidth: '100%', // MenuItem 기준
                overflow: 'hidden',
                textOverflow: 'ellipsis',
            }}
        >
            {text}
        </Typography>
    );

    return isTruncated ? (
        <BaseTooltip title={text} disableInteractive>
            {content}
        </BaseTooltip>
    ) : (
        content
    );
};

export default function DialogPath({ resources, setPfs, setPath }) {
    const t = useTranslations();
    const [anchorElMd, setAnchorElMd] = useState(null);
    const [anchorElXs, setAnchorElXs] = useState(null);

    const {
        isTruncatedMd,
        truncatedItemsMd,
        visibleItemsMd,
        isTruncatedXs,
        truncatedItemsXs,
        visibleItemsXs,
    } = useMemo(() => {
        const totalLength = resources.length;
        const MAX_VISIBLE_MD = 3;
        const MAX_VISIBLE_XS = 1;

        const isTruncatedMd = totalLength > MAX_VISIBLE_MD;
        const truncatedItemsMd = isTruncatedMd
            ? resources.slice(0, totalLength - MAX_VISIBLE_MD)
            : [];
        const visibleItemsMd = isTruncatedMd
            ? resources.slice(totalLength - MAX_VISIBLE_MD)
            : resources;

        const isTruncatedXs = totalLength > MAX_VISIBLE_XS;
        const truncatedItemsXs = isTruncatedXs
            ? resources.slice(0, totalLength - MAX_VISIBLE_XS)
            : [];
        const visibleItemsXs = isTruncatedXs
            ? resources.slice(totalLength - MAX_VISIBLE_XS)
            : resources;

        return {
            isTruncatedMd,
            truncatedItemsMd,
            visibleItemsMd,
            isTruncatedXs,
            truncatedItemsXs,
            visibleItemsXs,
        };
    }, [resources]);

    const handleMenuCloseMd = () => setAnchorElMd(null);
    const handleMenuCloseXs = () => setAnchorElXs(null);

    const handlePathClick = clickedItem => {
        const originalIndex = resources.findIndex(
            item => item.resourceSeq === clickedItem.resourceSeq
        );
        if (originalIndex > -1) {
            setPath(prev => prev.slice(0, originalIndex + 1));
            setPfs(clickedItem.resourceSeq);
        }
    };

    if (resources.length === 0) {
        return null;
    }

    return (
        <Stack
            direction={'row'}
            gap={1.5}
            sx={{ width: '100%', overflow: 'hidden' }}
            alignItems={'center'}
            flexWrap={'nowrap'}
        >
            <Box display="flex" alignItems="center" sx={{ minWidth: 0, overflow: 'hidden' }}>
                <Stack
                    direction="row"
                    alignItems="center"
                    gap={1.5}
                    sx={{ display: { xs: 'none', tablet: 'flex' } }}
                >
                    {isTruncatedMd && (
                        <>
                            <BaseTooltip title={t('tooltip.showPath')}>
                                <StyledButton
                                    variant={'text'}
                                    sx={{ p: 0.5, lineHeight: 1 }}
                                    onClick={e => setAnchorElMd(e.currentTarget)}
                                >
                                    <Typography variant={'subtitle3'} color={'text_80'}>
                                        ...
                                    </Typography>
                                </StyledButton>
                            </BaseTooltip>
                            <Menu
                                anchorEl={anchorElMd}
                                open={Boolean(anchorElMd)}
                                onClose={handleMenuCloseMd}
                                PaperProps={{
                                    sx: {
                                        display: { xs: 'none', tablet: 'flex' },
                                        maxWidth: '288px !important',
                                        mt: 1,
                                        minWidth: 125,
                                        boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
                                    },
                                }}
                                onClick={() => console.log('Menu 클릭')}
                            >
                                {truncatedItemsMd.map(item => (
                                    <MenuItem
                                        key={item.resourceSeq}
                                        sx={{
                                            maxWidth: '288px !important',
                                        }}
                                        onClick={() => {
                                            handlePathClick(item);
                                            handleMenuCloseMd();
                                        }}
                                    >
                                        <EllipsisMenuText text={item.resourceName} />
                                    </MenuItem>
                                ))}
                            </Menu>
                        </>
                    )}
                    {visibleItemsMd.map(item => (
                        <PathItem
                            key={item.resourceSeq}
                            item={item}
                            resources={resources}
                            handlePathClick={handlePathClick}
                            maxWidth={visibleItemsMd.length > 1 ? 140 : 'min-content'}
                        />
                    ))}
                </Stack>

                <Stack
                    direction="row"
                    alignItems="center"
                    gap={1.5}
                    sx={{ display: { xs: 'flex', tablet: 'none' }, flex: 1, minWidth: 0 }}
                >
                    {isTruncatedXs && (
                        <>
                            <BaseTooltip title={t('tooltip.showPath')}>
                                <StyledButton
                                    variant={'text'}
                                    sx={{ p: 0.5, lineHeight: 1 }}
                                    onClick={e => setAnchorElXs(e.currentTarget)}
                                >
                                    <Typography variant={'subtitle3'} color={'text_80'}>
                                        ...
                                    </Typography>
                                </StyledButton>
                            </BaseTooltip>
                            <Menu
                                anchorEl={anchorElXs}
                                open={Boolean(anchorElXs)}
                                onClose={handleMenuCloseXs}
                                PaperProps={{
                                    sx: {
                                        display: { xs: 'flex', tablet: 'none' },
                                        maxWidth: '288px !important',
                                        mt: 1,
                                        minWidth: 125,
                                        boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
                                    },
                                }}
                            >
                                {truncatedItemsXs.map(item => (
                                    <MenuItem
                                        key={item.resourceSeq}
                                        sx={{
                                            maxWidth: '288px !important',
                                        }}
                                        onClick={() => {
                                            handlePathClick(item);
                                            handleMenuCloseXs();
                                        }}
                                    >
                                        <EllipsisMenuText text={item.resourceName} />
                                    </MenuItem>
                                ))}
                            </Menu>
                        </>
                    )}
                    {visibleItemsXs.map(item => (
                        <PathItem
                            key={item.resourceSeq}
                            item={item}
                            resources={resources}
                            handlePathClick={handlePathClick}
                        />
                    ))}
                </Stack>
            </Box>
        </Stack>
    );
}

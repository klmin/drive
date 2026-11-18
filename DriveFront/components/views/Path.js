import { Box, ListItemText, MenuItem, Stack, Menu, Typography } from '@mui/material';
import StyledButton from '@/components/common/button/StyledButton';
import Image from 'next/image';
import { getFile } from '@/constants/FILES';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslations } from 'next-intl';
import palette from '@/theme/palette';
import { useRouter } from '@/hooks/useRouter';
import { BaseTooltip } from '../common/tooltip/BaseTooltip';

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

    return isTruncated ? <BaseTooltip title={text}>{content}</BaseTooltip> : content;
};

export default function Path({
    title,
    pathName,
    img,
    resource,
    shareType = '',
    titleAreaRef = null,
}) {
    const t = useTranslations();
    const router = useRouter();
    const [anchorEl, setAnchorEl] = useState(null);
    const isORG_LINK = shareType === 'ORG_LINK';

    const fullPath = useMemo(() => {
        if (!router.query.pfs || !resource) return [];

        const basePath = resource?.fullPath?.split('/').filter(f => f) ?? [];
        const basePathSeq = resource?.fullPathSeq?.split('/').filter(f => f) ?? [];

        const result = basePath.map((path, index) => ({
            path,
            seq: basePathSeq[index],
        }));

        result.push({
            path: resource?.resourceName,
            seq: resource?.resourceSeq,
            isLast: true,
        });

        return result;
    }, [resource, router.query.pfs]);

    const {
        isTruncatedMd,
        truncatedItemsMd,
        visibleItemsMd,
        isTruncatedXs,
        truncatedItemsXs,
        visibleItemsXs,
    } = useMemo(() => {
        const totalLength = fullPath.length;
        const MAX_VISIBLE_MD = 4;
        const MAX_VISIBLE_XS = 1;

        const isTruncatedMd = totalLength > MAX_VISIBLE_MD;
        const truncatedItemsMd = isTruncatedMd
            ? fullPath.slice(0, totalLength - MAX_VISIBLE_MD)
            : [];
        const visibleItemsMd = isTruncatedMd
            ? fullPath.slice(totalLength - MAX_VISIBLE_MD)
            : fullPath;

        const isTruncatedXs = totalLength > MAX_VISIBLE_XS;
        const truncatedItemsXs = isTruncatedXs
            ? fullPath.slice(0, totalLength - MAX_VISIBLE_XS)
            : [];
        const visibleItemsXs = isTruncatedXs
            ? fullPath.slice(totalLength - MAX_VISIBLE_XS)
            : fullPath;

        return {
            isTruncatedMd,
            truncatedItemsMd,
            visibleItemsMd,
            isTruncatedXs,
            truncatedItemsXs,
            visibleItemsXs,
        };
    }, [fullPath]);

    const handleMenuClose = () => {
        setAnchorEl(null);
    };

    const handlePathClick = item => {
        let url = resource.userSeq
            ? `${router.pathname}?pfs=${item.seq}&ss=${resource.userSeq}`
            : `${router.pathname}?pfs=${item.seq}`;
        router.push(!isORG_LINK ? url : (url += `&st=${shareType}`));
    };

    if (!resource || Object.keys(resource).length === 0) {
        return (
            <Stack
                direction={'row'}
                gap={1.5}
                alignItems={'center'}
                flexWrap={'nowrap'}
                sx={{ width: '100%', overflow: 'hidden' }}
            >
                <Box
                    ref={titleAreaRef}
                    sx={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 1,
                    }}
                >
                    <Box
                        sx={{
                            position: 'relative',
                            width: { xs: 20, md: 24 },
                            height: { xs: 20, md: 24 },
                        }}
                    >
                        {img}
                    </Box>
                    <Typography variant="h2_h5" component={'span'} noWrap>
                        {title}
                    </Typography>
                </Box>
            </Stack>
        );
    }

    return (
        <Stack
            direction={'row'}
            gap={1.5}
            alignItems={'center'}
            flexWrap={'nowrap'}
            sx={{ width: '100%', overflow: 'hidden' }}
        >
            {/*<StyledButton variant={'text'} color={'text_100'} onClick={() => router.push(pathName)}>*/}
            {!isORG_LINK && (
                <Stack
                    direction={'row'}
                    alignItems={'center'}
                    gap={1}
                    sx={{
                        flexShrink: 0,
                        cursor: 'pointer',
                        '&:hover': { textDecoration: 'underline' },
                    }}
                    onClick={() => router.push(pathName)}
                >
                    <Box
                        sx={{
                            width: { xs: 20, md: 24 },
                            height: { xs: 20, md: 24 },
                            position: 'relative',
                        }}
                    >
                        {img}
                    </Box>
                    <Typography variant="h2_h5" color={'text_100'} component={'span'} noWrap>
                        {title}
                    </Typography>
                </Stack>
            )}
            {/*</StyledButton>*/}

            <Stack
                direction="row"
                alignItems="center"
                gap={1.5}
                sx={{ display: { xs: 'none', md: 'flex' }, minWidth: 0 }}
            >
                {isTruncatedMd && (
                    <>
                        <Typography variant={'body9'} color={'text_40'}>
                            /
                        </Typography>
                        <BaseTooltip title={t('tooltip.showPath')}>
                            <StyledButton
                                variant={'text'}
                                sx={{ p: 0.5, lineHeight: 1 }}
                                onClick={e => setAnchorEl(e.currentTarget)}
                            >
                                <Typography variant={'subtitle3'} color={'text_80'}>
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
                                    // display: {xs: 'none', md: 'flex'},
                                    maxWidth: '288px !important',
                                    mt: 1,
                                    minWidth: 125,
                                    boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
                                },
                            }}
                            sx={{
                                display: { xs: 'none', md: 'flex' },
                                '& .MuiList-root': {
                                    width: '100%',
                                },
                            }}
                        >
                            {truncatedItemsMd.map(item => (
                                <MenuItem
                                    key={item.seq}
                                    sx={{
                                        width: '100%',
                                    }}
                                    onClick={() => {
                                        handlePathClick(item);
                                        handleMenuClose();
                                    }}
                                >
                                    <EllipsisMenuText text={item.path} />
                                </MenuItem>
                            ))}
                        </Menu>
                    </>
                )}
                {visibleItemsMd.map((item, index) => (
                    <BaseTooltip key={item.seq} title={item.path}>
                        <Stack
                            direction={'row'}
                            gap={1.5}
                            alignItems={'center'}
                            sx={{ minWidth: 0 }}
                        >
                            <Typography variant={'body9'} color={'text_40'}>
                                {isORG_LINK && index === 0 ? '' : '/'}
                            </Typography>
                            <Image
                                src={getFile('folder')?.src}
                                alt={'folder'}
                                width={20}
                                height={20}
                            />
                            <Typography
                                component="span"
                                onClick={() => {
                                    handlePathClick(item);
                                }}
                                sx={{
                                    maxWidth: visibleItemsMd.length > 1 ? 140 : 'min-content',
                                    cursor: item.isLast ? 'default' : 'pointer',
                                    '&:hover': {
                                        textDecoration: item.isLast ? 'none' : 'underline',
                                    },
                                }}
                                variant={item.isLast ? 'subtitle3' : 'body7'}
                                color={'text_80'}
                                noWrap
                            >
                                {item.path}
                            </Typography>
                        </Stack>
                    </BaseTooltip>
                ))}
            </Stack>

            <Stack
                direction="row"
                alignItems="center"
                gap={1.5}
                sx={{ display: { xs: 'flex', md: 'none' }, minWidth: 0 }}
            >
                {isTruncatedXs && (
                    <>
                        <Typography variant={'body9'} color={'text_40'}>
                            /
                        </Typography>
                        <StyledButton
                            variant={'text'}
                            sx={{ p: 0.5, lineHeight: 1 }}
                            onClick={e => setAnchorEl(e.currentTarget)}
                        >
                            <Typography variant={'subtitle3'} color={'text_80'}>
                                ...
                            </Typography>
                        </StyledButton>
                        <Menu
                            anchorEl={anchorEl}
                            open={Boolean(anchorEl)}
                            onClose={handleMenuClose}
                            sx={{ display: { xs: 'flex', md: 'none' } }}
                            PaperProps={{
                                sx: {
                                    display: { xs: 'flex', md: 'none' },
                                    maxWidth: '288px !important',
                                    mt: 1,
                                    minWidth: 125,
                                    boxShadow: `0 1px 10px 0 ${palette.divider_12}`,
                                },
                            }}
                        >
                            {truncatedItemsXs.map(item => (
                                <MenuItem
                                    key={item.seq}
                                    onClick={() => {
                                        handlePathClick(item);
                                        handleMenuClose();
                                    }}
                                >
                                    <EllipsisMenuText text={item.path} />
                                </MenuItem>
                            ))}
                        </Menu>
                    </>
                )}
                {visibleItemsXs.map(item => (
                    <Stack
                        key={item.seq}
                        direction={'row'}
                        gap={1.5}
                        alignItems={'center'}
                        sx={{ minWidth: 0 }}
                    >
                        <Typography variant={'body9'} color={'text_40'}>
                            /
                        </Typography>
                        <Image src={getFile('folder')?.src} alt={'folder'} width={20} height={20} />
                        <Typography
                            component="span"
                            onClick={() => {
                                handlePathClick(item);
                            }}
                            sx={{
                                cursor: item.isLast ? 'default' : 'pointer',
                                '&:hover': { textDecoration: item.isLast ? 'none' : 'underline' },
                            }}
                            variant={item.isLast ? 'subtitle3' : 'body7'}
                            color={'text_80'}
                            noWrap
                        >
                            {item.path}
                        </Typography>
                    </Stack>
                ))}
            </Stack>
        </Stack>
    );
}

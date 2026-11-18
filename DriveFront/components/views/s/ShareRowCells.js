import StyledCheckbox from '@/components/common/button/StyledCheckbox';
import { BaseTooltip } from '@/components/common/tooltip/BaseTooltip';
import ActionIconButton from '@/components/common/tooltip/ActionIconButton';
import { getFileByFileType, getItemExt } from '@/constants/FILES';
import convertBytesToReadableSize from '@/utils/math';
import { IconButton, Stack, TableCell, Typography } from '@mui/material';
import ProfileImage from '@/components/common/input/ProfileImage';
import dayjs from 'dayjs';
import Image from 'next/image';
import React from 'react';
import { useUser } from '@/context/UserContext';

// DriveRow 리렌더 시 ProfileImage까지 함께 리렌더되는 문제를 방지하기 위해
// Avatar 컴포넌트를 렌더링 경계로 분리
// src가 동일한 경우 React.memo에 의해 Avatar 렌더 스킵 → ProfileImage 실행 방지
const Avatar = React.memo(function Avatar({ ProfileImage, src, alt }) {
    return <ProfileImage src={src} alt={alt} width={20} height={20} />;
});

export const ShareRowCells = React.memo(
    function ShareRowCells({
        item,
        checked,
        drawerOpen,
        showLocation,
        showDeleteUser,
        onToggleCheck,
        onContextMenuOpen,
        onPreview,
        onDownload,
        hasMultipleSelection,
        disablePreview,
        disableDownload,
        t,
        getOwnerProfileSrc,
        getDeleteUserProfileSrc,
    }) {
        // TableRow는 Virtuoso가 이미 만들었으니 여기서는 TableCell만
        const ownerProfileSrc = getOwnerProfileSrc(item);
        const deleteUserProfileSrc = showDeleteUser ? getDeleteUserProfileSrc(item) : '';
        const user = useUser();
        const isExternalTenant = Number(item?.ownerTenantSeq) !== Number(user?.tenantSeq);

        return (
            <>
                <TableCell
                    align={'left'}
                    sx={{
                        p: {
                            md: '0px 4px 0px 8px !important',
                            xs: '0 0 0 6px!important',
                        },
                    }}
                    onClick={e => {
                        e.stopPropagation();
                        onToggleCheck(item);
                    }}
                >
                    {/* md 이상: 체크박스 + 아이콘 겹쳐서 표시 (CSS hover/checked) */}
                    <Stack
                        direction="row"
                        justifyContent="center"
                        alignItems="center"
                        sx={{ display: { xs: 'none', md: 'flex' } }}
                        onClick={e => {
                            e.stopPropagation();
                            onToggleCheck(item);
                        }}
                    >
                        <div className="toggle-wrap">
                            <span className="toggle-layer toggle-checkbox">
                                <StyledCheckbox checked={checked} />
                            </span>
                            <span className="toggle-layer toggle-icon">
                                <Image
                                    src={
                                        getFileByFileType(getItemExt(item), item?.resourceType)?.src
                                    }
                                    alt={
                                        getFileByFileType(getItemExt(item), item?.resourceType)
                                            ?.name
                                    }
                                    width={24}
                                    height={24}
                                />
                            </span>
                        </div>
                    </Stack>

                    {/* md 미만: 체크박스 + 아이콘 표시 */}
                    <Stack
                        direction="row"
                        justifyContent="center"
                        alignItems="center"
                        sx={{ display: { xs: 'flex', md: 'none' } }}
                        onClick={e => {
                            e.stopPropagation();
                            onToggleCheck(item);
                        }}
                    >
                        <StyledCheckbox checked={checked} />
                    </Stack>
                </TableCell>

                {/** 리소스명 */}
                <TableCell
                    style={{
                        paddingRight: '2px',
                    }}
                    sx={{
                        px: 2,
                        py: 2.5,
                        width: '100%',
                        minWidth: 0,
                        maxWidth: 50, // 옵션 영역 안밀리게 제한
                    }}
                >
                    <Stack direction="row" alignItems="center" gap={3} sx={{ minWidth: 0 }}>
                        <Stack
                            direction="row"
                            alignItems="center"
                            sx={{ display: { xs: 'flex', md: 'none' } }}
                        >
                            <Image
                                src={getFileByFileType(getItemExt(item), item?.resourceType)?.src}
                                alt={getFileByFileType(getItemExt(item), item?.resourceType)?.name}
                                width={24}
                                height={24}
                            />
                        </Stack>
                        <Stack direction={'column'} gap={1} sx={{ minWidth: 0 }}>
                            <Stack
                                direction="row"
                                alignItems={'center'}
                                gap={1.5}
                                sx={{ minWidth: 0, flex: 1 }}
                            >
                                <BaseTooltip
                                    key={item.resourceSeq}
                                    title={item?.resourceName}
                                    disableHoverListener={item.resourceName.length <= 30} // 추후 nameAreaWidth를 사용해 툴팁 표시하도록 수정 필요
                                >
                                    <Typography
                                        variant={'body5'}
                                        color={'text_80'}
                                        onClick={e => {
                                            e.stopPropagation();
                                            onPreview(item, { preferEdit: true });
                                        }}
                                        sx={{
                                            maxWidth: {
                                                xs: '100%',
                                                md: '100%',
                                                lg: '100%',
                                            },
                                            minWidth: 0, // 텍스트도 수축 허용
                                            flexShrink: 1,
                                            '&:hover': {
                                                textDecoration: 'underline',
                                            },
                                        }}
                                        noWrap
                                    >
                                        {item?.resourceName}
                                    </Typography>
                                </BaseTooltip>
                            </Stack>
                            <Stack
                                direction={'row'}
                                gap={1.5}
                                sx={{
                                    display: { xs: 'flex', md: 'none' },
                                }}
                            >
                                {item?.resourceType !== 'FOLDER' && (
                                    <>
                                        <Typography variant="body9" color={'text_60'}>
                                            {convertBytesToReadableSize(item?.sizeByte)}
                                        </Typography>
                                        <Typography variant="body9" color={'text_60'}>
                                            ·
                                        </Typography>
                                    </>
                                )}
                                <Typography
                                    variant="body9"
                                    color={'text_60'}
                                    sx={{
                                        fontVariantNumeric: 'tabular-nums',
                                    }}
                                >
                                    {dayjs(item?.resourceUpdateDate).format('YYYY. M. D. H:mm')}
                                </Typography>
                            </Stack>
                        </Stack>
                    </Stack>
                </TableCell>

                {/** 중요 표시 - 익명은 표시x 공간만 부여 */}
                <TableCell
                    style={{
                        paddingRight: 0,
                        paddingLeft: 0,
                    }}
                    sx={{
                        display: {
                            xs: 'none',
                            md: 'table-cell',
                        },
                    }}
                ></TableCell>

                {/** 소유자 */}
                <TableCell
                    style={{
                        paddingLeft: 8,
                        paddingRight: 24,
                    }}
                    sx={{
                        display: {
                            xs: 'none',
                            md: 'table-cell',
                        },
                    }}
                >
                    <Stack direction={'row'} gap={'8px'} alignItems={'center'}>
                        <Avatar ProfileImage={ProfileImage} src={ownerProfileSrc} alt="owner" />
                        <Typography variant="body9" color={'text_60'}>
                            {isExternalTenant
                                ? item?.ownerName + `(${item?.ownerTenantName})`
                                : item?.ownerName}
                        </Typography>
                    </Stack>
                </TableCell>
                {showDeleteUser && (
                    <TableCell
                        style={{
                            paddingLeft: 8,
                            paddingRight: 24,
                        }}
                        sx={{
                            display: {
                                xs: 'none',
                                md: 'table-cell',
                            },
                        }}
                    >
                        <Stack direction={'row'} gap={'8px'} alignItems={'center'}>
                            <Avatar
                                ProfileImage={ProfileImage}
                                src={deleteUserProfileSrc}
                                alt="deleteUser"
                            />
                            <Typography variant="body9" color={'text_60'}>
                                {item?.deleteUserName}
                            </Typography>
                        </Stack>
                    </TableCell>
                )}

                {/** 마지막 수정 날짜/최근 활동 날짜 */}
                <TableCell
                    style={{
                        paddingLeft: 8,
                        paddingRight: 24,
                    }}
                    sx={{
                        display: {
                            xs: 'none',
                            md: 'table-cell',
                        },
                    }}
                >
                    <Typography
                        variant="body9"
                        color={'text_60'}
                        sx={{
                            fontVariantNumeric: 'tabular-nums',
                        }}
                    >
                        {dayjs(item?.resourceUpdateDate).format('YYYY. M. D. H:mm')}
                    </Typography>
                </TableCell>

                {/** 크기 */}
                <TableCell
                    style={{
                        paddingLeft: 8,
                        paddingRight: showLocation ? 24 : 0,
                    }}
                    sx={{
                        width: showLocation ? 110 : 90,
                        minWidth: showLocation ? 110 : 90,
                        maxWidth: showLocation ? 110 : 90,
                        display: {
                            xs: 'none',
                            md: 'none',
                            lg: drawerOpen ? 'none' : 'table-cell',
                        },
                    }}
                >
                    <Typography variant="body9" color={'text_60'}>
                        {item?.resourceType !== 'FOLDER' &&
                            convertBytesToReadableSize(item?.sizeByte)}
                    </Typography>
                </TableCell>

                {/** 액션 아이콘 메뉴 */}
                <TableCell
                    sx={{
                        width: {
                            xs: 40,
                            md: 40,
                            lg: drawerOpen ? 40 : 200,
                        },
                        minWidth: {
                            xs: 0,
                            md: 0,
                            lg: drawerOpen ? 0 : 200,
                        },
                    }}
                >
                    <Stack
                        direction="row"
                        spacing={1}
                        alignItems="center"
                        justifyContent="flex-end"
                    >
                        {/* md 이상: hover시에만 표시 */}
                        <Stack
                            direction="row"
                            spacing={1}
                            alignItems="center"
                            sx={{
                                display: {
                                    xs: 'none',
                                    md: 'none',
                                    lg: drawerOpen ? 'none' : 'flex',
                                },
                            }}
                        >
                            <div className="row-actions">
                                <ActionIconButton
                                    title={t('common.action.previewOpen')}
                                    disabled={disablePreview(item)}
                                    onClick={() => onPreview(item)}
                                    icon="/common/icon-ic-preview.svg"
                                />
                                <ActionIconButton
                                    title={t('common.action.download')}
                                    disabled={disableDownload(item)}
                                    onClick={() => onDownload(item)}
                                    icon="/common/icon-ic-download.svg"
                                />
                            </div>
                        </Stack>

                        <span
                            style={{
                                cursor: hasMultipleSelection ? 'default' : 'pointer',
                                display: 'inline-flex',
                            }}
                            onClick={e => e.stopPropagation()}
                        >
                            <BaseTooltip title={t('tooltip.moreOptions')}>
                                <IconButton
                                    onClick={e => {
                                        if (hasMultipleSelection) return;
                                        e.preventDefault();
                                        e.stopPropagation();
                                        onContextMenuOpen(e, item);
                                    }}
                                    id={'dots-button'}
                                    disabled={hasMultipleSelection}
                                    sx={{
                                        display: {
                                            xs: 'flex',
                                            md: 'flex',
                                            lg: 'flex',
                                        },
                                    }}
                                >
                                    <Image
                                        src="/common/icon-ic-dots-vertical.svg"
                                        alt="dot"
                                        id={'dots'}
                                        width={20}
                                        height={20}
                                    />
                                </IconButton>
                            </BaseTooltip>
                        </span>
                    </Stack>
                </TableCell>
            </>
        );
    },
    (prev, next) => {
        return (
            prev.item?.resourceSeq === next.item?.resourceSeq &&
            prev.item?.resourceName === next.item?.resourceName &&
            prev.item?.isStarred === next.item?.isStarred &&
            prev.checked === next.checked &&
            prev.drawerOpen === next.drawerOpen &&
            prev.showLocation === next.showLocation &&
            prev.showDeleteUser === next.showDeleteUser &&
            prev.showLike === next.showLike &&
            prev.showShare === next.showShare &&
            prev.shouldShowStar === next.shouldShowStar &&
            prev.isShouldShare === next.isShouldShare &&
            // 처음으로 hasMultipleSelection === true가 될 때, 렌더링 된 row '더보기' 메뉴 모두 비활성화 처리 필요
            prev.hasMultipleSelection === next.hasMultipleSelection &&
            prev.item?.isShared === next.item?.isShared
        );
    }
);

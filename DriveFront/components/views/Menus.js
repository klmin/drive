'use client';

import * as React from 'react';
import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Collapse from '@mui/material/Collapse';
import { ListItem, Typography } from '@mui/material';
import Image from 'next/image';
import { useRouter } from '@/hooks/useRouter';
import { useTranslations } from 'next-intl';
import { TreeItem } from '@mui/x-tree-view/TreeItem';
import Box from '@mui/material/Box';
import palette from '@/theme/palette';
import { ArrowFiledRight, ArrowFilledDown } from '@/icon/Arrow';
import { useResources } from '@/provider/ResourceProvider';
import { BaseTooltip } from '../common/tooltip/BaseTooltip';
import useNavigate from '@/hooks/useNavigate';
import { ROUTES } from '@/constants/routes';

function findAncestors(flatData, childId) {
    const map = new Map(flatData.map(item => [item.resourceSeq, item]));
    const ancestors = [];
    let current = map.get(childId);

    while (current && current.parentFolderSeq) {
        ancestors.push(String(current.parentFolderSeq));
        current = map.get(current.parentFolderSeq);
    }
    return ancestors;
}

function buildTree(data) {
    const map = new Map();
    const roots = [];
    const foldersOnly = data.filter(item => item.resourceType === 'FOLDER');

    foldersOnly.forEach(item => {
        map.set(item.resourceSeq, { ...item, children: [] });
    });

    foldersOnly.forEach(item => {
        if (item.parentFolderSeq === null || item.parentFolderSeq === undefined) {
            roots.push(map.get(item.resourceSeq));
        } else {
            const parent = map.get(item.parentFolderSeq);
            if (parent) {
                parent.children.push(map.get(item.resourceSeq));
            }
        }
    });

    return roots;
}

const renderTreeItems = (nodes, navigate, level = 1) => {
    const indentation = 16;
    return nodes.map((node, index) => {
        return (
            <TreeItem
                key={node.resourceSeq}
                itemId={`${node.resourceSeq}`}
                slotProps={{
                    content: {
                        sx: {
                            paddingLeft: `${6.5 + level * indentation}px !important`,
                            height: 32,
                            borderRadius: 0,
                            '&.Mui-selected': {
                                backgroundColor: 'action.selected',
                            },
                        },
                    },
                }}
                label={
                    <Box
                        sx={{ display: 'flex', alignItems: 'center', width: '100%' }}
                        onClick={e => {
                            e.stopPropagation();
                            navigate(`/drive?pfs=${node.resourceSeq}`);
                        }}
                    >
                        <Typography variant={'body7'} color={'text_100'} noWrap>
                            {node.resourceName}
                        </Typography>
                    </Box>
                }
            >
                {node.resourceType === 'FOLDER' && node.children?.length > 0
                    ? renderTreeItems(node.children, navigate, level + 1)
                    : null}
            </TreeItem>
        );
    });
};

function CustomTreeItem({ node, level, expanded, onToggle, onNavigate, selectedId }) {
    const hasChildren = node.children && node.children.length > 0;
    const isExpanded = expanded.includes(String(node.resourceSeq));
    const isSelected = selectedId === String(node.resourceSeq);

    const handleToggleClick = e => {
        e.stopPropagation();
        onToggle(String(node.resourceSeq));
    };

    const handleNavigateClick = () => {
        onNavigate(node);
    };

    const textRef = useRef(null);
    const [isTruncated, setIsTruncated] = useState(false);

    useEffect(() => {
        if (!node || !node.resourceName) {
            return;
        }

        const checkTruncation = () => {
            if (textRef.current) {
                setIsTruncated(textRef.current.scrollWidth > textRef.current.offsetWidth);
            }
        };

        checkTruncation();
        window.addEventListener('resize', checkTruncation);

        return () => {
            window.removeEventListener('resize', checkTruncation);
        };
    }, [node]);

    return (
        <>
            <ListItemButton
                selected={isSelected}
                onClick={handleNavigateClick}
                sx={{
                    pl: 2 + level * 2,
                    height: 32,
                    backgroundColor: isSelected
                        ? `${palette.btn_white_bg_hover} !important`
                        : 'transparent',
                    '&:hover': {
                        backgroundColor: palette.btn_white_bg_hover,
                    },
                }}
            >
                <Box
                    sx={{
                        width: 24,
                        height: 24,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        mr: 0.5,
                        flexShrink: 0,
                    }}
                    onClick={handleToggleClick}
                >
                    {hasChildren &&
                        (isExpanded ? (
                            <ArrowFilledDown sx={{ width: 12, height: 12 }} />
                        ) : (
                            <ArrowFiledRight sx={{ width: 12, height: 12 }} />
                        ))}
                </Box>

                {/* 폴더 이름 */}
                <BaseTooltip disableHoverListener={!isTruncated} title={node.resourceName}>
                    <ListItemText
                        primary={node.resourceName}
                        primaryTypographyProps={{
                            variant: 'body7',
                            color: `${palette.text_100} !important`,
                            noWrap: true,
                            ref: textRef,
                        }}
                    />
                </BaseTooltip>
            </ListItemButton>

            {hasChildren && (
                <Collapse in={isExpanded} timeout={0} unmountOnExit={false}>
                    <List component="div" disablePadding>
                        {node.children.map(childNode => (
                            <CustomTreeItem
                                key={childNode.resourceSeq}
                                node={childNode}
                                level={level + 1}
                                expanded={expanded}
                                onToggle={onToggle}
                                onNavigate={onNavigate}
                                selectedId={selectedId}
                            />
                        ))}
                    </List>
                </Collapse>
            )}
        </>
    );
}

function CustomListItemButton({ route, text, activeIconSrc, inactiveIconSrc }) {
    const router = useRouter();
    const navigate = useNavigate();
    const isActive = router.pathname === route;

    return (
        <ListItemButton sx={{ px: 6, gap: 1.5 }} onClick={() => navigate(route)}>
            <ListItemIcon sx={{ minWidth: 16 }}>
                {/* CSS를 활용한 깜빡임 방지 로직 적용 */}
                <div className="menu-icon-wrapper">
                    <Image
                        src={inactiveIconSrc}
                        alt={text}
                        width={16}
                        height={16}
                        className={`menu-icon ${!isActive ? 'visible' : ''}`}
                    />
                    <Image
                        src={activeIconSrc}
                        alt={`${text} active`}
                        width={16}
                        height={16}
                        className={`menu-icon ${isActive ? 'visible' : ''}`}
                    />
                </div>
            </ListItemIcon>
            <ListItemText
                primary={text}
                primaryTypographyProps={{
                    sx: {
                        fontWeight: isActive ? '600 !important' : 'normal !important',
                        color: `${palette.text_100} !important`,
                    },
                }}
            />
        </ListItemButton>
    );
}

export default memo(function Menus({ shareEnabled }) {
    const t = useTranslations();
    const router = useRouter();
    const navigate = useNavigate();
    const { pathname, query } = router;
    const { resources } = useResources();
    const [expanded, setExpanded] = useState(() => {
        const savedState = sessionStorage.getItem('treeExpandedState');
        return savedState ? JSON.parse(savedState) : [''];
    });
    const scrollContainerRef = useRef(null);

    useEffect(() => {
        if (pathname.includes('drive')) {
            sessionStorage.setItem('treeExpandedState', JSON.stringify(expanded));
        }
    }, [expanded]);

    useEffect(() => {
        if (scrollContainerRef.current) {
            sessionStorage.setItem(
                'menusScrollPosition',
                scrollContainerRef.current.scrollTop.toString()
            );
        }
    }, [pathname]);

    useEffect(() => {
        if (pathname.includes('/drive')) {
            if (!query.pfs) {
                setExpanded(prevExpanded =>
                    prevExpanded.includes('drive') ? prevExpanded : [...prevExpanded, 'drive']
                );
            }
        }
    }, [query.pfs, pathname]);

    useEffect(() => {
        if (pathname.includes('/drive') && query.pfs) {
            const activeItemId = String(query.pfs);
            const ancestorIds = findAncestors(resources, Number(activeItemId));
            const requiredPath = ['drive', ...ancestorIds, activeItemId]; // 현재 폴더도 확장 대상에 포함
            const newExpanded = [...expanded];

            let hasChanges = false;
            requiredPath.forEach(path => {
                if (!newExpanded.includes(path)) {
                    newExpanded.push(path);
                    hasChanges = true;
                }
            });
            if (hasChanges) {
                setExpanded(newExpanded);
            }
        }
    }, [resources, query.pfs, pathname]);

    const treeData = useMemo(() => buildTree(resources), [resources]);
    const handleToggle = useCallback(itemId => {
        setExpanded(prevExpanded => {
            const isExpanded = prevExpanded.includes(itemId);
            if (isExpanded) {
                return prevExpanded.filter(id => id !== itemId);
            } else {
                return [...prevExpanded, itemId];
            }
        });
    }, []);

    const isDriveSelected = !query.pfs && pathname.includes('/drive');
    const handleNavigate = useCallback(
        node => {
            navigate(`/drive?pfs=${node.resourceSeq}`);
        },
        [navigate]
    );

    const stableExpanded = useMemo(() => expanded, [expanded]);

    useEffect(() => {
        const handleBeforeUnload = () => {
            sessionStorage.removeItem('treeExpandedState');
        };

        window.addEventListener('beforeunload', handleBeforeUnload);
        return () => {
            window.removeEventListener('beforeunload', handleBeforeUnload);
        };
    }, []);

    return (
        <List
            sx={{
                width: '100%',
                maxWidth: 360,
                bgcolor: '#FAFAFA',
                pt: 0,
                pb: '10px',
            }}
            component="nav"
            aria-labelledby="nested-list-subheader"
        >
            <CustomListItemButton
                route={ROUTES.HOME}
                text={t('sidebar.home')}
                activeIconSrc={'/menu/icon-ic-home.svg'}
                inactiveIconSrc={'/menu/icon-ic-home-outline.svg'}
            />

            <ListItem sx={{ px: 0, py: 0, display: 'block' }}>
                <ListItemButton
                    selected={isDriveSelected}
                    onClick={() => navigate(ROUTES.DRIVE)}
                    sx={{
                        pl: 2,
                        height: 32,
                        backgroundColor: isDriveSelected
                            ? `${palette.btn_white_bg_hover} !important`
                            : 'transparent',
                        '&:hover': { backgroundColor: palette.btn_white_bg_hover },
                    }}
                >
                    <Box
                        sx={{
                            width: 12,
                            height: 12,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            mr: 1,
                        }}
                        onClick={e => {
                            e.stopPropagation();
                            if (e.target.closest('#right')) {
                                sessionStorage.setItem(
                                    'treeExpandedState',
                                    JSON.stringify(['drive'])
                                );
                            } else
                                sessionStorage.setItem('treeExpandedState', JSON.stringify(['']));
                            handleToggle('drive');
                        }}
                    >
                        {stableExpanded.includes('drive') ? (
                            <ArrowFilledDown sx={{ width: 12, height: 12 }} />
                        ) : (
                            <ArrowFiledRight id={'right'} sx={{ width: 12, height: 12 }} />
                        )}
                    </Box>
                    <Image
                        src={
                            pathname.includes('drive')
                                ? '/menu/icon-ic-drive.svg'
                                : '/menu/icon-ic-drive-outline.svg'
                        }
                        alt="drive"
                        width={16}
                        height={16}
                    />
                    <ListItemText
                        primary={t('common.label.myDrive')}
                        primaryTypographyProps={{
                            variant: 'body5',
                            ml: 1.5,
                            color: `${palette.text_100} !important`,
                            fontWeight: pathname.includes('drive') ? '600 !important' : 'normal',
                        }}
                    />
                </ListItemButton>
                <Collapse in={stableExpanded.includes('drive')} timeout={0} unmountOnExit={false}>
                    <List component="div" disablePadding>
                        {treeData.map(node => (
                            <CustomTreeItem
                                key={node.resourceSeq}
                                node={node}
                                level={1}
                                expanded={stableExpanded}
                                selectedId={query.pfs ? String(query.pfs) : null}
                                onToggle={handleToggle}
                                onNavigate={handleNavigate}
                            />
                        ))}
                    </List>
                </Collapse>
            </ListItem>
            <CustomListItemButton
                route={ROUTES.RECENT}
                text={t('common.label.recent')}
                activeIconSrc={'/menu/icon-ic-recent.svg'}
                inactiveIconSrc={'/menu/icon-ic-recent-outline.svg'}
            />
            <CustomListItemButton
                route={ROUTES.STARRED}
                text={t('common.label.starred')}
                activeIconSrc={'/menu/icon-ic-star.svg'}
                inactiveIconSrc={'/menu/icon-ic-star-outline.svg'}
            />
            {shareEnabled === true && (
                <CustomListItemButton
                    route={ROUTES.SHARED}
                    text={t('common.label.shared')}
                    activeIconSrc={'/menu/icon-ic-share.svg'}
                    inactiveIconSrc={'/menu/icon-ic-share-outline.svg'}
                />
            )}
            <CustomListItemButton
                route={ROUTES.TRASH}
                text={t('common.label.trash')}
                activeIconSrc={'/menu/icon-ic-trash.svg'}
                inactiveIconSrc={'/menu/icon-ic-trash-outline.svg'}
            />
        </List>
    );
});

'use client';

import { useEffect } from 'react';

const setImagesDraggableFalse = container => {
    const images = container.querySelectorAll('img');
    images.forEach(img => {
        if (!img.hasAttribute('draggable')) {
            img.draggable = false;
        }
    });
};

export default function DraggableImageBlocker() {
    useEffect(() => {
        setImagesDraggableFalse(document);

        const observer = new MutationObserver(mutations => {
            mutations.forEach(mutation => {
                if (mutation.addedNodes.length > 0) {
                    mutation.addedNodes.forEach(node => {
                        if (node.nodeType === 1) {
                            setImagesDraggableFalse(node);
                        }
                    });
                }
            });
        });

        observer.observe(document.body, {
            childList: true,
            subtree: true,
        });

        return () => observer.disconnect();
    }, []);

    return null;
}

import React, { Profiler } from 'react';
import { recordProfilerEntry } from './reactProfilerStore';

export function PerfProfiler({ id, enabled, children }) {
    if (!enabled) return <>{children}</>;

    return (
        <Profiler
            id={id}
            onRender={(id, phase, actualDuration, baseDuration, startTime, commitTime) => {
                recordProfilerEntry({
                    id,
                    phase, // "mount" | "update"
                    actualDuration, // 실제 렌더 비용(ms)
                    baseDuration,
                    startTime,
                    commitTime,
                });
            }}
        >
            {children}
        </Profiler>
    );
}

const STORE_KEY = '__PERF_REACT_PROFILER__';

function getStore() {
    if (!globalThis[STORE_KEY]) globalThis[STORE_KEY] = [];
    return globalThis[STORE_KEY];
}

export function recordProfilerEntry(entry) {
    getStore().push(entry);
}

export function clearProfilerEntries() {
    getStore().length = 0;
}

export function readProfilerEntries() {
    return [...getStore()];
}

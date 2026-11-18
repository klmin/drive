/**
 * Drive resources mock data generator
 *
 * Usage:
 *  node tests/scripts/generate-mock-resources.js --count 500
 *  node tests/scripts/generate-mock-resources.js --count 2000
 *  node tests/scripts/generate-mock-resources.js --count 2000 --out mocks/data/resources_2000.json
 *
 * Default output:
 *  tests/mocks/data/resources_<count>.json
 */

const fs = require('fs');
const path = require('path');

function parseArgs(argv) {
    const args = {};
    for (let i = 2; i < argv.length; i++) {
        const k = argv[i];
        const v = argv[i + 1];
        if (k === '--count') args.count = Number(v);
        if (k === '--out') args.out = v;
    }
    return args;
}

function pad2(n) {
    return String(n).padStart(2, '0');
}

// "YYYY-MM-DD HH:mm:ss.SSS" 형태 (너희 응답과 동일한 포맷)
function formatDate(d) {
    const yyyy = d.getFullYear();
    const mm = pad2(d.getMonth() + 1);
    const dd = pad2(d.getDate());
    const hh = pad2(d.getHours());
    const mi = pad2(d.getMinutes());
    const ss = pad2(d.getSeconds());
    const ms = String(d.getMilliseconds()).padStart(3, '0');
    return `${yyyy}-${mm}-${dd} ${hh}:${mi}:${ss}.${ms}`;
}

// 랜덤 (고정 seed 없이도 되지만, 매번 결과 달라지는 게 싫으면 seed-random 붙이면 됨)
function randInt(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

function pick(arr) {
    return arr[randInt(0, arr.length - 1)];
}

// 파일명 만들기 (정렬/검색/ellipsis 등 UI 케이스 커버)
function makeFileName(i) {
    const exts = ['pdf', 'docx', 'pptx', 'xlsx', 'zip', 'gif', 'png', 'tfi', 'json', 'txt'];
    const ext = pick(exts);

    // 일부는 한국어/특수문자/긴 파일명 케이스 포함
    if (i % 37 === 0) return `회의자료_${i}_${randInt(10, 99)}.${ext}`;
    if (i % 53 === 0) return `정말정말긴파일명_${i}_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.${ext}`;
    if (i % 71 === 0) return `New Note (${randInt(1, 300)}).tfi`;
    if (i % 89 === 0) return `2026_01_${pad2(randInt(1, 28))}_거래명세표_${i}.${ext}`;

    return `File_${pad2(Math.floor(i / 1000))}_${String(i).padStart(4, '0')}.${ext}`;
}

function makeFolderName(i) {
    if (i % 41 === 0) return `제목없는 폴더 ${i}`;
    if (i % 97 === 0) return `React App_files_${i}`;
    return `Folder_${pad2(Math.floor(i / 1000))}_${String(i).padStart(4, '0')}`;
}

// sizeByte: 폴더는 null, 파일은 랜덤 바이트
function makeSizeByte(type) {
    if (type === 'FOLDER') return null;
    // 100B ~ 25MB
    return randInt(100, 25 * 1024 * 1024);
}

// 일부 shared/starred 케이스 섞기
function makeFlags(i) {
    const isStarred = i % 13 === 0; // 1/13
    const isShared = i % 17 === 0; // 1/17
    return { isStarred, isShared };
}

// 권한 플래그 (대부분 true, 일부 edge 케이스)
function makePerms(type, i) {
    // 폴더는 다운로드 false인 케이스 좀 섞기
    const canDownload = type === 'FILE' ? true : i % 9 === 0 ? true : false;

    // 일부는 canEdit false
    const canEdit = i % 23 === 0 ? false : true;

    return {
        canView: true,
        canEdit,
        canDownload,
        canUpload: type === 'FOLDER' ? true : false,
        canDelete: true,
        canShare: true,
    };
}

// owner 정보 (고정해도 충분)
function makeOwner() {
    return {
        userSeq: 41,
        ownerSeq: 41,
        ownerId: 'sunkyoung.yoon@thinkfree.com',
        ownerName: '윤선경',
        ownerStatus: 'ACTIVE',
    };
}

// 날짜: 최근일수록 updateDate가 뒤로 가도록 분포
function makeDates(i) {
    // 기준 시간: 지금
    const now = new Date();
    // i가 커질수록 조금 더 과거로
    const daysAgo = Math.floor(i / 50); // 0~(count/50)일 전 정도
    const minutesJitter = randInt(0, 60 * 24); // 0~1440분 랜덤
    const d = new Date(now.getTime() - (daysAgo * 24 * 60 + minutesJitter) * 60 * 1000);

    const register = new Date(d.getTime() - randInt(0, 10) * 24 * 60 * 60 * 1000);
    const update = d;
    const recentUpdate = new Date(d.getTime() - randInt(0, 120) * 60 * 1000);

    return {
        resourceRegisterDate: formatDate(register),
        resourceUpdateDate: formatDate(update),
        recentUpdateDate: formatDate(recentUpdate),
        // recentRegisterDate는 recent 화면 대비용(있어도 되고 없어도 됨)
        recentRegisterDate: formatDate(register),
    };
}

// 루트에 많이 있게 만들고, 일부는 parentFolderSeq를 넣어 폴더 이동/위치 케이스도 살짝
function makeParent(i, folderSeqPool) {
    // 70%는 루트
    if (Math.random() < 0.7 || folderSeqPool.length === 0) {
        return { parentFolderSeq: null, parentFolderName: null, fullPath: null, fullPathSeq: null };
    }
    const parentFolderSeq = pick(folderSeqPool);
    return {
        parentFolderSeq,
        parentFolderName: `Folder_${String(parentFolderSeq).slice(-4)}`,
        fullPath: `Folder_${String(parentFolderSeq).slice(-4)}`,
        fullPathSeq: null,
    };
}

function buildResource({ seq, type, name, parent, dates, flags, perms }) {
    const owner = makeOwner();

    return {
        resourceSeq: seq,
        resourceType: type,

        parentFolderSeq: parent.parentFolderSeq,
        parentFolderName: parent.parentFolderName,

        resourceName: name,

        ...owner,

        fullPathSeq: parent.fullPathSeq,
        fullPath: parent.fullPath,

        sizeByte: makeSizeByte(type),

        isLocked: false,

        isStarred: flags.isStarred,
        starredSeq: flags.isStarred ? seq + 1000 : null,

        isShared: flags.isShared,
        shareSeq: flags.isShared ? seq + 500 : null,
        shareTargetSeq: null,
        shareResourceSeq: flags.isShared ? seq : null,

        ...perms,

        resourceRegisterUserId: owner.ownerId,
        resourceRegisterUserName: owner.ownerName,
        resourceRegisterUserStatus: owner.ownerStatus,

        resourceRegisterDate: dates.resourceRegisterDate,

        resourceUpdateUserId: owner.ownerId,
        resourceUpdateUserName: owner.ownerName,
        resourceUpdateUserStatus: owner.ownerStatus,

        resourceUpdateDate: dates.resourceUpdateDate,
        recentUpdateDate: dates.recentUpdateDate,
        recentRegisterDate: dates.recentRegisterDate,

        resourceLocation: 'MY',
    };
}

function generate(count) {
    // seq는 실제처럼 큰 숫자 느낌도 가능
    const baseSeq = 25000;

    const items = [];
    const folderSeqPool = [];

    // 폴더 비율: 20% 정도
    const folderEvery = 5; // 1/5 = 20%

    for (let i = 1; i <= count; i++) {
        const seq = baseSeq + i;

        const type = i % folderEvery === 0 ? 'FOLDER' : 'FILE';
        const name = type === 'FOLDER' ? makeFolderName(i) : makeFileName(i);

        const dates = makeDates(i);
        const flags = makeFlags(i);
        const perms = makePerms(type, i);

        const parent = makeParent(i, folderSeqPool);

        const resource = buildResource({ seq, type, name, parent, dates, flags, perms });
        items.push(resource);

        if (type === 'FOLDER') folderSeqPool.push(seq);
    }

    return { items };
}

function ensureDir(dirPath) {
    fs.mkdirSync(dirPath, { recursive: true });
}

function main() {
    const { count = 500, out } = parseArgs(process.argv);
    if (!count || Number.isNaN(count) || count <= 0) {
        console.error('❌ --count must be a positive number');
        process.exit(1);
    }

    const defaultOut = `tests/mocks/data/resources_${count}.json`;
    const outPath = out ? out : defaultOut;

    const data = generate(count);

    const absOut = path.resolve(process.cwd(), outPath);
    ensureDir(path.dirname(absOut));

    fs.writeFileSync(absOut, JSON.stringify(data, null, 2), 'utf-8');
    console.log(`✅ Generated ${count} items -> ${outPath}`);
}

main();

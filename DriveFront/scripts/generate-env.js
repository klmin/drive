const fs = require('fs');
const path = require('path');

const now = new Date();
const kst = new Date(now.getTime() + 9 * 60 * 60 * 1000); // UTC+9

const formattedTime = kst.toISOString().replace('T', ' ').slice(0, 16); // "2025-07-30 12:34"
const compactTime = kst.toISOString().replace(/[-:T]/g, '').slice(0, 12); // "202507301234"

const envPath = path.join(__dirname, '../.env');

let env = '';
if (fs.existsSync(envPath)) {
    env = fs.readFileSync(envPath, 'utf-8');
}

const updatedEnv = env
    .split('\n')
    .filter(
        line =>
            !line.startsWith('NEXT_PUBLIC_BUILD_TIME=') &&
            !line.startsWith('NEXT_PUBLIC_BUILD_FILE_NAME=')
    )
    .concat([
        `NEXT_PUBLIC_BUILD_TIME=${formattedTime}`,
        `NEXT_PUBLIC_BUILD_FILE_NAME=Thinkfree-DriveFront-0.0.1_${compactTime}`,
    ])
    .join('\n');

fs.writeFileSync(envPath, updatedEnv.trim() + '\n');

#!/usr/bin/env node

import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { resolve, join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { spawnSync } from 'node:child_process';

const [generatedArg, edgeArg, resultArg] = process.argv.slice(2);
if (!generatedArg || !edgeArg || !resultArg) {
    console.error('Usage: node tools/test-scriptlets-v2.mjs <generated directory> <Edge executable> <result directory>');
    process.exit(2);
}
const generated = resolve(generatedArg);
const results = resolve(resultArg);
await mkdir(results, { recursive: true });
const code = (await readFile(join(generated, 'set-constant.js'), 'utf8'))
    .replace('__EBLOCKER_SCRIPTLET_ARGS__', JSON.stringify(['eblockerTestFlag', 'false']));
if (code.includes('__EBLOCKER_SCRIPTLET_ARGS__')) throw new Error('Argument marker was not replaced');
for (const [name, csp, nonce] of [
    ['plain', '', ''],
    ['strict-csp', `<meta http-equiv="Content-Security-Policy" content="script-src-elem 'self' 'nonce-eblocker-test'">`, ' nonce="eblocker-test"'],
]) {
    const html = `<!doctype html><html><head><meta charset="utf-8">${csp}<script${nonce}>${code}</script></head>` +
        `<body><script${nonce}>document.body.dataset.result = window.eblockerTestFlag === false ? 'PASS' : 'FAIL';</script></body></html>`;
    const page = join(results, `scriptlet-v2-${name}.html`);
    await writeFile(page, html, 'utf8');
    const run = spawnSync(resolve(edgeArg), [
        '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
        `--user-data-dir=${join(results, `edge-profile-${name}`)}`,
        '--dump-dom', pathToFileURL(page).href,
    ], { encoding: 'utf8', timeout: 30000 });
    if (run.error) throw run.error;
    if (run.status !== 0 || !run.stdout.includes('data-result="PASS"')) {
        console.error(run.stdout.slice(-2000));
        console.error(run.stderr.slice(-1000));
        throw new Error(`Edge scriptlet ${name} smoke test failed (${run.status})`);
    }
}
console.log('PASS: generated set-constant scriptlet ran in Edge with and without strict CSP');

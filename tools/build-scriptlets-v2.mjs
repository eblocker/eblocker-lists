#!/usr/bin/env node

// Build standalone page-world scriptlets from uBlock Origin's registered modules.
// Usage: node tools/build-scriptlets-v2.mjs <uBlock root> <output directory> [expected commit]
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import { resolve, join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { execFileSync } from 'node:child_process';

const [upstreamArg, outputArg, expectedCommit] = process.argv.slice(2);
if (!upstreamArg || !outputArg) {
    console.error('Usage: node tools/build-scriptlets-v2.mjs <uBlock root> <output directory>');
    process.exit(2);
}

const upstream = resolve(upstreamArg);
const output = resolve(outputArg);
const entryPoint = join(upstream, 'src/js/resources/scriptlets.js');
const legacyCommit = 'e93117cbb607472a830e1c0653dfbddde4c965fc';
const sourceCommit = execFileSync('git', ['-C', upstream, 'rev-parse', 'HEAD'], { encoding: 'utf8' }).trim();
if (expectedCommit && sourceCommit !== expectedCommit) {
    throw new Error(`uBlock checkout is ${sourceCommit}; expected ${expectedCommit}`);
}
const { builtinScriptlets } = await import(pathToFileURL(entryPoint).href);
const registry = new Map(builtinScriptlets.map(entry => [entry.name, entry]));
const skipped = [];
const generated = [];
const legacyFallback = [];
const writtenNames = new Set();

function dependenciesFor(entry) {
    const visited = new Set();
    const visiting = new Set();
    const ordered = [];
    function visit(name) {
        if (visited.has(name)) return;
        if (visiting.has(name)) throw new Error(`Circular dependency: ${name}`);
        const dependency = registry.get(name);
        if (!dependency) throw new Error(`Missing dependency ${name} for ${entry.name}`);
        visiting.add(name);
        for (const child of dependency.dependencies ?? []) visit(child);
        visiting.delete(name);
        visited.add(name);
        ordered.push(dependency);
    }
    for (const name of entry.dependencies ?? []) visit(name);
    return ordered;
}

function standalone(entry) {
    const functions = [...dependenciesFor(entry), entry];
    const source = functions.map(item => item.fn.toString()).join('\n\n');
    return `/* eBlocker scriptlet v2; uBlock Origin GPL-3.0; ${entry.name} */\n` +
        `(function() {\n` +
        `const scriptletGlobals = { bcSecret: undefined, logLevel: 0 };\n` +
        `${source}\n` +
        `try { ${entry.fn.name}(...__EBLOCKER_SCRIPTLET_ARGS__); } catch (_) {}\n` +
        `})();\n`;
}

await mkdir(output, { recursive: true });
for (const entry of builtinScriptlets) {
    if (!entry.name.endsWith('.js')) continue;
    if (entry.requiresTrust || entry.name.startsWith('trusted-') || (entry.world && entry.world !== 'MAIN')) {
        skipped.push(entry.name);
        continue;
    }
    const names = [entry.name, ...(entry.aliases ?? [])];
    const code = standalone(entry);
    if (/<\/script/i.test(code)) throw new Error(`Unsafe HTML script end in ${entry.name}`);
    for (const name of names) {
        if (!/^[A-Za-z0-9_.-]+\.js$/.test(name) || name.includes('..')) {
            throw new Error(`Unsafe scriptlet filename: ${name}`);
        }
        if (writtenNames.has(name)) throw new Error(`Duplicate scriptlet alias: ${name}`);
        writtenNames.add(name);
        await writeFile(join(output, name), code, 'utf8');
    }
    generated.push({ name: entry.name, aliases: names.slice(1), dependencies: entry.dependencies ?? [] });
}

// Retain names from the version currently shipped by eBlocker where uBlock no longer
// registers an equivalent module. These files keep the old {{n}} argument format.
const oldSource = execFileSync('git', ['-C', upstream, 'show', `${legacyCommit}:assets/resources/scriptlets.js`],
    { encoding: 'utf8', maxBuffer: 10 * 1024 * 1024 });
let oldNames = [];
let oldCode = [];
let inScriptlet = false;
async function finishLegacy() {
    if (oldNames.length === 0) return;
    const missing = oldNames.filter(name => !writtenNames.has(name));
    if (missing.length > 0) {
        const code = oldCode.join('\n') + '\n';
        if (/<\/script/i.test(code)) throw new Error(`Unsafe HTML script end in legacy ${oldNames[0]}`);
        for (const name of missing) {
            if (!/^[A-Za-z0-9_.-]+\.js$/.test(name) || name.includes('..')) {
                throw new Error(`Unsafe legacy scriptlet filename: ${name}`);
            }
            writtenNames.add(name);
            await writeFile(join(output, name), code, 'utf8');
        }
        legacyFallback.push({ source: oldNames[0], names: missing });
    }
    oldNames = [];
    oldCode = [];
}
for (const line of oldSource.split(/\r?\n/)) {
    const match = /^\/\/\/ (?:alias )?([A-Za-z0-9_.-]+)$/.exec(line);
    if (match) {
        if (!inScriptlet) await finishLegacy();
        const name = match[1].endsWith('.js') ? match[1] : `${match[1]}.js`;
        oldNames.push(name);
        inScriptlet = true;
    } else if (line === '})();' && inScriptlet) {
        oldCode.push(line);
        inScriptlet = false;
    } else if (inScriptlet) {
        oldCode.push(line);
    }
}
await finishLegacy();
if (generated.length < 20) throw new Error(`Only ${generated.length} scriptlets generated`);
const stale = (await readdir(output)).filter(name => name.endsWith('.js') && !writtenNames.has(name));
if (stale.length) throw new Error(`Stale scriptlets in output directory: ${stale.join(', ')}. Use a clean output directory.`);
await writeFile(join(output, 'manifest.json'), JSON.stringify({
    source: 'https://github.com/gorhill/uBlock',
    sourceCommit,
    entryPoint: 'src/js/resources/scriptlets.js',
    license: 'GPL-3.0',
    generated,
    legacyCommit,
    legacyFallback,
    skipped,
}, null, 2) + '\n');
await writeFile(join(output, 'LICENSE-UBLOCK.txt'), await readFile(join(upstream, 'LICENSE.txt')));
console.log(`Generated ${generated.length} page-world scriptlets (${generated.reduce((n, item) => n + item.aliases.length, 0)} aliases) and ${legacyFallback.reduce((n, item) => n + item.names.length, 0)} legacy fallback names; skipped ${skipped.length}.`);

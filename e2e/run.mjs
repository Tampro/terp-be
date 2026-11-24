// Live E2E runner for the ERP stack (gateway :18080 by default, E2E_GATEWAY to override).
//   node e2e/run.mjs                 every suite except opt-in ones (see suites.json)
//   node e2e/run.mjs acc sales own   only these suites
//   node e2e/run.mjs --all           also the opt-in suites (s3: needs the seaweedfs container)
//   node e2e/run.mjs --verbose ...   print every PASS line, not only failures
//   node e2e/run.mjs --keep ...      keep the test companies (default: soft-deleted afterwards, see cleanup.mjs)
// Each suite = e2e/lib/head.mjs (login + two fresh test companies) + e2e/suites/<name>.mjs, unless the suite is
// standalone. Suites that write to public, rate-limited endpoints (career apply, support portal) wait for the
// gateway bucket to refill (burst 5, 10/min) so back-to-back runs don't fail with 429.
import fs from 'fs'; import os from 'os'; import path from 'path'; import { spawnSync } from 'child_process';
import { fileURLToPath } from 'url';
import { cleanup } from './cleanup.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const manifest = JSON.parse(fs.readFileSync(path.join(here, 'suites.json'), 'utf8'));
const head = fs.readFileSync(path.join(here, 'lib', 'head.mjs'), 'utf8');
const args = process.argv.slice(2);
const verbose = args.includes('--verbose'); const all = args.includes('--all'); const keep = args.includes('--keep');
const names = args.filter(a => !a.startsWith('--'));
const unknown = names.filter(n => !manifest[n]);
if (unknown.length) { console.error('Unknown suite(s): ' + unknown.join(', ') + '\nKnown: ' + Object.keys(manifest).join(' ')); process.exit(2); }
const selected = names.length ? names : Object.keys(manifest).filter(n => all || !manifest[n].optIn);
const PUBLIC_GAP_MS = 65_000;
const sleep = ms => new Promise(r => setTimeout(r, ms));

function compose(name) {
  const m = manifest[name];
  const body = fs.readFileSync(path.join(here, 'suites', name + '.mjs'), 'utf8');
  if (m.standalone) return body;
  // ES module imports must come first: hoist the body's import lines above the head.
  const lines = body.split('\n');
  const imports = lines.filter(l => /^import\s/.test(l)); const rest = lines.filter(l => !/^import\s/.test(l));
  const h = head.replaceAll('__PFX__', m.prefix).replaceAll('__pfx__', m.prefix.toLowerCase()).replaceAll('__NAME__', m.prefix + 'E');
  return imports.join('\n') + '\n' + h + '\n' + rest.join('\n');
}

const results = []; let lastPublic = 0; const startedAt = new Date(Date.now() - 5000);
for (const name of selected) {
  const m = manifest[name];
  if (m.publicWrites && lastPublic && Date.now() - lastPublic < PUBLIC_GAP_MS) {
    const wait = PUBLIC_GAP_MS - (Date.now() - lastPublic);
    console.log(`… waiting ${Math.ceil(wait / 1000)} s for the public rate limit before ${name}`);
    await sleep(wait);
  }
  const file = path.join(os.tmpdir(), `erp-e2e-${name}-${process.pid}.mjs`);
  fs.writeFileSync(file, compose(name));
  const t0 = Date.now();
  const r = spawnSync(process.execPath, [file], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
  fs.rmSync(file, { force: true });
  if (m.publicWrites) lastPublic = Date.now();
  const out = (r.stdout || '') + (r.stderr || '');
  const sum = out.match(/(\d+)\/(\d+) passed/);
  const fails = out.split('\n').filter(l => l.startsWith('FAIL'));
  if (verbose) console.log(out.split('\n').filter(l => /^(PASS|FAIL)/.test(l)).join('\n'));
  else fails.forEach(l => console.log('  ' + l.slice(0, 300)));
  const ok = sum && sum[1] === sum[2];
  const line = sum ? `${sum[1]}/${sum[2]}` : 'CRASHED';
  console.log(`${ok ? 'OK  ' : 'FAIL'} ${name.padEnd(13)} ${line.padEnd(8)} ${((Date.now() - t0) / 1000).toFixed(0)} s  ${m.description}`);
  if (!sum) console.log(out.split('\n').filter(l => /Error|at /.test(l)).slice(0, 5).map(l => '  ' + l.slice(0, 200)).join('\n'));
  results.push({ name, ok, line });
}
if (!keep) {
  try { await cleanup({ since: startedAt }); } catch (e) { console.log('cleanup skipped: ' + e.message); }
}
const failed = results.filter(r => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} suites green` + (failed.length ? ' — failed: ' + failed.map(r => `${r.name} (${r.line})`).join(', ') : ''));
process.exitCode = failed.length ? 1 : 0; // not process.exit(): cleanup's fetch sockets crash Node on Windows

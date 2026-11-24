// Deletes (soft, through the super-admin API) the test companies the E2E suites leave behind, with their accounts:
// the company is marked deleted and its users are removed from Keycloak; business rows in the other services stay.
//   node e2e/cleanup.mjs                      companies created in the last 24 h
//   node e2e/cleanup.mjs --since 2026-11-01   created on or after that day (UTC)
//   node e2e/cleanup.mjs --dry-run            list only
// Only a company that looks like test data is touched: code ending in "_" + 6 upper-case hex characters (the random
// suffix the suites and e2e-login.mjs use) AND an @example.com email. run.mjs calls cleanup() after the suites.
import fs from 'fs'; import os from 'os'; import path from 'path'; import { fileURLToPath } from 'url';

const G = (process.env.E2E_GATEWAY || 'http://localhost:18080') + '/api/v1';
const TEST_CODE = /_[0-9A-F]{6}$/;
const TEST_EMAIL = /@example\.com$/i;

async function call(method, p, token) {
  const r = await fetch(G + p, { method, headers: { 'Accept-Language': 'en', Authorization: 'Bearer ' + token } });
  const j = await r.json().catch(() => ({}));
  return { s: r.status, code: j.code, data: j.data };
}

export async function cleanup({ since, dryRun = false, log = console.log } = {}) {
  const [, user, pass] = fs.readFileSync(path.join(os.homedir(), '.terp-e2e', 'erp-superadmin.txt'), 'utf8').trim().split('\n');
  const login = await fetch(G + '/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: user, password: pass }) }).then(r => r.json());
  const sa = login?.data?.accessToken;
  if (!sa) throw new Error('super admin login failed');
  const from = since.toISOString().slice(0, 10);
  const found = [];
  for (let page = 0; ; page++) {
    const r = await call('GET', `/iam/companies?createdFrom=${from}&size=100&page=${page}`, sa);
    if (r.s !== 200) throw new Error('company list failed: ' + r.s + ' ' + r.code);
    const rows = r.data?.content || r.data?.items || r.data?.data || [];
    found.push(...rows);
    if (rows.length < 100) break;
  }
  const test = found.filter(co => TEST_CODE.test(co.code || '') && TEST_EMAIL.test(co.email || '')
    && (!co.createdDate || new Date(co.createdDate) >= since));
  let deleted = 0, failed = 0;
  for (const co of test) {
    if (dryRun) { log(`  would delete ${co.code}`); continue; }
    const r = await call('DELETE', '/iam/companies/' + co.id, sa);
    if (r.s === 200) deleted++; else { failed++; log(`  could not delete ${co.code}: ${r.s} ${r.code || ''}`); }
  }
  log(`cleanup: ${test.length} test compan${test.length === 1 ? 'y' : 'ies'} found` + (dryRun ? ' (dry run)' : `, ${deleted} deleted` + (failed ? `, ${failed} failed` : '')));
  return { found: test.length, deleted, failed };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  const i = args.indexOf('--since');
  const since = i >= 0 ? new Date(args[i + 1] + 'T00:00:00Z') : new Date(Date.now() - 24 * 3600 * 1000);
  if (isNaN(since)) { console.error('--since needs a date like 2026-11-01'); process.exit(2); } // before any fetch
  const r = await cleanup({ since, dryRun: args.includes('--dry-run') });
  process.exitCode = r.failed ? 1 : 0; // not process.exit(): open fetch sockets crash Node on Windows
}

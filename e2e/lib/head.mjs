// Shared head, prepended to every suite body by e2e/run.mjs: superadmin login (password from ~/.terp-e2e,
// never in the repo), helpers c/ck/pw, and two fresh test companies A and B with their admin tokens (t = A).
// __PFX__/__pfx__/__NAME__ are replaced per suite so each run creates uniquely named, obviously fake data.
import fs from 'fs'; import os from 'os'; import path from 'path'; import crypto from 'crypto';
const [, __suUser, __suPass] = fs.readFileSync(path.join(os.homedir(), '.terp-e2e', 'erp-superadmin.txt'), 'utf8').trim().split('\n');
const G = (process.env.E2E_GATEWAY || 'http://localhost:18080') + '/api/v1';
const c = async (m, pa, b, t) => { const r = await fetch(G + pa, { method: m, headers: { 'Content-Type': 'application/json', 'Accept-Language': 'en', ...(t ? { Authorization: 'Bearer ' + t } : {}) }, body: b && JSON.stringify(b) }); const j = await r.json().catch(() => ({})); return { s: r.status, code: j.code, msg: j.message, data: j.data }; };
const pw = () => 'Cr-' + crypto.randomBytes(8).toString('base64url') + '9aA'; let n = 0, f = 0;
const ck = (name, ok, info) => { n++; if (!ok) f++; console.log((ok ? 'PASS ' : 'FAIL ') + name + '  — ' + info); };
const sa = (await c('POST', '/auth/login', { username: __suUser, password: __suPass })).data.accessToken; const x = crypto.randomBytes(3).toString('hex').toUpperCase();
const mk = async (k) => { const un = '__pfx__.admin' + k + '.' + x.toLowerCase(); const co = await c('POST', '/iam/companies', { code: '__PFX__' + k + '_' + x, name: '__NAME__ ' + k + ' ' + x, email: '__pfx__' + k + '.' + x.toLowerCase() + '@example.com', enableLogin: true, admin: { username: un, email: un + '@example.com', fullName: '__NAME__ Admin ' + k } }, sa); const np = pw(); await c('POST', '/auth/initial-password', { username: un, currentPassword: co.data.admin.temporaryPassword, newPassword: np }); return { id: co.data.company.id, admin: co.data.admin, t: (await c('POST', '/auth/login', { username: un, password: np })).data.accessToken }; };
const A = await mk('A'), B = await mk('B'); const t = A.t;


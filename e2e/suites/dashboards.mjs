// @standalone: this suite carries its own head.
// HR / employee dashboards
import fs from 'fs'; import os from 'os'; import path from 'path'; import crypto from 'crypto';
const [, u, p] = fs.readFileSync(path.join(os.homedir(), '.terp-e2e', 'erp-superadmin.txt'), 'utf8').trim().split('\n');
const G = (process.env.E2E_GATEWAY || 'http://localhost:18080') + '/api/v1';
const c = async (m, pa, b, t) => { const r = await fetch(G + pa, { method: m, headers: { 'Content-Type': 'application/json', 'Accept-Language': 'en', ...(t ? { Authorization: 'Bearer ' + t } : {}) }, body: b && JSON.stringify(b) }); const j = await r.json().catch(() => ({})); return { s: r.status, code: j.code, data: j.data }; };
let n = 0, f = 0; const ck = (name, ok, info) => { n++; if (!ok) f++; console.log((ok ? 'PASS ' : 'FAIL ') + name + '  — ' + info); };
const sa = (await c('POST', '/auth/login', { username: u, password: p })).data.accessToken; const x = crypto.randomBytes(3).toString('hex').toUpperCase();
const un = 'db.admin.' + x.toLowerCase();
const co = await c('POST', '/iam/companies', { code: 'DB_' + x, name: 'Dashboard co ' + x, email: 'db.' + x.toLowerCase() + '@example.com', enableLogin: true, admin: { username: un, email: un + '@example.com', fullName: 'Dash Admin' } }, sa);
const np = 'Db-' + crypto.randomBytes(8).toString('base64url') + '9aA';
await c('POST', '/auth/initial-password', { username: un, currentPassword: co.data.admin.temporaryPassword, newPassword: np });
const t = (await c('POST', '/auth/login', { username: un, password: np })).data.accessToken;

let r = await c('GET', '/employees/dashboard', null, t); const e = r.data || {};
ck('employees: departments, byDepartment, promotions, terminations', r.s === 200 && 'departments' in e && Array.isArray(e.byDepartment) && 'promotions' in e && 'terminations' in e, r.s + ' ' + JSON.stringify({ departments: e.departments, byDepartment: e.byDepartment, promotions: e.promotions, terminations: e.terminations }));
r = await c('GET', '/attendance/dashboard', null, t); const a = r.data || {};
ck('attendance: absentToday, onLeaveToday, notMarkedToday', r.s === 200 && 'absentToday' in a && 'onLeaveToday' in a && 'notMarkedToday' in a, r.s + ' ' + JSON.stringify({ absentToday: a.absentToday, onLeaveToday: a.onLeaveToday, notMarkedToday: a.notMarkedToday, activeEmployees: a.activeEmployees }));
r = await c('GET', '/recruitment/dashboard', null, t); const q = r.data || {};
ck('recruitment: totalCandidates, scheduledInterviews, completedOnboardings, funnel(5)', r.s === 200 && 'scheduledInterviews' in q && 'completedOnboardings' in q && Array.isArray(q.funnel) && q.funnel.length === 5, r.s + ' ' + JSON.stringify({ totalCandidates: q.totalCandidates, scheduledInterviews: q.scheduledInterviews, completedOnboardings: q.completedOnboardings, funnel: q.funnel?.map(s => s.stage + ':' + s.reached) }));
r = await c('GET', '/reports/dashboard', null, t); const d = r.data || {};
ck('report-service company dashboard carries the new fields', r.s === 200 && d.employees?.data && 'byDepartment' in d.employees.data && 'notMarkedToday' in (d.attendance?.data || {}) && Array.isArray(d.recruitment?.data?.funnel), r.s + ' parts with error: ' + Object.entries(d).filter(([, v]) => v && v.error).map(([k, v]) => k + '=' + v.error).join(','));
r = await c('GET', '/employees/dashboard?companyId=1', null, t); ck('company admin asking another company → own figures (no 403/500)', r.s === 200, r.s + ' ' + r.code);
r = await c('GET', '/recruitment/dashboard', null); ck('no token → 401', r.s === 401, r.s);
console.log('\n' + (n - f) + '/' + n + ' passed'); process.exit(f ? 1 : 0);

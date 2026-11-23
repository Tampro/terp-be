// @standalone: this suite carries its own head.
// plan module catalogue
import fs from 'fs'; import os from 'os'; import path from 'path'; import crypto from 'crypto';
const [, u, p] = fs.readFileSync(path.join(os.homedir(), '.terp-e2e', 'erp-superadmin.txt'), 'utf8').trim().split('\n');
const G = (process.env.E2E_GATEWAY || 'http://localhost:18080') + '/api/v1';
const c = async (m, pa, b, t) => { const r = await fetch(G + pa, { method: m, headers: { 'Content-Type': 'application/json', 'Accept-Language': 'en', ...(t ? { Authorization: 'Bearer ' + t } : {}) }, body: b && JSON.stringify(b) }); const j = await r.json().catch(() => ({})); return { s: r.status, code: j.code, data: j.data }; };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const pw = () => 'Pm-' + crypto.randomBytes(8).toString('base64url') + '9aA'; let n = 0, f = 0;
const ck = (name, ok, info) => { n++; if (!ok) f++; console.log((ok ? 'PASS ' : 'FAIL ') + name + '  — ' + info); };
const sa = (await c('POST', '/auth/login', { username: u, password: p })).data.accessToken; const x = crypto.randomBytes(3).toString('hex').toUpperCase();

let r = await c('GET', '/saas/plan-modules', null, sa);
// Which modules are available depends on the plan the test company gets, so only HRM (in every plan) is asserted.
ck('module catalogue: 23 entries, HRM available', r.s === 200 && r.data.length === 23 && r.data.find(m => m.code === 'HRM').available, r.s + ' n=' + r.data?.length);

const planBody = (modules) => ({ code: 'PM_' + x, name: 'Plan modules ' + x, description: 'test', monthlyPrice: 100000, yearlyPrice: null, maxUsers: null, maxEmployees: null, storageLimitGb: null, trialDays: 0, aiIntegration: false, defaultPlan: false, enabled: false, recommended: false, modules });
r = await c('POST', '/saas/admin/plans', planBody(['RECRUITMENT', 'ACCOUNTING']), sa); const planId = r.data?.id;
ck('SA creates plan with modules', r.s === 200 && JSON.stringify(r.data.modules) === JSON.stringify(['RECRUITMENT', 'ACCOUNTING']), r.s + ' ' + JSON.stringify(r.data?.modules));
r = await c('POST', '/saas/admin/plans', { ...planBody(['NOPE']), code: 'PM2_' + x }, sa); ck('unknown module → 400', r.s === 400, r.s + ' ' + r.code);

const un = 'pm.admin.' + x.toLowerCase();
const co = await c('POST', '/iam/companies', { code: 'PM_' + x, name: 'Plan modules co ' + x, email: 'pm.' + x.toLowerCase() + '@example.com', enableLogin: true, admin: { username: un, email: un + '@example.com', fullName: 'Plan Admin' } }, sa);
const np = pw(); await c('POST', '/auth/initial-password', { username: un, currentPassword: co.data.admin.temporaryPassword, newPassword: np });
const login = async () => (await c('POST', '/auth/login', { username: un, password: np })).data.accessToken;
let t = await login();
r = await c('GET', '/employees', null, t); ck('before: company admin reads employees (no plan limit)', r.s === 200, r.s + ' ' + r.code);

r = await c('PUT', '/saas/admin/companies/' + co.data.company.id + '/subscription', { planId: Number(planId), expiresAt: null }, sa);
ck('SA puts the company on the plan without HRM', r.s === 200, r.s + ' ' + r.code);
console.log('… waiting 65 s for the authority caches (IAM 30 s, services 60 s)'); await sleep(65000);
t = await login();
r = await c('GET', '/me', null, t); const perms = r.data?.permissions || [];
ck('/me: no EMPLOYEE_*/PAYROLL_*, keeps RECRUITMENT_*, IAM_*, SAAS_*, HELPDESK_*', !perms.some(q => /^(EMPLOYEE|PAYROLL|ATTENDANCE|TRAINING)_/.test(q)) && perms.some(q => q.startsWith('RECRUITMENT_')) && perms.some(q => q.startsWith('IAM_')) && perms.some(q => q.startsWith('SAAS_')) && perms.includes('HELPDESK_VIEW'), 'n=' + perms.length);
r = await c('GET', '/employees', null, t); ck('employee-service → 403 (HRM not in plan)', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', '/payroll/runs', null, t); ck('payroll-service → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', '/recruitment/postings', null, t); ck('recruitment-service still allowed (not 403)', r.s !== 403 && r.s < 500, r.s + ' ' + r.code);
r = await c('GET', '/iam/users', null, t); ck('IAM users still allowed', r.s === 200, r.s);
r = await c('GET', '/helpdesk/tickets', null, t); ck('helpdesk still allowed', r.s === 200, r.s);
r = await c('GET', '/saas/subscription', null, t); ck('subscription shows plan modules', r.s === 200 && (r.data.plan?.modules || []).includes('RECRUITMENT'), r.s + ' ' + JSON.stringify(r.data?.plan?.modules));

r = await c('PUT', '/saas/admin/plans/' + planId, planBody([]), sa); ck('SA clears the module list (= every module)', r.s === 200 && r.data.modules.length === 0, r.s);
console.log('… waiting 65 s'); await sleep(65000);
t = await login();
r = await c('GET', '/employees', null, t); ck('after: employee-service allowed again', r.s === 200, r.s + ' ' + r.code);
console.log('\n' + (n - f) + '/' + n + ' passed'); process.exit(f ? 1 : 0);

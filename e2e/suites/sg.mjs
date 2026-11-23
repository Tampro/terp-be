// small gaps: offer bonus, PDFs, login history, plan free/badge, media quota

const num = v => Number(v); const show = d => JSON.stringify(d)?.slice(0, 260);
const today = new Date().toISOString().slice(0, 10); const later = new Date(Date.now() + 30 * 864e5).toISOString().slice(0, 10);
const raw = async (pa, tk) => { const r = await fetch(G + pa, { headers: { Authorization: 'Bearer ' + tk } }); return { s: r.status, type: r.headers.get('content-type'), head: Buffer.from(await r.arrayBuffer()).subarray(0, 5).toString() }; };
let r;
// 1. offer bonus + equity
const jt = (await c('POST', '/recruitment/job-types', { name: 'Full time' }, t)).data;
const loc = (await c('POST', '/recruitment/job-locations', { name: 'HQ', city: 'Test City', status: 'ACTIVE' }, t)).data;
const job = (await c('POST', '/recruitment/job-postings', { title: 'Dev ' + x, jobTypeId: num(jt.id), locationId: num(loc.id), priority: 'LOW', skills: ['Java'], startDate: today, applicationDeadline: later, applicationType: 'EXISTING', positions: 1, description: 'Test', customQuestionIds: [] }, t)).data;
r = await c('POST', '/recruitment/candidates', { jobPostingId: num(job.id), firstName: 'Test', lastName: 'Candidate', email: 'cand.' + x.toLowerCase() + '@example.com' }, t); const cand = r.data?.candidate ?? r.data;
ck('candidate created', r.s === 200 && cand?.id, r.s + ' ' + r.code + ' ' + r.msg);
r = await c('POST', '/recruitment/offers', { candidateId: num(cand.id), jobPostingId: num(job.id), salary: 20000000, bonus: 5000000, equity: '0.1% vesting 4y', startDate: later, expirationDate: later }, t); const off = r.data;
ck('offer echoes bonus 5,000,000 and equity', r.s === 200 && num(off.bonus) === 5000000 && off.equity === '0.1% vesting 4y', r.s + ' ' + r.code + ' ' + r.msg + ' ' + show({ b: off?.bonus, e: off?.equity }));
const tpl = (await c('POST', '/recruitment/offer-templates', { name: 'With bonus', content: '<p>Salary {{salary}}, bonus {{bonus}}, equity {{equity}}.</p>', variables: 'salary,bonus,equity', status: 'ACTIVE' }, t)).data;
const L = await raw('/recruitment/offers/' + off.id + '/letter?templateId=' + tpl.id, t); ck('letter PDF with the bonus/equity variables', L.s === 200 && L.head === '%PDF-', L.s + ' ' + L.type);
r = await c('POST', '/recruitment/offers', { candidateId: num(cand.id), salary: 1, bonus: -5 }, t); ck('negative bonus → COMMON_001', r.code === 'COMMON_001', r.s + ' ' + r.code);
// 2. accounting PDFs
for (const pa of ['/accounting/reports/tax-summary/pdf?from=' + today.slice(0, 8) + '01&to=' + today, '/accounting/reports/customer-balances/pdf', '/accounting/reports/vendor-balances/pdf']) {
  const p = await raw(pa, t); ck('PDF ' + pa.split('?')[0].split('/').slice(-2).join('/'), p.s === 200 && p.head === '%PDF-' && p.type?.includes('pdf'), p.s + ' ' + p.type);
}
r = await c('GET', '/accounting/reports/tax-summary/pdf?from=' + today + '&to=2020-01-01', null, t); ck('to before from → ACC_065', r.code === 'ACC_065', r.s + ' ' + r.code);
const roles = (await c('GET', '/iam/roles', null, t)).data; const empRole = roles.find(z => z.code === 'EMPLOYEE'); const un = 'sg.emp.' + x.toLowerCase(); const p0 = pw();
const eu = await c('POST', '/iam/users', { username: un, email: un + '@example.com', fullName: 'SG Employee', roleIds: [Number(empRole.id)], password: p0 }, t); const p1 = pw();
await c('POST', '/auth/initial-password', { username: un, currentPassword: eu.data?.temporaryPassword || p0, newPassword: p1 });
const et = (await c('POST', '/auth/login', { username: un, password: p1 })).data?.accessToken;
const pe = await raw('/accounting/reports/customer-balances/pdf', et); ck('employee balances PDF → 403', pe.s === 403, String(pe.s));
// 3. login history: mobile UA, language, timezone, role at login
const lg = async tz => (await fetch(G + '/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json', 'User-Agent': 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148', 'Accept-Language': 'vi-VN,vi;q=0.9', 'X-Client-Timezone': tz }, body: JSON.stringify({ username: un, password: p1 }) })).status;
ck('login with a bad timezone still succeeds', (await lg('Mars/X')) === 200, '');
ck('login from a phone', (await lg('Asia/Ho_Chi_Minh')) === 200, '');
r = await c('GET', '/iam/login-history?keyword=' + un + '&size=5&sort=id,desc', null, t); const rows = r.data?.content || [];
const h = rows[0], hBad = rows[1];
ck('login history: MOBILE, vi-VN, Asia/Ho_Chi_Minh, role EMPLOYEE, geo null', r.s === 200 && h?.deviceType === 'MOBILE' && h.language === 'vi-VN' && h.timezone === 'Asia/Ho_Chi_Minh' && h.roleAtLogin === 'EMPLOYEE' && h.city == null && h.country == null && h.isp == null, r.s + ' ' + r.code + ' ' + show(h));
ck('bad timezone stored as null', hBad && hBad.timezone == null, show(hBad && { tz: hBad.timezone }));
r = await c('GET', '/iam/login-history?ipAddress=' + encodeURIComponent(h?.ipAddress || ''), null, t); ck('filter by exact IP', r.s === 200 && (r.data?.content || []).length >= 2 && r.data.content.every(z => z.ipAddress === h.ipAddress), r.s + ' ' + show({ n: r.data?.content?.length, ip: h?.ipAddress }));
// 4. plan free + badge (super admin)
const plans = (await c('GET', '/saas/admin/plans?size=50', null, sa)).data; const base = (plans?.content ?? plans ?? []).find(p => (p.modules || []).length > 5) ?? (plans?.content ?? plans)[0];
r = await c('POST', '/saas/admin/plans', { code: 'FREEBAD_' + x, name: 'Bad free', monthlyPrice: 1000, maxUsers: 5, storageLimitGb: 1, enabled: true, free: true, modules: base.modules }, sa); ck('free plan with a price → SAAS_041', r.code === 'SAAS_041', r.s + ' ' + r.code + ' ' + r.msg);
r = await c('POST', '/saas/admin/plans', { code: 'TINY_' + x, name: 'Tiny ' + x, monthlyPrice: 0, maxUsers: 50, storageLimitGb: 0.01, enabled: true, free: true, badge: 'Most Popular', modules: base.modules }, sa); const tiny = r.data;
ck('free plan with badge, 0.01 GB', r.s === 200 && tiny.free === true && tiny.badge === 'Most Popular', r.s + ' ' + r.code + ' ' + r.msg + ' ' + show({ f: tiny?.free, b: tiny?.badge }));
// 5. media quota: assign the 0.01 GB plan to company A, upload ~6 MB twice
r = await c('PUT', '/saas/admin/companies/' + A.id + '/subscription', { planId: num(tiny.id) }, sa); ck('company A on the tiny plan', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
r = await c('GET', '/media/stats', null, t); ck('media stats show storageLimitBytes 10737418', r.s === 200 && num(r.data.storageLimitBytes) === 10737418, r.s + ' ' + r.code + ' ' + show(r.data));
const pdf6 = Buffer.concat([Buffer.from('%PDF-1.4\n'), Buffer.alloc(6 * 1024 * 1024, 32), Buffer.from('\n%%EOF')]);
const up = async () => { const fd = new FormData(); fd.append('files', new Blob([pdf6], { type: 'application/pdf' }), 'big.pdf'); const res = await fetch(G + '/media/files', { method: 'POST', headers: { Authorization: 'Bearer ' + t, 'Accept-Language': 'en' }, body: fd }); const j = await res.json().catch(() => ({})); return { s: res.status, code: j.code, msg: j.message }; };
r = await up(); ck('first 6 MB upload accepted', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
r = await up(); ck('second 6 MB upload → MEDIA_010 (0.01 GB reached)', r.s === 400 && r.code === 'MEDIA_010' && /0\.01/.test(r.msg || ''), r.s + ' ' + r.code + ' ' + r.msg);
r = await c('GET', '/media/stats', null, B.t); ck('company B has its own limit', r.s === 200 && num(r.data.storageLimitBytes) !== 10737418, show(r.data?.storageLimitBytes));
console.log(`\n${n - f}/${n} passed`);

// @standalone: this suite carries its own head.
// CRM pipelines, leads, deals, contracts
import fs from 'fs'; import os from 'os'; import path from 'path'; import crypto from 'crypto';
const [, u, p] = fs.readFileSync(path.join(os.homedir(), '.terp-e2e', 'erp-superadmin.txt'), 'utf8').trim().split('\n');
const G = (process.env.E2E_GATEWAY || 'http://localhost:18080') + '/api/v1';
const c = async (m, pa, b, t) => { const r = await fetch(G + pa, { method: m, headers: { 'Content-Type': 'application/json', 'Accept-Language': 'en', ...(t ? { Authorization: 'Bearer ' + t } : {}) }, body: b && JSON.stringify(b) }); const j = await r.json().catch(() => ({})); return { s: r.status, code: j.code, msg: j.message, data: j.data }; };
const pw = () => 'Cr-' + crypto.randomBytes(8).toString('base64url') + '9aA'; let n = 0, f = 0;
const ck = (name, ok, info) => { n++; if (!ok) f++; console.log((ok ? 'PASS ' : 'FAIL ') + name + '  — ' + info); };
const sa = (await c('POST', '/auth/login', { username: u, password: p })).data.accessToken; const x = crypto.randomBytes(3).toString('hex').toUpperCase();
const mk = async (k) => { const un = 'cr.admin' + k + '.' + x.toLowerCase(); const co = await c('POST', '/iam/companies', { code: 'CR' + k + '_' + x, name: 'CRM ' + k + ' ' + x, email: 'cr' + k + '.' + x.toLowerCase() + '@example.com', enableLogin: true, admin: { username: un, email: un + '@example.com', fullName: 'CRM Admin ' + k } }, sa); const np = pw(); await c('POST', '/auth/initial-password', { username: un, currentPassword: co.data.admin.temporaryPassword, newPassword: np }); return { id: co.data.company.id, admin: co.data.admin, t: (await c('POST', '/auth/login', { username: un, password: np })).data.accessToken }; };
const A = await mk('A'), B = await mk('B'); const t = A.t;
const me = (await c('GET', '/me', null, t)).data; const myId = Number(me?.id ?? me?.userId ?? me?.user?.id ?? A.admin.id);
const today = new Date().toISOString().slice(0, 10); const later = new Date(Date.now() + 90 * 864e5).toISOString().slice(0, 10);

// setup
let r = await c('POST', '/crm/pipelines', { name: 'Sales' }, t); const pl = r.data; ck('pipeline created', r.s === 200 && pl.id, r.s + ' ' + r.code + ' ' + r.msg);
const ls = []; for (const nm of ['Draft', 'Sent', 'Open']) ls.push((await c('POST', '/crm/pipelines/' + pl.id + '/stages', { kind: 'LEAD', name: nm }, t)).data);
r = await c('POST', '/crm/leads', { pipelineId: Number(pl.id), name: 'Test Prospect', email: 'prospect@example.com', subject: 'Wants 20 seats', ownerUserId: myId }, t); ck('lead before deal stages: still created (lead stages exist)', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
const ds = []; for (const nm of ['Initial Contact', 'Qualification', 'Closed']) ds.push((await c('POST', '/crm/pipelines/' + pl.id + '/stages', { kind: 'DEAL', name: nm }, t)).data);
ck('stages: 3 lead + 3 deal', ls.every(z => z?.id) && ds.every(z => z?.id), ls.map(z => z?.name) + ' / ' + ds.map(z => z?.name));
const s0 = ls.map(z => Number(z.id));
r = await c('PUT', '/crm/pipelines/' + pl.id + '/stages/order', { kind: 'LEAD', stageIds: [s0[2], s0[0], s0[1]] }, t); ck('reorder lead stages', r.s === 200, r.s + ' ' + r.code);
r = await c('PUT', '/crm/pipelines/' + pl.id + '/stages/order', { kind: 'LEAD', stageIds: [s0[0]] }, t); ck('reorder with a partial list → CRM_020', r.code === 'CRM_020', r.s + ' ' + r.code);
const lab = (await c('POST', '/crm/labels', { pipelineId: Number(pl.id), name: 'Hot', color: '#EF4444' }, t)).data;
const src = (await c('POST', '/crm/sources', { name: 'Website' }, t)).data;
const pl2 = (await c('POST', '/crm/pipelines', { name: 'Other' }, t)).data; const lab2 = (await c('POST', '/crm/labels', { pipelineId: Number(pl2.id), name: 'Cold', color: '#3B82F6' }, t)).data;

// lead
r = await c('POST', '/crm/leads', { pipelineId: Number(pl.id), name: 'Test Buyer', email: 'buyer@example.com', subject: 'Needs an ERP for 30 staff', ownerUserId: myId, notes: '<p>Met at the expo</p>' }, t);
const lead = r.data?.lead; ck('lead in first lead stage (Open after reorder)', r.s === 200 && lead.stageName === 'Open', r.s + ' ' + r.code + ' ' + lead?.stageName);
r = await c('PUT', '/crm/leads/' + lead.id + '/stage', { stageId: s0[1] }, t); ck('move lead to Sent', r.s === 200 && r.data.lead.stageName === 'Sent', r.s + ' ' + r.data?.lead?.stageName);
r = await c('PUT', '/crm/leads/' + lead.id + '/stage', { stageId: Number(ds[0].id) }, t); ck('move lead to a deal stage → CRM_008', r.code === 'CRM_008', r.s + ' ' + r.code);
r = await c('PUT', '/crm/leads/' + lead.id + '/labels', { ids: [Number(lab.id)] }, t); ck('label lead', r.s === 200 && r.data.length === 1, r.s + ' ' + r.code);
r = await c('PUT', '/crm/leads/' + lead.id + '/labels', { ids: [Number(lab2.id)] }, t); ck('label of another pipeline → refused', r.s === 400 || r.s === 404, r.s + ' ' + r.code);
await c('PUT', '/crm/leads/' + lead.id + '/sources', { ids: [Number(src.id)] }, t);
r = await c('POST', '/crm/leads/' + lead.id + '/tasks', { name: 'Send brochure', date: today, priority: 'HIGH' }, t); ck('lead task', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
r = await c('POST', '/crm/leads/' + lead.id + '/calls', { subject: 'Intro call', callType: 'OUTBOUND', duration: '00:10:00' }, t); ck('lead call', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
r = await c('POST', '/crm/leads/' + lead.id + '/discussions', { comment: 'Asked for pricing' }, t); ck('lead discussion', r.s === 200, r.s + ' ' + r.code);
r = await c('POST', '/crm/leads/' + lead.id + '/emails', { to: 'buyer@example.com', subject: 'Brochure', body: 'Attached.' }, t); ck('lead email logged', r.s === 200, r.s + ' ' + r.code);
r = await c('POST', '/crm/leads/' + lead.id + '/files', { mediaFileId: 999999999 }, t); ck('file not in the media library → refused', r.s >= 400 && r.s < 500, r.s + ' ' + r.code);
r = await c('GET', '/crm/leads/board?pipelineId=' + pl.id, null, t); ck('lead board has 3 columns', r.s === 200 && r.data.length === 3, r.s + ' ' + r.data?.map(z => z.name + ':' + z.leads.length));
r = await c('GET', '/crm/leads?pipelineId=' + pl.id, null, t); ck('lead list with task progress', r.s === 200 && r.data.content.length === 2, r.s + ' ' + r.data?.content?.length);

// convert
r = await c('POST', '/crm/leads/' + lead.id + '/convert', { price: 15000000, newClient: { companyName: 'Test Buyer Co ' + x, contactPerson: 'Test Buyer', email: 'buyer@example.com' }, copy: ['TASKS', 'NOTES', 'SOURCES', 'DISCUSSIONS'] }, t);
const conv = r.data; ck('convert with a new client → deal + customer', r.s === 200 && conv.dealId && conv.customerId, r.s + ' ' + r.code + ' ' + JSON.stringify(conv));
r = await c('GET', '/accounting/customers/' + conv.customerId, null, t); ck('customer exists in accounting', r.s === 200 && r.data.companyName === 'Test Buyer Co ' + x, r.s + ' ' + r.data?.customerCode);
r = await c('POST', '/crm/leads/' + lead.id + '/convert', { customerId: Number(conv.customerId), copy: [] }, t); ck('second convert → CRM_011', r.code === 'CRM_011', r.s + ' ' + r.code + ' ' + r.msg);
r = await c('GET', '/crm/deals/' + conv.dealId, null, t); const dd = r.data;
ck('deal: first deal stage, client, copied task/source/notes', r.s === 200 && dd.deal.stageName === 'Initial Contact' && dd.activity.clients.length === 1 && dd.activity.tasks.length === 1 && dd.activity.sources.length === 1 && dd.notes === '<p>Met at the expo</p>' && dd.activity.calls.length === 0,
  r.s + ' ' + dd?.deal?.stageName + ' clients=' + dd?.activity?.clients?.length + ' tasks=' + dd?.activity?.tasks?.length + ' calls=' + dd?.activity?.calls?.length);
r = await c('PUT', '/crm/deals/' + conv.dealId + '/status', { status: 'WON' }, t); ck('deal WON', r.s === 200, r.s + ' ' + r.code);
r = await c('GET', '/crm/deals/board?pipelineId=' + pl.id, null, t); ck('deal board totals', r.s === 200 && Number(r.data[0].total) === 15000000, r.s + ' ' + JSON.stringify(r.data?.map(z => z.name + ':' + z.count + '/' + z.total)));
r = await c('DELETE', '/crm/stages/' + ds[0].id, null, t); ck('delete deal stage in use → CRM_009', r.code === 'CRM_009', r.s + ' ' + r.code);
r = await c('GET', '/crm/reports/leads?pipelineId=' + pl.id, null, t); ck('lead report: 2 leads, 1 converted, 50%', r.s === 200 && r.data.total === 2 && r.data.converted === 1 && r.data.conversionPercent === 50, r.s + ' ' + r.code + ' ' + JSON.stringify(r.data && { t: r.data.total, c: r.data.converted, p: r.data.conversionPercent }));
r = await c('GET', '/crm/reports/deals?pipelineId=' + pl.id, null, t); ck('deal report: WON 1', r.s === 200 && r.data.byStatus.WON.count === 1, r.s + ' ' + r.code + ' ' + JSON.stringify(r.data?.byStatus));

// contracts
const ct = (await c('POST', '/crm/contract-types', { name: 'Service', active: true }, t)).data;
r = await c('POST', '/crm/contracts', { subject: 'Annual support', customerId: Number(conv.customerId), contractTypeId: Number(ct.id), value: 24000000, startDate: today, endDate: later }, t);
const con = r.data?.contract; ck('contract number CON0001', r.s === 200 && con.number === 'CON0001', r.s + ' ' + r.code + ' ' + r.msg + ' ' + con?.number);
r = await c('POST', '/crm/contracts', { subject: 'Bad dates', customerId: Number(conv.customerId), contractTypeId: Number(ct.id), startDate: later, endDate: today }, t); ck('end before start → CTR_006', r.code === 'CTR_006', r.s + ' ' + r.code);
r = await c('POST', '/crm/contracts/' + con.id + '/renewals', { startDate: later, endDate: later, value: 25000000, status: 'ACTIVE' }, t); ck('renewal', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
r = await c('POST', '/crm/contracts/' + con.id + '/comments', { message: 'Signed copy received' }, t); ck('contract comment', r.s === 200, r.s + ' ' + r.code);
r = await c('POST', '/crm/contracts/' + con.id + '/duplicate', null, t); ck('duplicate → CON0002', r.s === 200 && r.data.contract.number === 'CON0002', r.s + ' ' + r.code + ' ' + r.data?.contract?.number);
r = await c('GET', '/crm/contracts/' + con.id, null, t); ck('contract owner = creator (id + name)', r.s === 200 && !!r.data.contract.ownerId && !!r.data.contract.ownerName, r.s + ' ' + r.data?.contract?.ownerId + ' ' + r.data?.contract?.ownerName);
r = await c('GET', '/crm/contracts?size=50', null, t); ck('contract list rows carry ownerName', r.s === 200 && r.data.content.every(x => !!x.ownerName), r.s + ' ' + r.data?.content?.map(x => x.ownerName).join(','));
r = await c('GET', '/crm/contract-types', null, t); ck('contract type contractCount = 2', r.s === 200 && r.data.find(x => x.id === ct.id)?.contractCount === 2, r.s + ' ' + JSON.stringify(r.data?.map(x => [x.name, x.contractCount])));
r = await c('GET', '/crm/contracts/status-counts', null, t); ck('status counts total 2', r.s === 200 && r.data.total === 2, r.s + ' ' + JSON.stringify(r.data));

// isolation & permissions
r = await c('GET', '/crm/leads/' + lead.id, null, B.t); ck('company B reads A lead → 404 CRM_006', r.s === 404 && r.code === 'CRM_006', r.s + ' ' + r.code);
r = await c('POST', '/crm/leads/' + lead.id + '/convert', { customerId: 1, copy: [] }, B.t); ck('company B converts A lead → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('PUT', '/crm/deals/' + conv.dealId + '/status', { status: 'LOSS' }, B.t); ck('company B changes A deal status → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('GET', '/crm/contracts/' + con.id, null, B.t); ck('company B reads A contract → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('POST', '/crm/contracts/' + con.id + '/comments', { message: 'x' }, B.t); ck('company B comments A contract → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('POST', '/crm/contracts', { subject: 'Steal', customerId: Number(conv.customerId), contractTypeId: Number(ct.id), startDate: today, endDate: later }, B.t); ck('company B contract with A customer/type → 404', r.s === 404, r.s + ' ' + r.code);
const roles = (await c('GET', '/iam/roles', null, t)).data; const emp = roles.find(z => z.code === 'EMPLOYEE'); const ep = pw();
const eu = await c('POST', '/iam/users', { username: 'cr.emp.' + x.toLowerCase(), email: 'cr.emp.' + x.toLowerCase() + '@example.com', fullName: 'CRM Employee', roleIds: [Number(emp.id)], password: ep }, t); const ep2 = pw();
await c('POST', '/auth/initial-password', { username: 'cr.emp.' + x.toLowerCase(), currentPassword: eu.data?.temporaryPassword || ep, newPassword: ep2 });
const et = (await c('POST', '/auth/login', { username: 'cr.emp.' + x.toLowerCase(), password: ep2 })).data?.accessToken;
r = await c('GET', '/crm/leads', null, et); ck('employee without CRM_VIEW → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', '/crm/contracts', null, et); ck('employee without CRM_CONTRACT_VIEW → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', '/crm/leads'); ck('no token → 401', r.s === 401, r.s);
r = await c('GET', '/crm/contracts'); ck('no token contracts → 401', r.s === 401, r.s);
console.log('\n' + (n - f) + '/' + n + ' passed'); process.exit(f ? 1 : 0);

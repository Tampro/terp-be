// notification-service + accounting outbox

const num = v => Number(v); const show = d => JSON.stringify(d)?.slice(0, 280);
const today = new Date().toISOString().slice(0, 10); const due = new Date(Date.now() + 30 * 864e5).toISOString().slice(0, 10);
const sleep = ms => new Promise(res => setTimeout(res, ms));
const waitFor = async (fn, ok, ms = 45000) => { const t0 = Date.now(); let v; while (Date.now() - t0 < ms) { v = await fn(); if (ok(v)) return v; await sleep(2000); } return v; };
const deliveries = async (q = '', tk = t) => (await c('GET', '/notifications/deliveries?size=100' + q, null, tk)).data?.content || [];
let r = await c('GET', '/accounting/chart-of-accounts?size=5', null, t);
// ---------- notification: support ticket → EMAIL (company SMTP points at a host that cannot resolve)
r = await c('PUT', '/settings/company/email', { enabled: true, host: 'smtp.no-such-host.invalid', port: 587, username: 'test', password: 'fake-smtp-pass', encryption: 'TLS', fromAddress: 'noreply@example.com', fromName: 'Test Co' }, t);
ck('company SMTP set to an unresolvable host', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
const wh = (await c('POST', '/settings/webhooks', { eventCode: 'NEW_JOB_CANDIDATE', url: 'https://hooks.no-such-host.invalid/erp', active: true, secret: 'fake-signing-secret-0001' }, t)).data;
ck('webhook on NEW_JOB_CANDIDATE (unresolvable host)', !!wh?.id, show(wh));
const cat = (await c('POST', '/support/ticket-categories', { name: 'General', color: '#123456' }, t)).data;
r = await c('POST', '/support/tickets', { accountType: 'CUSTOM', name: 'Test Customer', email: 'cust.' + x.toLowerCase() + '@example.com', categoryId: Number(cat.id), subject: 'Need help', description: 'Hello' }, t);
ck('ticket created', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
let ds = await waitFor(() => deliveries('&eventType=support.ticket.created'), v => v.some(z => z.channel === 'EMAIL' && z.attempts > 0));
const tEmail = ds.find(z => z.channel === 'EMAIL');
ck('ticket → EMAIL delivery recorded, target masked', !!tEmail && tEmail.target === '*@example.com', show(ds));
ck('EMAIL attempted and failed on SMTP (no external send)', tEmail && ['PENDING', 'FAILED'].includes(tEmail.status) && /SMTP|CONNECTION|TIMEOUT/.test(tEmail.lastErrorCode || ''), show(tEmail));
ck('no Slack/Telegram delivery while integrations are off', !ds.some(z => z.channel === 'SLACK' || z.channel === 'TELEGRAM'), show(ds.map(z => z.channel)));
// toggle off → SKIPPED
await c('PUT', '/settings/email-notifications/SUPPORT_TICKET_CREATED', { enabled: false }, t);
await c('POST', '/support/tickets', { accountType: 'CUSTOM', name: 'Test Customer', email: 'cust2.' + x.toLowerCase() + '@example.com', categoryId: Number(cat.id), subject: 'Second', description: 'Hello' }, t);
ds = await waitFor(() => deliveries('&eventType=support.ticket.created&channel=EMAIL'), v => v.length >= 2 && v.every(z => z.attempts > 0 || z.status === 'SKIPPED'));
ck('toggle off → EMAIL SKIPPED EMAIL_DISABLED', ds.some(z => z.status === 'SKIPPED' && z.lastErrorCode === 'EMAIL_DISABLED'), show(ds.map(z => [z.status, z.lastErrorCode])));
// recruitment apply → EMAIL + WEBHOOK (URL refused / connection error, no egress)
const slug = 'nt-' + x.toLowerCase();
await c('PUT', '/recruitment/career-settings', { slug, enabled: true, displayName: 'Test Careers' }, t);
const jt = (await c('POST', '/recruitment/job-types', { name: 'Full time' }, t)).data;
const loc = (await c('POST', '/recruitment/job-locations', { name: 'HQ', city: 'Test City', status: 'ACTIVE' }, t)).data;
const job = (await c('POST', '/recruitment/job-postings', { title: 'Tester ' + x, jobTypeId: num(jt.id), locationId: num(loc.id), priority: 'LOW', skills: ['QA'], startDate: today, applicationDeadline: due, applicationType: 'EXISTING', positions: 1, description: 'Test', customQuestionIds: [] }, t)).data;
await c('PUT', '/recruitment/job-postings/' + job.id + '/publish', null, t);
const fd = new FormData(); fd.append('application', new Blob([JSON.stringify({ firstName: 'Test', lastName: 'Applicant', email: 'app.' + x.toLowerCase() + '@example.com', termsAccepted: true, answers: [] })], { type: 'application/json' })); fd.append('resume', new Blob([Buffer.from('%PDF-1.4\n%%EOF')], { type: 'application/pdf' }), 'cv.pdf');
r = await fetch(G + '/public/career/' + slug + '/jobs/' + job.id + '/apply', { method: 'POST', body: fd }); ck('applied', r.status === 200, String(r.status));
ds = await waitFor(() => deliveries('&eventType=recruitment.application.received'), v => v.some(z => z.channel === 'WEBHOOK' && z.attempts > 0) && v.some(z => z.channel === 'EMAIL'));
const wd = ds.find(z => z.channel === 'WEBHOOK');
ck('apply → WEBHOOK delivery, host only, refused without egress', wd && wd.target === 'hooks.no-such-host.invalid' && ['FAILED', 'PENDING'].includes(wd.status) && /URL_REFUSED|CONNECTION_ERROR/.test(wd.lastErrorCode || ''), show(wd));
ck('apply → EMAIL delivery', ds.some(z => z.channel === 'EMAIL'), show(ds.map(z => z.channel)));
// crm lead → owner via IAM internal contact
const pipe = (await c('POST', '/crm/pipelines', { name: 'Sales ' + x }, t)).data;
const st = []; for (const nm of ['New', 'Contacted']) st.push((await c('POST', '/crm/pipelines/' + pipe.id + '/stages', { kind: 'LEAD', name: nm }, t)).data); await c('POST', '/crm/pipelines/' + pipe.id + '/stages', { kind: 'DEAL', name: 'Open' }, t);
const me = (await c('GET', '/me', null, t)).data;
r = await c('POST', '/crm/leads', { name: 'Test Lead', subject: 'Interest', email: 'lead@example.com', pipelineId: num(pipe?.id), ownerUserId: num(me?.id ?? me?.userId ?? me?.user?.id) }, t);
ck('lead created (owner = admin)', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(st));
ds = await waitFor(() => deliveries('&eventType=crm.lead.assigned'), v => v.some(z => z.attempts > 0 || z.status === 'SKIPPED'));
ck('lead assigned → EMAIL to the owner (resolved via IAM internal)', ds.some(z => z.channel === 'EMAIL' && z.target?.startsWith('*@')), show(ds));
// idempotency + isolation + retry
const all = await deliveries(); const ids = all.map(z => z.eventId + z.channel + z.target);
ck('no duplicate delivery per event+channel+target', new Set(ids).size === ids.length, all.length + ' rows');
const one = all.find(z => z.status === 'FAILED') || all[0];
r = await c('GET', '/notifications/deliveries/' + one.id, null, B.t); ck('company B reads A delivery → 404 NTF_002', r.s === 404 && r.code === 'NTF_002', r.s + ' ' + r.code);
r = await c('GET', '/notifications/deliveries', null, B.t); ck("company B list has none of A's", (r.data?.content || []).every(z => z.companyId !== all[0].companyId), r.data?.content?.length + ' rows');
const sent = all.find(z => z.status !== 'FAILED');
r = await c('POST', '/notifications/deliveries/' + sent.id + '/retry', null, t); ck('retry a non-FAILED delivery → NTF_003', r.code === 'NTF_003', r.s + ' ' + r.code);
r = await c('GET', '/api/v1/internal/notification-config?companyId=1'.replace('/api/v1', ''), null, t); ck('gateway does not expose /internal (user token)', r.s === 404 || r.s === 403, r.s + ' ' + r.code);
r = await fetch('http://localhost:18081/api/v1/internal/notification-config?companyId=1', { headers: { Authorization: 'Bearer ' + t } }); ck('IAM internal refuses a user token → 403', r.status === 403, String(r.status));
// ---------- accounting outbox: sales invoice post → journalStatus PENDING → POSTED
const tax = (await c('POST', '/inventory/taxes', { name: 'VAT 10', rate: 10 }, t)).data;
const unit = (await c('POST', '/inventory/units', { name: 'Piece' }, t)).data;
const icat = (await c('POST', '/inventory/categories', { name: 'Goods', color: '#3B82F6' }, t)).data;
const whs = (await c('POST', '/inventory/warehouses', { name: 'Main', address: '1 Test Street', city: 'Test City', zipCode: '70000', active: true }, t)).data;
const item = (await c('POST', '/inventory/items', { type: 'PRODUCT', name: 'Widget', sku: 'N-' + x, taxId: Number(tax.id), categoryId: Number(icat.id), unitId: Number(unit.id), salePrice: 100, purchasePrice: 60 }, t)).data;
const ven = (await c('POST', '/accounting/vendors', { companyName: 'Test Supplier ' + x, contactPerson: 'Test Contact', email: 'supplier@example.com' }, t)).data;
const cust = (await c('POST', '/accounting/customers', { companyName: 'Test Buyer ' + x, contactPerson: 'Test Buyer', email: 'buyer@example.com' }, t)).data;
r = await c('POST', '/purchase/invoices', { vendorId: Number(ven.id), warehouseId: Number(whs.id), invoiceDate: today, dueDate: due, lines: [{ itemId: Number(item.id), quantity: 10 }] }, t);
const pi = r.data?.invoice; r = await c('POST', '/purchase/invoices/' + pi.id + '/post', null, t);
ck('purchase post → POSTED, journal PENDING or POSTED', r.data?.invoice?.status === 'POSTED' && ['PENDING', 'POSTED'].includes(r.data?.invoice?.journalStatus), r.s + ' ' + r.code + ' ' + r.msg + ' ' + r.data?.invoice?.journalStatus);
let pj = await waitFor(async () => (await c('GET', '/purchase/invoices/' + pi.id, null, t)).data?.invoice?.journalStatus, v => v === 'POSTED');
ck('purchase journal → POSTED asynchronously', pj === 'POSTED', String(pj));
r = await c('POST', '/sales/invoices', { customerId: Number(cust.id), warehouseId: Number(whs.id), docDate: today, dueDate: due, paymentTerms: 'Net 30', lines: [{ itemId: Number(item.id), quantity: 2 }] }, t); const si = r.data?.document;
r = await c('POST', '/sales/invoices/' + si.id + '/post', null, t); ck('sales post → POSTED, journal PENDING', r.data?.document?.status === 'POSTED' && r.data?.document?.journalStatus === 'PENDING', r.s + ' ' + r.code + ' ' + r.msg + ' ' + r.data?.document?.journalStatus);
let sj = await waitFor(async () => (await c('GET', '/sales/invoices/' + si.id, null, t)).data?.document?.journalStatus, v => v === 'POSTED');
ck('sales journal → POSTED asynchronously', sj === 'POSTED', String(sj));
r = await c('GET', '/accounting/journal-entries?sourceType=SALES_INVOICE', null, t); const je = (r.data?.content || []);
ck('exactly one SALES_INVOICE entry', je.length === 1, show(je.map(z => z.number)));
r = await c('GET', '/sales/invoices?journalStatus=POSTED', null, t); ck('journalStatus filter', r.data?.content?.length === 1, show(r.data?.content?.map(z => z.journalStatus)));
r = await c('POST', '/sales/invoices/' + si.id + '/journal/retry', null, t); ck('retry a POSTED journal → SAL_025', r.code === 'SAL_025', r.s + ' ' + r.code);
r = await c('POST', '/sales/invoices/' + si.id + '/journal/retry', null, B.t); ck('company B retries A invoice → 404', r.s === 404, r.s + ' ' + r.code);
// payment clear → invoice PAID via event
const bank = (await c('POST', '/accounting/bank-accounts', { holderName: 'Test Shop', bankName: 'Test Bank', accountNumber: 'NT' + x, openingBalance: 1000000, active: true }, t)).data;
r = await c('POST', '/accounting/customer-payments', { partyId: Number(cust.id), bankAccountId: Number(bank.id), paymentDate: today, reference: 'PAY1', allocations: [{ invoiceId: Number(si.id), amount: 220 }] }, t); const pay = r.data;
ck('customer payment created', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(r.data));
r = await c('POST', '/accounting/customer-payments/' + (pay?.id ?? pay?.payment?.id) + '/clear', null, t); ck('payment cleared', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
const paid = await waitFor(async () => (await c('GET', '/sales/invoices/' + si.id, null, t)).data?.document, v => v?.status === 'PAID');
ck('invoice → PAID asynchronously, balance 0', paid?.status === 'PAID' && num(paid.balance) === 0, show({ s: paid?.status, b: paid?.balance }));
ds = await waitFor(() => deliveries('&eventType=accounting.customer_payment.cleared'), v => v.length > 0);
ck('payment cleared → EMAIL delivery to the customer', ds.some(z => z.channel === 'EMAIL'), show(ds));
// FAILED path: close last year, post an invoice dated in it → refused synchronously (validate-only)
const LY = +today.slice(0, 4) - 1;
await c('POST', '/accounting/year-end-closes', { financialYear: LY, closingDate: LY + '-12-31' }, t);
r = await c('POST', '/sales/invoices', { customerId: Number(cust.id), warehouseId: Number(whs.id), docDate: LY + '-06-01', dueDate: LY + '-07-01', paymentTerms: 'Net 30', lines: [{ itemId: Number(item.id) }] }, t); const old = r.data?.document;
r = await c('POST', '/sales/invoices/' + old.id + '/post', null, t); ck('closed year still refused at once → ACC_070, DRAFT', r.code === 'ACC_070', r.s + ' ' + r.code);
// queues drained
console.log(`\n${n - f}/${n} passed`);

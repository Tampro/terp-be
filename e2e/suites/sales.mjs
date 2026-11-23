// @standalone: this suite carries its own head.
// quotations, proposals, sales invoices, returns
import fs from 'fs'; import os from 'os'; import path from 'path'; import crypto from 'crypto';
const [, u, p] = fs.readFileSync(path.join(os.homedir(), '.terp-e2e', 'erp-superadmin.txt'), 'utf8').trim().split('\n');
const G = (process.env.E2E_GATEWAY || 'http://localhost:18080') + '/api/v1';
const c = async (m, pa, b, t) => { const r = await fetch(G + pa, { method: m, headers: { 'Content-Type': 'application/json', 'Accept-Language': 'en', ...(t ? { Authorization: 'Bearer ' + t } : {}) }, body: b && JSON.stringify(b) }); const j = await r.json().catch(() => ({})); return { s: r.status, code: j.code, msg: j.message, data: j.data }; };
const pw = () => 'Sl-' + crypto.randomBytes(8).toString('base64url') + '9aA'; let n = 0, f = 0;
const ck = (name, ok, info) => { n++; if (!ok) f++; console.log((ok ? 'PASS ' : 'FAIL ') + name + '  — ' + info); };
const num = (v) => Number(v);
const sa = (await c('POST', '/auth/login', { username: u, password: p })).data.accessToken; const x = crypto.randomBytes(3).toString('hex').toUpperCase();
const mk = async (k) => { const un = 'sl.admin' + k + '.' + x.toLowerCase(); const co = await c('POST', '/iam/companies', { code: 'SL' + k + '_' + x, name: 'Sales ' + k + ' ' + x, email: 'sl' + k + '.' + x.toLowerCase() + '@example.com', enableLogin: true, admin: { username: un, email: un + '@example.com', fullName: 'Sales Admin ' + k } }, sa); const np = pw(); await c('POST', '/auth/initial-password', { username: un, currentPassword: co.data.admin.temporaryPassword, newPassword: np }); return { id: co.data.company.id, t: (await c('POST', '/auth/login', { username: un, password: np })).data.accessToken }; };
const A = await mk('A'), B = await mk('B'); const t = A.t;

// catalogue
const tax = (await c('POST', '/inventory/taxes', { name: 'VAT 10', rate: 10 }, t)).data;
const unit = (await c('POST', '/inventory/units', { name: 'Piece' }, t)).data;
const cat = (await c('POST', '/inventory/categories', { name: 'Gadgets', color: '#3B82F6' }, t)).data;
const wh = (await c('POST', '/inventory/warehouses', { name: 'Main', address: '1 Test Street', city: 'Test City', zipCode: '70000', active: true }, t)).data;
let r = await c('POST', '/inventory/items', { type: 'PRODUCT', name: 'Test Widget', sku: 'W-' + x, taxId: Number(tax.id), categoryId: Number(cat.id), unitId: Number(unit.id), salePrice: 100, purchasePrice: 60, openingWarehouseId: Number(wh.id), openingQuantity: 10 }, t);
const item = r.data; ck('inventory: product with opening stock 10', r.s === 200 && num(item.quantity) === 10, r.s + ' ' + r.code + ' qty=' + item?.quantity);
const svc = (await c('POST', '/inventory/items', { type: 'SERVICE', name: 'Setup', generateSku: true, categoryId: Number(cat.id), unitId: Number(unit.id), salePrice: 50, purchasePrice: 0 }, t)).data;
r = await c('POST', '/accounting/customers', { companyName: 'Test Customer Co', contactPerson: 'Test Contact', email: 'buyer@example.com', billing: { name: 'Test Customer Co', address: '2 Test Road', city: 'Test City' }, shippingSameAsBilling: true }, t);
const cust = r.data; ck('accounting: customer CUST-0001', r.s === 200 && cust.customerCode === 'CUST-0001', r.s + ' ' + cust?.customerCode);

const today = new Date().toISOString().slice(0, 10); const due = new Date(Date.now() + 30 * 864e5).toISOString().slice(0, 10);
const form = (lines, extra = {}) => ({ customerId: Number(cust.id), warehouseId: Number(wh.id), docDate: today, dueDate: due, paymentTerms: 'Net 30', lines, ...extra });
// quotation
r = await c('POST', '/sales/quotations', form([{ itemId: Number(item.id), quantity: 2, discountPct: 10 }, { itemId: Number(svc.id) }]), t); const q = r.data?.document;
ck('quotation: QT number, totals 200−20+18 + 50 = 248', r.s === 200 && /^QT-\d{4}-\d{2}-\d{3}$/.test(q.number) && num(q.total) === 248 && num(q.tax) === 18, r.s + ' ' + r.code + ' ' + q?.number + ' total=' + q?.total);
r = await c('POST', '/sales/quotations/' + q.id + '/accept', null, t); ck('accept a draft → SAL_003', r.code === 'SAL_003', r.s + ' ' + r.code);
await c('POST', '/sales/quotations/' + q.id + '/send', null, t);
r = await c('POST', '/sales/quotations/' + q.id + '/versions', null, t); ck('new version of a sent quotation: version 2', r.s === 200 && r.data.document.versionNo === 2 && r.data.document.versionOfId === q.id, r.s + ' v=' + r.data?.document?.versionNo);
r = await c('PUT', '/sales/quotations/' + q.id, form([{ itemId: Number(item.id) }]), t); ck('edit a sent quotation → SAL_002', r.code === 'SAL_002', r.s + ' ' + r.code);
await c('POST', '/sales/quotations/' + q.id + '/accept', null, t);
r = await c('POST', '/sales/quotations/' + q.id + '/convert', null, t); const inv1 = r.data?.document;
ck('convert accepted quotation → draft SI invoice', r.s === 200 && inv1.kind === 'INVOICE' && inv1.status === 'DRAFT' && /^SI-/.test(inv1.number) && inv1.convertedFromId === q.id, r.s + ' ' + inv1?.number);
r = await c('POST', '/sales/quotations/' + q.id + '/convert', null, t); ck('second conversion → SAL_005', r.code === 'SAL_005', r.s + ' ' + r.code + ' ' + r.msg);
// proposal
r = await c('POST', '/sales/proposals', form([{ itemId: Number(svc.id) }]), t); const sp = r.data?.document; ck('proposal: SP number', r.s === 200 && /^SP-/.test(sp.number), r.s + ' ' + sp?.number);
await c('POST', '/sales/proposals/' + sp.id + '/send', null, t); await c('POST', '/sales/proposals/' + sp.id + '/accept', null, t);
r = await c('POST', '/sales/proposals/' + sp.id + '/convert', null, t); ck('services-only proposal → SERVICE invoice', r.s === 200 && r.data.document.invoiceType === 'SERVICE', r.s + ' ' + r.data?.document?.invoiceType);
r = await c('GET', '/sales/proposals/' + sp.id, null, t); ck('proposal keeps ACCEPTED after conversion', r.data.document.status === 'ACCEPTED', r.data.document.status);
// invoice post
r = await c('POST', '/sales/invoices/' + inv1.id + '/post', null, t); ck('post product invoice → POSTED', r.s === 200 && r.data.document.status === 'POSTED' && num(r.data.document.balance) === 248, r.s + ' ' + r.code);
r = await c('GET', '/inventory/stock?itemId=' + item.id, null, t); ck('stock 10 → 8 after posting', num(r.data[0].quantity) === 8, JSON.stringify(r.data));
r = await c('POST', '/sales/invoices/' + inv1.id + '/post', null, t); ck('post again → SAL_002 (stock untouched)', r.code === 'SAL_002', r.s + ' ' + r.code);
r = await c('POST', '/sales/invoices', form([{ itemId: Number(item.id), quantity: 100 }]), t); const big = r.data.document;
r = await c('POST', '/sales/invoices/' + big.id + '/post', null, t); ck('not enough stock → INV_017 relayed, stays DRAFT', r.s === 400 && r.code === 'INV_017', r.s + ' ' + r.code + ' ' + r.msg);
r = await c('GET', '/sales/invoices/' + big.id, null, t); ck('big invoice still DRAFT, stock still 8', r.data.document.status === 'DRAFT' && num((await c('GET', '/inventory/stock?itemId=' + item.id, null, t)).data[0].quantity) === 8, r.data.document.status);
r = await c('POST', '/sales/invoices', form([{ itemId: Number(item.id) }], { invoiceType: 'SERVICE', warehouseId: null }), t); ck('service-wise invoice with a product → SAL_008', r.code === 'SAL_008', r.s + ' ' + r.code);
r = await c('POST', '/sales/invoices', form([{ itemId: Number(item.id) }], { warehouseId: null }), t); r = await c('POST', '/sales/invoices/' + r.data.document.id + '/post', null, t); ck('product invoice without warehouse → SAL_009', r.code === 'SAL_009', r.s + ' ' + r.code);
// returns
r = await c('GET', '/sales/returns/returnable?invoiceId=' + inv1.id, null, t); const rl = r.data.find(z => z.itemId === item.id);
ck('returnable lines of the posted invoice', r.s === 200 && num(rl.returnable) === 2, JSON.stringify(r.data?.map(z => z.itemName + ':' + z.returnable)));
r = await c('POST', '/sales/returns', { invoiceId: Number(inv1.id), returnDate: today, reason: 'Excess quantity', lines: [{ invoiceLineId: Number(rl.invoiceLineId), quantity: 3 }] }, t); ck('return 3 of 2 → SAL_011', r.code === 'SAL_011', r.s + ' ' + r.code);
r = await c('POST', '/sales/returns', { invoiceId: Number(inv1.id), returnDate: today, reason: 'Excess quantity', lines: [{ invoiceLineId: Number(rl.invoiceLineId), quantity: 1 }] }, t); const ret = r.data?.ret;
ck('return SR draft, 1 × 100 −10% +10% = 99', r.s === 200 && /^SR-/.test(ret.number) && num(ret.total) === 99, r.s + ' ' + r.code + ' ' + ret?.number + ' total=' + ret?.total);
r = await c('POST', '/sales/returns/' + ret.id + '/complete', null, t); ck('complete before approve → SAL_014', r.code === 'SAL_014', r.s + ' ' + r.code);
await c('POST', '/sales/returns/' + ret.id + '/approve', null, t); r = await c('POST', '/sales/returns/' + ret.id + '/complete', null, t);
ck('approve + complete → COMPLETED', r.s === 200 && r.data.ret.status === 'COMPLETED', r.s + ' ' + r.code);
r = await c('GET', '/inventory/stock?itemId=' + item.id, null, t); ck('stock 8 → 9 after the return', num(r.data[0].quantity) === 9, JSON.stringify(r.data));
r = await c('GET', '/sales/returns/returnable?invoiceId=' + inv1.id, null, t); ck('returnable now 1', num(r.data.find(z => z.itemId === item.id).returnable) === 1, '');
// isolation & permissions
r = await c('GET', '/sales/invoices/' + inv1.id, null, B.t); ck('company B reads A invoice → 404 SAL_001', r.s === 404 && r.code === 'SAL_001', r.s + ' ' + r.code);
r = await c('POST', '/sales/invoices/' + inv1.id + '/post', null, B.t); ck('company B posts A invoice → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('GET', '/inventory/items/' + item.id, null, B.t); ck('company B reads A item → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('GET', '/accounting/customers/' + cust.id, null, B.t); ck('company B reads A customer → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('POST', '/sales/invoices', form([{ itemId: Number(item.id) }]), B.t); ck('company B invoice with A customer → 404 relayed', r.s === 404, r.s + ' ' + r.code);
r = await c('GET', '/sales/invoices?overdue=true', null, t); ck('overdue filter answers (none yet)', r.s === 200 && r.data.content.length === 0, r.s);
r = await c('GET', '/sales/invoices/status-counts', null, t); ck('invoice status counts', r.s === 200 && r.data.POSTED === 1, JSON.stringify(r.data));
const roles = (await c('GET', '/iam/roles', null, t)).data; const emp = roles.find(z => z.code === 'EMPLOYEE'); const ep = pw();
const eu = await c('POST', '/iam/users', { username: 'sl.emp.' + x.toLowerCase(), email: 'sl.emp.' + x.toLowerCase() + '@example.com', fullName: 'Sales Employee', roleIds: [Number(emp.id)], password: ep }, t); const ep2 = pw();
await c('POST', '/auth/initial-password', { username: 'sl.emp.' + x.toLowerCase(), currentPassword: eu.data.temporaryPassword || ep, newPassword: ep2 });
const et = (await c('POST', '/auth/login', { username: 'sl.emp.' + x.toLowerCase(), password: ep2 })).data.accessToken;
r = await c('GET', '/sales/invoices', null, et); ck('employee without SALES_VIEW → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('POST', '/inventory/stock/moves', { sourceType: 'SALES_INVOICE', sourceId: 1, lines: [{ itemId: Number(item.id), warehouseId: Number(wh.id), quantity: -1 }] }, et); ck('employee stock move → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', '/sales/quotations', null); ck('no token → 401', r.s === 401, r.s);
console.log('\n' + (n - f) + '/' + n + ' passed'); process.exit(f ? 1 : 0);

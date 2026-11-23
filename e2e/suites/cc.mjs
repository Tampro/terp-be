// automatic journal postings (manual §20)

const num = v => Number(v); const show = d => JSON.stringify(d)?.slice(0, 260);
const today = new Date().toISOString().slice(0, 10); const ym = today.slice(0, 7); const Y = +today.slice(0, 4); const LY = Y - 1;
const monthEnd = new Date(Date.UTC(Y, +ym.slice(5, 7), 0)).toISOString().slice(0, 10); const due = new Date(Date.now() + 30 * 864e5).toISOString().slice(0, 10);
const tb = async () => { const r = await c('GET', '/accounting/ledger/trial-balance?from=' + Y + '-01-01&to=' + Y + '-12-31', null, t); const m = {}; for (const row of r.data?.rows || []) m[row.code] = num(row.periodDebit) - num(row.periodCredit); m._balanced = r.data?.balanced; return m; };
const d = (a, b, code) => +((b[code] || 0) - (a[code] || 0)).toFixed(2);
const stock = async (item, wh) => num(((await c('GET', '/inventory/stock?itemId=' + item, null, t)).data || []).find(z => String(z.warehouseId) === String(wh))?.quantity ?? 0);
const sleep = ms => new Promise(res => setTimeout(res, ms));
const settle = async (pa, pick) => { for (let i = 0; i < 25; i++) { const v = pick((await c('GET', pa, null, t)).data); if (v === 'POSTED' || v === 'FAILED') return v; await sleep(1500); } return 'TIMEOUT'; };
let r = await c('GET', '/accounting/chart-of-accounts?size=5', null, t); // seeds the chart
// master data
const tax = (await c('POST', '/inventory/taxes', { name: 'VAT 10', rate: 10 }, t)).data;
const unit = (await c('POST', '/inventory/units', { name: 'Piece' }, t)).data;
const cat = (await c('POST', '/inventory/categories', { name: 'Goods', color: '#3B82F6' }, t)).data;
const wh = (await c('POST', '/inventory/warehouses', { name: 'Main', address: '1 Test Street', city: 'Test City', zipCode: '70000', active: true }, t)).data;
const offWh = (await c('POST', '/inventory/warehouses', { name: 'Closed', address: '9 Test Street', city: 'Test City', zipCode: '70009', active: false }, t)).data;
const item = (await c('POST', '/inventory/items', { type: 'PRODUCT', name: 'Test Widget', sku: 'W-' + x, taxId: Number(tax.id), categoryId: Number(cat.id), unitId: Number(unit.id), salePrice: 100, purchasePrice: 60 }, t)).data;
const svc = (await c('POST', '/inventory/items', { type: 'SERVICE', name: 'Setup', generateSku: true, categoryId: Number(cat.id), unitId: Number(unit.id), salePrice: 50, purchasePrice: 0 }, t)).data;
const ven = (await c('POST', '/accounting/vendors', { companyName: 'Test Supplier ' + x, contactPerson: 'Test Contact', email: 'supplier@example.com' }, t)).data;
const cust = (await c('POST', '/accounting/customers', { companyName: 'Test Buyer ' + x, contactPerson: 'Test Buyer', email: 'buyer@example.com' }, t)).data;
ck('master data', item?.id && svc?.id && ven?.id && cust?.id && wh?.id, show([item?.id, svc?.id, ven?.id, cust?.id]));
// 1. purchase invoice: 10 × 60 + VAT 10 % = 660
let t0 = await tb();
r = await c('POST', '/purchase/invoices', { vendorId: Number(ven.id), warehouseId: Number(wh.id), invoiceDate: today, dueDate: due, lines: [{ itemId: Number(item.id), quantity: 10 }] }, t);
const pi = r.data?.invoice; r = await c('POST', '/purchase/invoices/' + pi?.id + '/post', null, t);
await settle('/purchase/invoices/' + pi?.id, d => d?.invoice?.journalStatus);
let t1 = await tb();
ck('purchase post → Dr 156 600, Dr 1331 60, Cr 331 660', r.s === 200 && d(t0, t1, '156') === 600 && d(t0, t1, '1331') === 60 && d(t0, t1, '331') === -660, r.s + ' ' + r.code + ' ' + r.msg + ' ' + show({ i156: d(t0, t1, '156'), i1331: d(t0, t1, '1331'), i331: d(t0, t1, '331') }));
r = await c('GET', '/accounting/journal-entries?sourceType=PURCHASE_INVOICE', null, t); ck('one PURCHASE_INVOICE journal entry', r.data?.content?.length === 1, show(r.data?.content?.map(z => z.number)));
// 2. sales invoice: 2 × 100 + VAT 20 + service 50 = 270; cost 120
const form = (lines, extra = {}) => ({ customerId: Number(cust.id), warehouseId: Number(wh.id), docDate: today, dueDate: due, paymentTerms: 'Net 30', lines, ...extra });
r = await c('POST', '/sales/invoices', form([{ itemId: Number(item.id) }], { warehouseId: Number(offWh.id) }), t); ck('draft with inactive warehouse → SAL_024', r.code === 'SAL_024', r.s + ' ' + r.code + ' ' + r.msg);
r = await c('POST', '/sales/invoices', form([{ itemId: Number(item.id), quantity: 2 }, { itemId: Number(svc.id) }]), t); const si = r.data?.document;
ck('sales invoice total 270', num(si?.total) === 270, r.s + ' ' + r.code + ' ' + r.msg + ' ' + si?.total);
t0 = await tb(); r = await c('POST', '/sales/invoices/' + si.id + '/post', null, t); await settle('/sales/invoices/' + si.id, d => d?.document?.journalStatus); t1 = await tb();
ck('sales post → Dr 131 270, Cr 5111 200, Cr 5113 50, Cr 33311 20', r.s === 200 && d(t0, t1, '131') === 270 && d(t0, t1, '5111') === -200 && d(t0, t1, '5113') === -50 && d(t0, t1, '33311') === -20, r.s + ' ' + r.code + ' ' + r.msg + ' ' + show({ a131: d(t0, t1, '131'), a5111: d(t0, t1, '5111'), a5113: d(t0, t1, '5113'), a33311: d(t0, t1, '33311') }));
ck('sales post → COGS Dr 632 120 / Cr 156 120', d(t0, t1, '632') === 120 && d(t0, t1, '156') === -120, show({ a632: d(t0, t1, '632'), a156: d(t0, t1, '156') }));
r = await c('POST', '/sales/invoices/' + si.id + '/post', null, t); const t1b = await tb(); ck('post again refused, nothing booked twice', r.s === 400 && d(t1, t1b, '131') === 0, r.s + ' ' + r.code);
// 3. sales return 1 item: Dr 5213 100 + 33311 10 / Cr 131 110; Dr 156 60 / Cr 632 60
r = await c('GET', '/sales/returns/returnable?invoiceId=' + si.id, null, t); const rl = r.data?.find(z => z.itemId === item.id);
r = await c('POST', '/sales/returns', { invoiceId: Number(si.id), warehouseId: Number(wh.id), returnDate: today, reason: 'Damaged', lines: [{ invoiceLineId: Number(rl?.invoiceLineId), quantity: 1 }] }, t); const ret = r.data?.ret;
await c('POST', '/sales/returns/' + ret?.id + '/approve', null, t);
t0 = await tb(); r = await c('POST', '/sales/returns/' + ret?.id + '/complete', null, t); await settle('/sales/returns/' + ret?.id, d => (d?.ret ?? d)?.journalStatus); t1 = await tb();
ck('return → Dr 5213 100, Dr 33311 10, Cr 131 110', r.s === 200 && d(t0, t1, '5213') === 100 && d(t0, t1, '33311') === 10 && d(t0, t1, '131') === -110, r.s + ' ' + r.code + ' ' + r.msg + ' ' + show({ a5213: d(t0, t1, '5213'), a33311: d(t0, t1, '33311'), a131: d(t0, t1, '131') }));
ck('return restock → Dr 156 60 / Cr 632 60', d(t0, t1, '156') === 60 && d(t0, t1, '632') === -60, show({ a156: d(t0, t1, '156'), a632: d(t0, t1, '632') }));
r = await c('POST', '/accounting/credit-notes', { returnId: Number(ret?.id), notes: 'Test note' }, t); const cn = r.data;
t0 = await tb(); r = await c('POST', '/accounting/credit-notes/' + cn?.id + '/approve', null, t); t1 = await tb();
ck('credit note for the return approves without a second entry', r.s === 200 && d(t0, t1, '131') === 0 && d(t0, t1, '5213') === 0 && d(t0, t1, '5111') === 0, r.s + ' ' + r.code + ' ' + r.msg + ' ' + show({ a131: d(t0, t1, '131'), a5111: d(t0, t1, '5111') }));
// 4. purchase return 2 items: Dr 331 132 / Cr 156 120, Cr 1331 12
r = await c('GET', '/purchase/returns/returnable?invoiceId=' + pi.id, null, t); const prl = (r.data || []).find(z => z.itemId === item.id);
r = await c('POST', '/purchase/returns', { invoiceId: Number(pi.id), returnDate: today, reason: 'Wrong item', lines: [{ invoiceLineId: Number(prl?.invoiceLineId), quantity: 2 }] }, t); const pr = r.data?.ret ?? r.data;
await c('POST', '/purchase/returns/' + pr?.id + '/approve', null, t);
t0 = await tb(); r = await c('POST', '/purchase/returns/' + pr?.id + '/complete', null, t); await settle('/purchase/returns/' + pr?.id, d => (d?.ret ?? d)?.journalStatus); t1 = await tb();
ck('purchase return → Dr 331 132, Cr 156 120, Cr 1331 12', r.s === 200 && d(t0, t1, '331') === 132 && d(t0, t1, '156') === -120 && d(t0, t1, '1331') === -12, r.s + ' ' + r.code + ' ' + r.msg + ' ' + show({ a331: d(t0, t1, '331'), a156: d(t0, t1, '156'), a1331: d(t0, t1, '1331') }));
// 5. POS: 1 × 110 (100 + VAT 10), cost 60
const bank = (await c('POST', '/accounting/bank-accounts', { holderName: 'Test Shop', bankName: 'Test Bank', accountNumber: 'CC' + x, openingBalance: 100000000, active: true }, t)).data;
t0 = await tb(); r = await c('POST', '/pos/orders', { warehouseId: Number(wh.id), bankAccountId: Number(bank.id), lines: [{ itemId: Number(item.id), quantity: 1 }] }, t); t1 = await tb();
ck('POS → Cr 5111 100, Cr 33311 10, Dr 632 60, Cr 156 60', r.s === 200 && d(t0, t1, '5111') === -100 && d(t0, t1, '33311') === -10 && d(t0, t1, '632') === 60 && d(t0, t1, '156') === -60, r.s + ' ' + r.code + ' ' + r.msg + ' ' + show({ a5111: d(t0, t1, '5111'), a33311: d(t0, t1, '33311'), a632: d(t0, t1, '632'), a156: d(t0, t1, '156') }));
let tbAll = await tb(); ck('trial balance balanced after all postings', tbAll._balanced === true, String(tbAll._balanced));
// 6. closed year → ACC_070, invoice stays DRAFT, stock unchanged
r = await c('POST', '/accounting/year-end-closes', { financialYear: LY, closingDate: LY + '-12-31' }, t); ck('close last year', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
const st0 = await stock(item.id, wh.id);
r = await c('POST', '/sales/invoices', form([{ itemId: Number(item.id) }], { docDate: LY + '-06-01', dueDate: LY + '-07-01' }), t); const old = r.data?.document;
r = await c('POST', '/sales/invoices/' + old?.id + '/post', null, t); ck('post into closed year → ACC_070', r.code === 'ACC_070', r.s + ' ' + r.code + ' ' + r.msg);
r = await c('GET', '/sales/invoices/' + old?.id, null, t); ck('invoice stays DRAFT, stock unchanged', r.data?.document?.status === 'DRAFT' && (await stock(item.id, wh.id)) === st0, r.data?.document?.status + ' stock ' + st0 + '→' + (await stock(item.id, wh.id)));
// 7. payroll: accrual Dr 6421 / Cr 334, pay Dr 334 / Cr bank → 334 = 0
const br = (await c('POST', '/org/branches', { name: 'CC Branch ' + x, city: 'Test City', status: 'ACTIVE' }, t)).data;
const dep = (await c('POST', '/org/departments', { name: 'CC Dept', branchId: num(br.id), status: 'ACTIVE' }, t)).data;
const des = (await c('POST', '/org/designations', { name: 'CC Clerk', departmentId: num(dep.id), status: 'ACTIVE' }, t)).data;
const mkEmp = async tag => (await c('POST', '/employees', { email: 'cc.' + tag + '.' + x.toLowerCase() + '@example.com', password: pw(), profile: { fullName: 'Test ' + tag + ' ' + x, gender: 'MALE', branchId: num(br.id), departmentId: num(dep.id), designationId: num(des.id), dateOfJoining: ym + '-01', employmentType: 'FULL_TIME', status: 'ACTIVE' } }, t)).data;
const emp = await mkEmp('worker'), emp2 = await mkEmp('boss');
await c('POST', '/payroll/salaries', { employeeId: num(emp.id), basicSalary: 10000000, components: [], status: 'ACTIVE' }, t);
r = await c('POST', '/payroll/runs', { title: 'CC ' + ym, frequency: 'MONTHLY', periodStart: ym + '-01', periodEnd: monthEnd, payDate: monthEnd, bankAccountId: num(bank.id) }, t); const run = r.data;
r = await c('POST', '/payroll/runs/' + run?.id + '/process', null, t); const net = num(r.data?.netPay); ck('payroll processed', r.data?.status === 'COMPLETED' && net > 0, r.s + ' ' + r.code + ' ' + r.msg + ' net=' + net);
t0 = await tb(); r = await c('POST', '/payroll/runs/' + run?.id + '/pay', { bankAccountId: num(bank.id), paymentDate: today }, t); t1 = await tb();
ck('pay → Dr 6421 = net, 334 nets to 0', r.data?.paymentStatus === 'PAID' && d(t0, t1, '6421') === net && d(t0, t1, '334') === 0, r.s + ' ' + r.code + ' ' + r.msg + ' ' + show({ a6421: d(t0, t1, '6421'), a334: d(t0, t1, '334'), net }));
// 8. status locks
const lc = async (k, name) => (await c('POST', '/lifecycle/catalogs/' + k, { name, status: 'ACTIVE' }, t)).data;
const wt = await lc('warning-types', 'CC Late'), sev = await lc('warning-severities', 'CC Minor');
const wForm = { employeeId: num(emp.id), warningTypeId: num(wt?.id), severityId: num(sev?.id), subject: 'Late arrival', warningDate: today, description: 'Test' };
const w = (await c('POST', '/lifecycle/warnings', wForm, t)).data;
await c('PUT', '/lifecycle/warnings/' + w?.id + '/status', { status: 'APPROVED' }, t);
r = await c('PUT', '/lifecycle/warnings/' + w?.id, wForm, t); ck('edit approved warning → EMP_102', r.code === 'EMP_102', r.s + ' ' + r.code + ' ' + r.msg);
r = await c('DELETE', '/lifecycle/warnings/' + w?.id, null, t); ck('delete approved warning → EMP_102', r.code === 'EMP_102', r.s + ' ' + r.code);
const tForm = { employeeId: num(emp.id), purpose: 'Client visit', destination: 'Test City', startDate: due, endDate: due };
const trip = (await c('POST', '/lifecycle/trips', tForm, t)).data;
r = await c('PUT', '/lifecycle/trips/' + trip?.id + '/status', { status: 'COMPLETED' }, t); ck('trip PLANNED → COMPLETED → EMP_096', r.code === 'EMP_096', r.s + ' ' + r.code + ' ' + r.msg);
await c('PUT', '/lifecycle/trips/' + trip?.id + '/status', { status: 'ONGOING' }, t);
r = await c('PUT', '/lifecycle/trips/' + trip?.id, tForm, t); ck('edit ongoing trip → EMP_102', r.code === 'EMP_102', r.s + ' ' + r.code);
const otForm = { title: 'Close books', days: 1, hours: 2, rate: 100000, startDate: ym + '-02', endDate: ym + '-02' };
const ot = (await c('POST', '/payroll/employees/' + emp2.id + '/overtimes', otForm, t)).data;
await c('PUT', '/payroll/overtimes/' + ot?.id + '/status', { status: 'APPROVED' }, t);
r = await c('PUT', '/payroll/overtimes/' + ot?.id, otForm, t); ck('edit approved overtime → PAY_043', r.code === 'PAY_043', r.s + ' ' + r.code + ' ' + r.msg);
r = await c('PUT', '/payroll/overtimes/' + ot?.id + '/status', { status: 'REJECTED' }, t); ck('re-decide approved overtime → PAY_043', r.code === 'PAY_043', r.s + ' ' + r.code);
const ctype = (await c('POST', '/document/contract-types', { name: 'CC Fixed term ' + x, status: 'ACTIVE' }, t)).data;
const cForm = { employeeId: num(emp.id), contractTypeId: num(ctype?.id), startDate: due, basicSalary: 15000000 };
r = await c('POST', '/document/contracts', cForm, t); const con = r.data; ck('contract created', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(r.data));
await c('PUT', '/document/contracts/' + con?.id + '/status', { status: 'ACTIVE' }, t);
r = await c('PUT', '/document/contracts/' + con?.id, cForm, t); ck('edit active contract → DOC_027', r.code === 'DOC_027', r.s + ' ' + r.code + ' ' + r.msg);
r = await c('PUT', '/document/contracts/' + con?.id + '/status', { status: 'DRAFT' }, t); ck('active → draft → DOC_028', r.code === 'DOC_028', r.s + ' ' + r.code);
r = await c('GET', '/settings/company/email', null, t); ck('email settings expose smtpConfigured', r.s === 200 && typeof r.data?.smtpConfigured === 'boolean', r.s + ' ' + show(r.data));
// isolation
r = await c('POST', '/accounting/journal-postings', { sourceType: 'SALES_INVOICE', sourceId: Number(si.id), date: today, reference: 'X', description: 'x', goodsAmount: 1, serviceAmount: 0, taxAmount: 0, costAmount: 0 }, B.t);
r = await c('GET', '/accounting/journal-entries?sourceType=SALES_INVOICE', null, B.t); ck("company B sees none of A's entries", (r.data?.content || []).every(z => z.reference !== si.number), show(r.data?.content?.map(z => z.reference)));
r = await c('POST', '/accounting/journal-postings', { sourceType: 'SALES_INVOICE', sourceId: 1, date: today, goodsAmount: 1 }); ck('journal-postings without token → 401', r.s === 401, r.s);
console.log(`\n${n - f}/${n} passed`);

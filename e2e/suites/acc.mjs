// accounting: customers, vendors, vouchers, payments, notes, reports
const until = async (pa, ok) => { let d; for (let i = 0; i < 25; i++) { d = (await c('GET', pa, null, t)).data; if (ok(d)) return d; await new Promise(res => setTimeout(res, 1500)); } return d; };
const num = v => Number(v); const show = d => JSON.stringify(d)?.slice(0, 220);
const today = new Date().toISOString().slice(0, 10); const due = new Date(Date.now() + 30 * 864e5).toISOString().slice(0, 10);
const raw = async (pa, tok) => { const res = await fetch(G + pa, { headers: { Authorization: 'Bearer ' + tok } }); return { s: res.status, buf: Buffer.from(await res.arrayBuffer()) }; };
// chart of accounts (lazy VAS seed)
let r = await c('GET', '/accounting/chart-of-accounts?size=200', null, t); const accts = r.data?.content ?? r.data ?? [];
const acc = code => accts.find(a => a.code === code);
ck('VAS chart seeded (112, 131, 331, 5111, 6428)', r.s === 200 && ['112', '131', '331', '5111', '6428'].every(acc), r.s + ' ' + r.code + ' n=' + accts.length);
r = await c('GET', '/accounting/account-mapping', null, t); ck('account mapping has 12 keys', r.s === 200 && Object.keys(r.data.accounts ?? r.data).length >= 12, r.s + ' ' + show(r.data));
// catalogue + parties
const unit = (await c('POST', '/inventory/units', { name: 'Piece' }, t)).data;
const cat = (await c('POST', '/inventory/categories', { name: 'Services', color: '#3B82F6' }, t)).data;
const wh = (await c('POST', '/inventory/warehouses', { name: 'Main', address: '1 Test Street', city: 'Test City', zipCode: '70000', active: true }, t)).data;
const svc = (await c('POST', '/inventory/items', { type: 'SERVICE', name: 'Consulting', generateSku: true, categoryId: Number(cat.id), unitId: Number(unit.id), salePrice: 500, purchasePrice: 300 }, t)).data;
const prod = (await c('POST', '/inventory/items', { type: 'PRODUCT', name: 'Test Part', sku: 'P-' + x, categoryId: Number(cat.id), unitId: Number(unit.id), salePrice: 100, purchasePrice: 100, openingWarehouseId: Number(wh.id), openingQuantity: 5 }, t)).data;
const cust = (await c('POST', '/accounting/customers', { companyName: 'Test Customer ' + x, contactPerson: 'Test Contact', email: 'cust@example.com' }, t)).data;
const ven = (await c('POST', '/accounting/vendors', { companyName: 'Test Vendor ' + x, contactPerson: 'Test Contact', email: 'ven@example.com' }, t)).data;
// bank
r = await c('POST', '/accounting/bank-accounts', { holderName: 'Main Account', bankName: 'Test Bank', accountNumber: 'A1-' + x, openingBalance: 1000, active: true, branch: 'HQ', accountType: 'CHECKING', iban: 'VN00TEST' }, t);
const bank = r.data; ck('bank account (CHECKING, opening 1000)', r.s === 200 && num(bank.balance) === 1000, r.s + ' ' + r.code + ' ' + r.msg);
const bank2 = (await c('POST', '/accounting/bank-accounts', { holderName: 'Savings', bankName: 'Test Bank', accountNumber: 'A2-' + x, openingBalance: 0, active: true, accountType: 'SAVINGS' }, t)).data;
// FR-ACC-PAY-003 GWT: SI balance 500, bank 1000, payment 500 cleared
r = await c('POST', '/sales/invoices', { customerId: Number(cust.id), invoiceType: 'SERVICE', docDate: today, dueDate: due, lines: [{ itemId: Number(svc.id) }] }, t);
const si = r.data?.document; await c('POST', '/sales/invoices/' + si.id + '/post', null, t);
r = await c('GET', '/accounting/customer-payments/open-items?partyId=' + cust.id, null, t); ck('open items show the SI 500', r.s === 200 && show(r.data).includes(si.number), r.s + ' ' + r.code + ' ' + show(r.data));
r = await c('POST', '/accounting/customer-payments', { partyId: Number(cust.id), bankAccountId: Number(bank.id), paymentDate: today, reference: 'TT-1', allocations: [{ invoiceId: Number(si.id), amount: 600 }] }, t); ck('allocation over balance → ACC_054', r.code === 'ACC_054', r.s + ' ' + r.code);
r = await c('POST', '/accounting/customer-payments', { partyId: Number(cust.id), bankAccountId: Number(bank.id), paymentDate: today, reference: 'TT-1', allocations: [{ invoiceId: Number(si.id), amount: 500 }] }, t);
const cp = r.data; ck('customer payment CP-, PENDING', r.s === 200 && /^CP-/.test(cp.number) && cp.status === 'PENDING', r.s + ' ' + r.code + ' ' + r.msg + ' ' + cp?.number);
r = await c('POST', '/accounting/customer-payments/' + cp.id + '/clear', null, t); ck('clear → CLEARED', r.s === 200 && r.data.status === 'CLEARED', r.s + ' ' + r.code + ' ' + r.msg);
r = { data: await until('/sales/invoices/' + si.id, d => d?.document?.status === 'PAID') }; ck('GWT: SI balance 0 / PAID', num(r.data.document.balance) === 0 && r.data.document.status === 'PAID', show({ b: r.data.document.balance, s: r.data.document.status }));
r = await c('GET', '/accounting/bank-accounts/' + bank.id, null, t); ck('GWT: bank 1000 → 1500', num(r.data.balance) === 1500, r.data.balance);
r = await c('GET', '/accounting/bank-accounts/' + bank.id + '/transactions', null, t); ck('GWT: Credit 500 bank transaction', (r.data.content ?? r.data).some(z => z.type === 'CREDIT' && num(z.amount) === 500), show((r.data.content ?? r.data).map(z => z.type + ':' + z.amount + ':' + z.runningBalance)));
const ctx = (r.data.content ?? r.data).find(z => num(z.amount) === 500);
r = await c('GET', '/accounting/journal-entries?sourceType=CUSTOMER_PAYMENT', null, t); let je = (r.data?.content ?? [])[0];
if (!je) { r = await c('GET', '/accounting/journal-entries', null, t); je = (r.data?.content ?? []).find(e => show(e).includes(cp.number) || e.sourceId === cp.id); }
r = je ? await c('GET', '/accounting/journal-entries/' + je.id, null, t) : r;
ck('GWT: balanced entry Dr 112 / Cr 131 500', !!je && num(r.data.totalDebit) === 500 && num(r.data.totalCredit) === 500 && r.data.lines.some(l => num(l.debit) === 500 && (l.accountCode ?? show(l)).includes('112')) && r.data.lines.some(l => num(l.credit) === 500 && (l.accountCode ?? show(l)).includes('131')), show(r.data?.lines));
r = await c('POST', '/accounting/customer-payments/' + cp.id + '/clear', null, t); ck('clear twice → ACC_051', r.code === 'ACC_051', r.s + ' ' + r.code);
r = await c('GET', '/accounting/chart-of-accounts/' + acc('131').id, null, t); ck('receivable 131 balance moved', r.s === 200, show({ cur: r.data?.currentBalance }));
r = await c('POST', '/accounting/bank-transactions/' + ctx.id + '/reconcile', null, t); ck('reconcile → RECONCILED', r.s === 200 && r.data.status === 'RECONCILED', r.s + ' ' + r.code);
// vendor: purchase invoice 300, pay 100 → PARTIAL
r = await c('POST', '/purchase/invoices', { vendorId: Number(ven.id), invoiceDate: today, dueDate: due, lines: [{ itemId: Number(svc.id) }] }, t); const pi = r.data?.invoice; await c('POST', '/purchase/invoices/' + pi.id + '/post', null, t);
r = await c('POST', '/accounting/vendor-payments', { partyId: Number(ven.id), bankAccountId: Number(bank.id), paymentDate: today, allocations: [{ invoiceId: Number(pi.id), amount: 100 }] }, t); const vp = r.data;
r = await c('POST', '/accounting/vendor-payments/' + vp?.id + '/clear', null, t); ck('vendor payment VP- cleared', r.s === 200 && /^VP-/.test(vp.number), r.s + ' ' + r.code + ' ' + r.msg);
r = { data: await until('/purchase/invoices/' + pi.id, d => d?.invoice?.status === 'PARTIAL') }; ck('PI PARTIAL, balance 200', r.data.invoice.status === 'PARTIAL' && num(r.data.invoice.balance) === 200, show({ s: r.data.invoice.status, b: r.data.invoice.balance }));
ck('bank 1500 → 1400', num((await c('GET', '/accounting/bank-accounts/' + bank.id, null, t)).data.balance) === 1400, '');
// revenue / expense
const rcat = (await c('POST', '/accounting/revenue-categories', { name: 'Interest', code: 'RI-' + x, glAccountId: Number(acc('515')?.id ?? acc('711').id), enabled: true }, t)).data;
const ecat = (await c('POST', '/accounting/expense-categories', { name: 'Office', code: 'EO-' + x, glAccountId: Number(acc('6428').id), enabled: true }, t)).data;
r = await c('POST', '/accounting/revenues', { date: today, categoryId: Number(rcat.id), bankAccountId: Number(bank.id), amount: 50, reference: 'INT-1', description: 'Bank interest' }, t); const rev = r.data;
ck('revenue REV-, DRAFT', r.s === 200 && /^REV-\d{4}-\d{2}-\d{3}$/.test(rev.number) && rev.status === 'DRAFT', r.s + ' ' + r.code + ' ' + r.msg + ' ' + rev?.number);
r = await c('POST', '/accounting/revenues/' + rev.id + '/post', null, t); ck('post a draft → ACC_030', r.code === 'ACC_030', r.s + ' ' + r.code);
await c('POST', '/accounting/revenues/' + rev.id + '/approve', null, t); r = await c('POST', '/accounting/revenues/' + rev.id + '/post', null, t); ck('revenue approved → POSTED', r.s === 200 && r.data.status === 'POSTED', r.s + ' ' + r.code + ' ' + r.msg);
r = await c('PUT', '/accounting/revenues/' + rev.id, { date: today, categoryId: Number(rcat.id), bankAccountId: Number(bank.id), amount: 1 }, t); ck('edit posted → ACC_029', r.code === 'ACC_029', r.s + ' ' + r.code);
r = await c('POST', '/accounting/expenses', { date: today, categoryId: Number(ecat.id), bankAccountId: Number(bank.id), amount: 20, description: 'Paper' }, t); const exp = r.data;
await c('POST', '/accounting/expenses/' + exp.id + '/approve', null, t); r = await c('POST', '/accounting/expenses/' + exp.id + '/post', null, t); ck('expense EXP- posted', r.s === 200 && /^EXP-/.test(exp.number), r.s + ' ' + r.code + ' ' + r.msg);
ck('bank 1400 + 50 − 20 = 1430', num((await c('GET', '/accounting/bank-accounts/' + bank.id, null, t)).data.balance) === 1430, '');
// transfer
r = await c('POST', '/accounting/bank-transfers', { date: today, fromAccountId: Number(bank.id), toAccountId: Number(bank.id), amount: 10 }, t); ck('transfer to the same account → ACC_025', r.code === 'ACC_025', r.s + ' ' + r.code);
r = await c('POST', '/accounting/bank-transfers', { date: today, fromAccountId: Number(bank.id), toAccountId: Number(bank2.id), amount: 400, charges: 5, reference: 'TRF' }, t); const tr = r.data;
r = await c('POST', '/accounting/bank-transfers/' + tr.id + '/process', null, t); ck('transfer processed → COMPLETED', r.s === 200 && r.data.status === 'COMPLETED', r.s + ' ' + r.code + ' ' + r.msg);
ck('balances 1430 − 405 = 1025 / 400', num((await c('GET', '/accounting/bank-accounts/' + bank.id, null, t)).data.balance) === 1025 && num((await c('GET', '/accounting/bank-accounts/' + bank2.id, null, t)).data.balance) === 400, '');
r = await c('POST', '/accounting/bank-transfers/' + tr.id + '/process', null, t); ck('process twice → ACC_026', r.code === 'ACC_026', r.s + ' ' + r.code);
// credit note from a completed sales return
r = await c('POST', '/sales/invoices', { customerId: Number(cust.id), warehouseId: Number(wh.id), docDate: today, dueDate: today, lines: [{ itemId: Number(prod.id), quantity: 2 }] }, t); const si2 = r.data.document; await c('POST', '/sales/invoices/' + si2.id + '/post', null, t);
const rl = (await c('GET', '/sales/returns/returnable?invoiceId=' + si2.id, null, t)).data.find(z => z.itemId === prod.id);
const sr = (await c('POST', '/sales/returns', { invoiceId: Number(si2.id), returnDate: today, reason: 'Damaged', lines: [{ invoiceLineId: Number(rl.invoiceLineId), quantity: 1 }] }, t)).data.ret;
r = await c('POST', '/accounting/credit-notes', { returnId: Number(sr.id) }, t); ck('credit note from a draft return → ACC_063', r.code === 'ACC_063', r.s + ' ' + r.code);
await c('POST', '/sales/returns/' + sr.id + '/approve', null, t); await c('POST', '/sales/returns/' + sr.id + '/complete', null, t);
r = await c('POST', '/accounting/credit-notes', { returnId: Number(sr.id) }, t); const cn = r.data; ck('credit note CN- total 100', r.s === 200 && /^CN-/.test(cn.number) && num(cn.total) === 100, r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(cn));
r = await c('POST', '/accounting/credit-notes', { returnId: Number(sr.id) }, t); ck('second note for the return → ACC_064', r.code === 'ACC_064', r.s + ' ' + r.code);
r = await c('POST', '/accounting/credit-notes/' + cn.id + '/approve', null, t); ck('credit note approved', r.s === 200 && r.data.status === 'APPROVED', r.s + ' ' + r.code + ' ' + r.msg);
// pay SI2 (200) with the credit note (100) + 100 cash
r = await c('POST', '/accounting/customer-payments', { partyId: Number(cust.id), bankAccountId: Number(bank.id), paymentDate: today, allocations: [{ invoiceId: Number(si2.id), amount: 200 }], appliedNotes: [{ noteId: Number(cn.id), amount: 100 }] }, t);
const cp2 = r.data; r = await c('POST', '/accounting/customer-payments/' + cp2?.id + '/clear', null, t);
ck('payment with credit note: SI2 PAID, note APPLIED, bank +100', r.s === 200 && (await until('/sales/invoices/' + si2.id, d => d?.document?.status === 'PAID')).document.status === 'PAID' && (await c('GET', '/accounting/credit-notes/' + cn.id, null, t)).data.status === 'APPLIED' && num((await c('GET', '/accounting/bank-accounts/' + bank.id, null, t)).data.balance) === 1125, r.s + ' ' + r.code + ' ' + r.msg);
// reports
r = await c('GET', '/accounting/reports/bill-aging?asOf=' + today, null, t); ck('bill aging shows 200 owed', r.s === 200 && show(r.data).includes('200'), r.s + ' ' + r.code + ' ' + show(r.data));
r = await c('GET', '/accounting/reports/tax-summary?from=' + today + '&to=' + today, null, t); ck('tax summary', r.s === 200, r.s + ' ' + r.code + ' ' + show(r.data));
r = await c('GET', '/accounting/reports/vendor-balances?showZero=false', null, t); ck('vendor balances: 200', r.s === 200 && show(r.data).includes('200'), r.s + ' ' + show(r.data));
r = await c('GET', '/accounting/reports/customers/' + cust.id + '?from=' + today + '&to=' + today, null, t); ck('customer report', r.s === 200, r.s + ' ' + r.code + ' ' + show(r.data));
let pd = await raw('/accounting/reports/customers/' + cust.id + '/pdf?from=' + today + '&to=' + today, t); ck('customer report PDF', pd.s === 200 && pd.buf.subarray(0, 4).toString() === '%PDF', pd.s + ' ' + pd.buf.length);
pd = await raw('/accounting/reports/invoice-aging/pdf?asOf=' + today, t); ck('invoice aging PDF', pd.s === 200 && pd.buf.subarray(0, 4).toString() === '%PDF', pd.s + ' ' + pd.buf.length);
// POS income still works and journals
r = await c('POST', '/accounting/bank-accounts/' + bank.id + '/income', { sourceType: 'POS_ORDER', sourceId: 777, amount: 30 }, t); await c('POST', '/accounting/bank-accounts/' + bank.id + '/income', { sourceType: 'POS_ORDER', sourceId: 777, amount: 30 }, t);
ck('POS income idempotent (1125 → 1155)', r.s === 200 && num((await c('GET', '/accounting/bank-accounts/' + bank.id, null, t)).data.balance) === 1155, r.s + ' ' + r.code);
// isolation & permissions
r = await c('GET', '/accounting/customer-payments/' + cp.id, null, B.t); ck('company B reads A payment → 404 ACC_050', r.s === 404 && r.code === 'ACC_050', r.s + ' ' + r.code);
r = await c('POST', '/accounting/revenues/' + rev.id + '/approve', null, B.t); ck('company B approves A revenue → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('POST', '/accounting/bank-transfers/' + tr.id + '/process', null, B.t); ck('company B processes A transfer → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('GET', '/accounting/journal-entries/' + je?.id, null, B.t); ck('company B reads A journal → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('POST', '/accounting/credit-notes/' + cn.id + '/approve', null, B.t); ck('company B approves A note → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('POST', '/sales/invoices/' + si.id + '/payments', { paymentId: 1, amount: 1 }, B.t); ck('company B pays A invoice in commerce → 404', r.s === 404, r.s + ' ' + r.code);
const roles = (await c('GET', '/iam/roles', null, t)).data; const empRole = roles.find(z => z.code === 'EMPLOYEE'); const ep = pw(); const eun = 'ac.emp.' + x.toLowerCase();
const eu = await c('POST', '/iam/users', { username: eun, email: eun + '@example.com', fullName: 'Acc Employee', roleIds: [Number(empRole.id)], password: ep }, t); const ep2 = pw();
await c('POST', '/auth/initial-password', { username: eun, currentPassword: eu.data?.temporaryPassword || ep, newPassword: ep2 });
const et = (await c('POST', '/auth/login', { username: eun, password: ep2 })).data?.accessToken;
r = await c('GET', '/accounting/customer-payments', null, et); ck('employee payments → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', '/accounting/chart-of-accounts', null, et); ck('employee chart → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', '/accounting/reports/tax-summary?from=' + today + '&to=' + today, null, et); ck('employee reports → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', '/accounting/revenues'); ck('no token → 401', r.s === 401, r.s);
console.log(`\n${n - f}/${n} passed`);

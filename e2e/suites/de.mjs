// double entry: journals, reports, year-end close
const num = v => Number(v); const show = d => JSON.stringify(d)?.slice(0, 220);
const raw = async (pa, tok) => { const res = await fetch(G + pa, { headers: { Authorization: 'Bearer ' + tok } }); return { s: res.status, buf: Buffer.from(await res.arrayBuffer()) }; };
const today = new Date().toISOString().slice(0, 10); const Y = new Date().getUTCFullYear(); const LY = Y - 1;
let r = await c('GET', '/accounting/chart-of-accounts?size=300', null, t); const accts = r.data?.content ?? r.data ?? [];
const acc = code => Number(accts.find(a => a.code === code)?.id);
ck('chart has 112, 5111, 6428, 911, 4212', [112, 5111, 6428, 911, 4212].every(k => acc(String(k))), accts.length + ' accounts');
const je = (date, lines, extra = {}) => c('POST', '/accounting/journal-entries', { date, referenceType: 'GENERAL', reference: 'T', description: 'Test entry', lines, post: true, ...extra }, t);
const L = (code, debit, credit) => ({ accountId: acc(code), debit, credit, description: 'line' });
// manual journal
r = await je(today, [L('112', 100, null), L('5111', null, 90)]); ck('unbalanced → ACC_066 with difference', r.code === 'ACC_066' && /10/.test(r.msg), r.s + ' ' + r.code + ' ' + r.msg);
r = await je(today, [L('112', 100, null), L('5111', null, 100)], { description: 'abc' }); ck('description < 5 chars → refused', r.s === 400, r.s + ' ' + r.code);
r = await je(today, [L('112', 200, null), L('5111', null, 200)], { post: false }); const draft = r.data;
ck('draft JE- created', r.s === 200 && draft.status === 'DRAFT' && /^JE-\d{4}-\d{3}$/.test(draft.number), r.s + ' ' + r.code + ' ' + r.msg + ' ' + draft?.number);
const bal = async code => num((await c('GET', '/accounting/chart-of-accounts/' + acc(code), null, t)).data.currentBalance);
const b0 = await bal('112'); ck('draft moves no balance', b0 === 0, String(b0));
r = await c('POST', '/accounting/journal-entries/' + draft.id + '/post', null, t); ck('post draft → POSTED, 112 = 200', r.s === 200 && r.data.status === 'POSTED' && (await bal('112')) === 200, r.s + ' ' + r.code);
r = await c('PUT', '/accounting/journal-entries/' + draft.id, { date: today, referenceType: 'GENERAL', description: 'Changed entry', lines: [L('112', 1, null), L('5111', null, 1)] }, t); ck('edit posted → ACC_067', r.code === 'ACC_067', r.s + ' ' + r.code);
r = await c('POST', '/accounting/journal-entries/' + draft.id + '/reverse', { date: today, description: 'Undo test entry' }, t); const rev = r.data;
ck('reverse → new entry, 112 back to 0', r.s === 200 && (await bal('112')) === 0, r.s + ' ' + r.code + ' ' + r.msg);
r = await c('GET', '/accounting/journal-entries/' + draft.id, null, t); ck('original REVERSED and linked', r.data.status === 'REVERSED' && r.data.reversedById === rev.id, show({ s: r.data.status, by: r.data.reversedById }));
r = await c('POST', '/accounting/journal-entries/' + draft.id + '/reverse', {}, t); ck('reverse twice → ACC_069', r.code === 'ACC_069', r.s + ' ' + r.code);
// automatic entry (from a revenue) cannot be edited
const bank = (await c('POST', '/accounting/bank-accounts', { holderName: 'Main', bankName: 'Test Bank', accountNumber: 'D-' + x, openingBalance: 0, active: true }, t)).data;
const rcat = (await c('POST', '/accounting/revenue-categories', { name: 'Other', code: 'RO-' + x, glAccountId: acc('711'), enabled: true }, t)).data;
const rv = (await c('POST', '/accounting/revenues', { date: today, categoryId: Number(rcat.id), bankAccountId: Number(bank.id), amount: 40, description: 'Misc income' }, t)).data;
await c('POST', '/accounting/revenues/' + rv.id + '/approve', null, t); await c('POST', '/accounting/revenues/' + rv.id + '/post', null, t);
const auto = (await c('GET', '/accounting/journal-entries?type=AUTOMATIC', null, t)).data?.content?.[0];
r = await c('DELETE', '/accounting/journal-entries/' + auto?.id, null, t); ck('delete automatic entry → ACC_068', r.code === 'ACC_068', r.s + ' ' + r.code);
// last year's activity, then reports
await je(LY + '-03-10', [L('112', 1000, null), L('5111', null, 1000)]);
await je(LY + '-04-10', [L('6428', 300, null), L('112', null, 300)]);
await je(LY + '-05-10', [L('112', 50, null), L('5111', null, 50)], { post: false });
r = await c('GET', '/accounting/ledger/trial-balance?from=' + LY + '-01-01&to=' + LY + '-12-31', null, t);
ck('trial balance balanced (1300 / 1300)', r.s === 200 && r.data.balanced && num(r.data.totals.periodDebit) === 1300 && num(r.data.totals.periodCredit) === 1300, r.s + ' ' + show(r.data?.totals));
r = await c('GET', '/accounting/ledger/profit-loss?from=' + LY + '-01-01&to=' + LY + '-12-31', null, t); ck('P&L net profit 700', r.s === 200 && num(r.data.netProfit ?? r.data.net) === 700, r.s + ' ' + show(r.data));
r = await c('GET', '/accounting/ledger/general-ledger?accountId=' + acc('112') + '&from=' + LY + '-01-01&to=' + LY + '-12-31', null, t); ck('GL 112 closing 700', r.s === 200 && num(r.data.closingBalance ?? r.data.closing) === 700, r.s + ' ' + show(r.data));
r = await c('GET', '/accounting/ledger/cash-flow?from=' + LY + '-01-01&to=' + LY + '-12-31', null, t); ck('cash flow net change +700', r.s === 200 && num(r.data.netChange) === 700, r.s + ' ' + show(r.data));
r = await c('GET', '/accounting/ledger/expense-report?from=' + LY + '-01-01&to=' + LY + '-12-31', null, t); ck('expense report 300', r.s === 200 && show(r.data).includes('300'), r.s + ' ' + show(r.data));
r = await c('GET', '/accounting/ledger/account-statement?accountId=' + acc('5111') + '&from=' + LY + '-01-01&to=' + LY + '-12-31', null, t); ck('account statement', r.s === 200, r.s + ' ' + r.code);
r = await c('GET', '/accounting/ledger/account-balances?asOf=' + today + '&hideZero=true', null, t); ck('account balances', r.s === 200, r.s + ' ' + r.code);
for (const p of ['trial-balance', 'profit-loss', 'cash-flow', 'expense-report']) { const pd = await raw('/accounting/ledger/' + p + '/pdf?from=' + LY + '-01-01&to=' + LY + '-12-31', t); ck(p + ' PDF', pd.s === 200 && pd.buf.subarray(0, 4).toString() === '%PDF', pd.s + ' ' + pd.buf.length); }
// balance sheet
r = await c('POST', '/accounting/balance-sheets', { date: LY + '-12-31', financialYear: LY, notes: 'Draft sheet' }, t); const bs1 = r.data;
ck('balance sheet balanced (assets 700 = equity 700)', r.s === 200 && (bs1.balanced === true || num(bs1.difference) === 0), r.s + ' ' + r.code + ' ' + r.msg + ' ' + show({ a: bs1?.totalAssets, l: bs1?.totalLiabilities, e: bs1?.totalEquity, d: bs1?.difference }));
await c('POST', '/accounting/balance-sheets/' + bs1.id + '/finalise', null, t);
r = await c('PUT', '/accounting/balance-sheets/' + bs1.id + '/notes', { notes: 'x' }, t); ck('finalised sheet locked → ACC_073', r.code === 'ACC_073', r.s + ' ' + r.code);
const bs2 = (await c('POST', '/accounting/balance-sheets', { date: today, financialYear: Y }, t)).data; await c('POST', '/accounting/balance-sheets/' + bs2.id + '/finalise', null, t);
r = await c('GET', '/accounting/balance-sheets/compare?firstId=' + bs1.id + '&secondId=' + bs2.id, null, t); ck('compare two finalised sheets', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
// year-end close of last year
r = await c('POST', '/accounting/year-end-closes', { financialYear: LY, closingDate: LY + '-12-31' }, t); ck('close with a draft in the year → ACC_076', r.code === 'ACC_076', r.s + ' ' + r.code + ' ' + r.msg);
const d2 = (await c('GET', '/accounting/journal-entries?status=DRAFT', null, t)).data.content.find(e => e.date.startsWith(String(LY)));
await c('DELETE', '/accounting/journal-entries/' + d2.id, null, t);
r = await c('POST', '/accounting/year-end-closes', { financialYear: LY, closingDate: LY + '-12-31' }, t); ck('year-end close posted', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(r.data));
ck('5111 and 6428 cleared of last year, 4212 = 700, 911 = 0', (await bal('4212')) === 700 && (await bal('911')) === 0, show({ r: await bal('4212'), s: await bal('911'), rev: await bal('5111') }));
r = await c('POST', '/accounting/year-end-closes', { financialYear: LY, closingDate: LY + '-12-31' }, t); ck('close twice → ACC_075', r.code === 'ACC_075', r.s + ' ' + r.code);
r = await je(LY + '-06-01', [L('112', 5, null), L('5111', null, 5)]); ck('entry dated in the closed year → ACC_070', r.code === 'ACC_070', r.s + ' ' + r.code + ' ' + r.msg);
r = await c('GET', '/accounting/year-end-closes', null, t); ck('closed years listed', r.s === 200 && show(r.data).includes(String(LY)), r.s + ' ' + show(r.data));
// isolation & permissions
r = await c('GET', '/accounting/journal-entries/' + draft.id, null, B.t); ck('company B reads A entry → 404', r.s === 404 && r.code === 'ACC_021', r.s + ' ' + r.code);
r = await c('POST', '/accounting/journal-entries/' + draft.id + '/reverse', {}, B.t); ck('company B reverses A entry → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('GET', '/accounting/balance-sheets/' + bs1.id, null, B.t); ck('company B reads A sheet → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('GET', '/accounting/ledger/general-ledger?accountId=' + acc('112') + '&from=' + today + '&to=' + today, null, B.t); ck('company B GL of A account → 404', r.s === 404, r.s + ' ' + r.code);
const roles = (await c('GET', '/iam/roles', null, t)).data; const empRole = roles.find(z => z.code === 'EMPLOYEE'); const ep = pw(); const eun = 'de.emp.' + x.toLowerCase();
const eu = await c('POST', '/iam/users', { username: eun, email: eun + '@example.com', fullName: 'Ledger Employee', roleIds: [Number(empRole.id)], password: ep }, t); const ep2 = pw();
await c('POST', '/auth/initial-password', { username: eun, currentPassword: eu.data?.temporaryPassword || ep, newPassword: ep2 });
const et = (await c('POST', '/auth/login', { username: eun, password: ep2 })).data?.accessToken;
r = await c('POST', '/accounting/journal-entries', { date: today, referenceType: 'GENERAL', description: 'Employee entry', lines: [L('112', 1, null), L('5111', null, 1)] }, et); ck('employee manual journal → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('POST', '/accounting/year-end-closes', { financialYear: Y, closingDate: today }, et); ck('employee year-end close → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', '/accounting/ledger/trial-balance?from=' + today + '&to=' + today); ck('no token → 401', r.s === 401, r.s);
console.log(`\n${n - f}/${n} passed`);

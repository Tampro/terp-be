// financial goals and budgets
const num = v => Number(v); const show = d => JSON.stringify(d)?.slice(0, 220);
const today = new Date().toISOString().slice(0, 10); const Y = new Date().getUTCFullYear();
const ago = d => new Date(Date.now() - d * 864e5).toISOString().slice(0, 10); const ahead = d => new Date(Date.now() + d * 864e5).toISOString().slice(0, 10);
let r = await c('GET', '/accounting/chart-of-accounts?size=300', null, t); const accts = r.data?.content ?? r.data ?? [];
const acc = code => Number(accts.find(a => a.code === code)?.id);
const je = (date, lines) => c('POST', '/accounting/journal-entries', { date, referenceType: 'GENERAL', description: 'Test entry', lines, post: true }, t);
const L = (code, debit, credit) => ({ accountId: acc(code), debit, credit });
// goals
r = await c('POST', '/accounting/goal-categories', { code: 'sav', name: 'Savings' }, t); const gc = r.data; ck('goal category, code upper-cased', r.s === 200 && gc.code === 'SAV', r.s + ' ' + r.code + ' ' + r.msg + ' ' + gc?.code);
r = await c('POST', '/accounting/goals', { name: 'Bad', type: 'SAVINGS', targetAmount: 100, startDate: today, targetDate: today }, t); ck('target date not after start → ACC_081', r.code === 'ACC_081', r.s + ' ' + r.code);
r = await c('POST', '/accounting/goals', { name: 'Emergency fund', categoryId: Number(gc.id), type: 'SAVINGS', priority: 'HIGH', targetAmount: 1000, startDate: ago(50), targetDate: ahead(50), accountId: acc('1281') || acc('128') || acc('112'), description: 'Fake goal' }, t);
const goal = r.data; ck('goal DRAFT', r.s === 200 && goal.status === 'DRAFT', r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(goal));
r = await c('POST', '/accounting/goals/' + goal.id + '/contributions', { amount: 10 }, t); ck('contribution on a draft goal → ACC_083', r.code === 'ACC_083', r.s + ' ' + r.code);
r = await c('POST', '/accounting/goals/' + goal.id + '/activate', null, t); ck('activate → ACTIVE', r.s === 200 && r.data.status === 'ACTIVE', r.s + ' ' + r.code);
r = await c('POST', '/accounting/goals/' + goal.id + '/activate', null, t); ck('activate twice → ACC_082', r.code === 'ACC_082', r.s + ' ' + r.code);
await c('POST', '/accounting/goals/' + goal.id + '/milestones', { name: 'Half', targetAmount: 500, targetDate: ahead(10) }, t);
await c('POST', '/accounting/goals/' + goal.id + '/milestones', { name: 'Missed', targetAmount: 900, targetDate: ago(1) }, t);
await c('POST', '/accounting/goals/' + goal.id + '/contributions', { date: ago(20), amount: 300, notes: 'First' }, t);
r = await c('POST', '/accounting/goals/' + goal.id + '/contributions', { amount: 200 }, t); ck('manual contributions', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
r = await c('GET', '/accounting/goals/' + goal.id + '/tracking', null, t); const tk = r.data;
ck('tracking: 500 = 50 %, ON_TRACK, previous 300', r.s === 200 && num(tk.currentAmount) === 500 && num(tk.progressPercent) === 50 && tk.trackingStatus === 'ON_TRACK' && num(tk.previousAmount) === 300, r.s + ' ' + show({ c: tk?.currentAmount, p: tk?.progressPercent, e: tk?.elapsedPercent, s: tk?.trackingStatus, prev: tk?.previousAmount, proj: tk?.projectedCompletionDate }));
ck('milestones: Half ACHIEVED, Missed PENDING + overdue', tk.milestones.some(m => m.name === 'Half' && m.status === 'ACHIEVED') && tk.milestones.some(m => m.name === 'Missed' && m.status === 'PENDING' && m.overdue), show(tk.milestones));
r = await c('POST', '/accounting/goals', { name: 'Ledger goal', type: 'SAVINGS', targetAmount: 1000, startDate: ago(10), targetDate: ahead(90), accountId: acc('1281') || acc('112') }, t); const g2 = r.data;
await c('POST', '/accounting/goals/' + g2.id + '/activate', null, t);
const gacc = accts.find(a => Number(a.id) === Number(g2.accountId))?.code ?? '112';
await je(today, [L(gacc, 250, null), L('5111', null, 250)]);
r = await c('GET', '/accounting/goals/' + g2.id + '/tracking', null, t); ck('automatic contribution from posted lines (250)', r.s === 200 && num(r.data.automaticAmount) === 250, r.s + ' ' + show({ a: r.data?.automaticAmount, acct: gacc }));
r = await c('POST', '/accounting/goals/' + goal.id + '/contributions', { amount: 500 }, t); r = await c('GET', '/accounting/goals/' + goal.id, null, t); ck('reaching the target completes the goal', r.data.status === 'COMPLETED', r.data.status);
// budgets
r = await c('POST', '/accounting/budget-periods', { name: 'FY ' + Y, financialYear: Y, startDate: Y + '-01-01', endDate: Y + '-12-31' }, t); const per = r.data; ck('budget period ACTIVE', r.s === 200 && per.status === 'ACTIVE', r.s + ' ' + r.code + ' ' + r.msg);
r = await c('POST', '/accounting/budget-periods/' + per.id + '/close', null, t); ck('close period before its end → ACC_088', r.code === 'ACC_088', r.s + ' ' + r.code);
r = await c('POST', '/accounting/budgets', { name: 'Office costs', periodId: Number(per.id), type: 'OPERATIONAL', amount: 1000 }, t); const bud = r.data; ck('budget DRAFT, amount 0 (form amount ignored)', r.s === 200 && bud.status === 'DRAFT' && num(bud.amount) === 0, r.s + ' ' + r.code + ' ' + r.msg + ' ' + bud?.amount);
r = await c('POST', '/accounting/budgets/' + bud.id + '/allocations', { accountId: acc('112'), allocated: 100 }, t); ck('non-expense account → ACC_093', r.code === 'ACC_093', r.s + ' ' + r.code);
r = await c('POST', '/accounting/budgets/' + bud.id + '/allocations', { accountId: acc('6428'), allocated: 600 }, t); ck('allocate 600 to 6428', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
const parent = accts.find(a => Number(a.id) === acc('6428'))?.parentId;
if (parent) { r = await c('POST', '/accounting/budgets/' + bud.id + '/allocations', { accountId: Number(parent), allocated: 100 }, t); ck('parent of an allocated account → ACC_097', r.code === 'ACC_097', r.s + ' ' + r.code + ' ' + r.msg); }
const other = accts.find(a => a.code.startsWith('641') && a.active !== false) ?? accts.find(a => a.code === '6427');
r = await c('POST', '/accounting/budgets/' + bud.id + '/allocations', { accountId: Number(other.id), allocated: 500 }, t); const otherLine = r.data; ck('no cap: allocate 500 more', r.s === 200, r.s + ' ' + r.code + ' ' + other.code);
r = await c('GET', '/accounting/budgets/' + bud.id, null, t); ck('budget amount = sum of allocations (1100)', num(r.data?.amount) === 1100, show(r.data?.amount));
await c('PUT', '/accounting/budgets/' + bud.id + '/allocations/' + otherLine?.id, { accountId: Number(other.id), allocated: 200 }, t);
r = await c('GET', '/accounting/budgets/' + bud.id, null, t); ck('edit recomputes the amount (800)', num(r.data?.amount) === 800, show(r.data?.amount));
r = await c('POST', '/accounting/budgets/' + bud.id + '/activate', null, t); ck('activate before approve → ACC_091', r.code === 'ACC_091', r.s + ' ' + r.code);
r = await c('POST', '/accounting/budgets/' + bud.id + '/approve', null, t); ck('approve → APPROVED with approvedBy', r.s === 200 && r.data.status === 'APPROVED' && r.data.approvedBy, r.s + ' ' + r.code + ' ' + show(r.data));
await c('POST', '/accounting/budgets/' + bud.id + '/activate', null, t);
// spend: 450 on 6428 (posted), 999 draft, 70 on the other account, 30 last year
await je(today, [L('6428', 450, null), L('112', null, 450)]);
await c('POST', '/accounting/journal-entries', { date: today, referenceType: 'GENERAL', description: 'Draft spend', lines: [L('6428', 999, null), L('112', null, 999)], post: false }, t);
await je(today, [L(other.code, 70, null), L('112', null, 70)]);
await je((Y - 1) + '-12-15', [L('6428', 30, null), L('112', null, 30)]);
r = await c('GET', '/accounting/budgets/' + bud.id + '/monitoring', null, t); const mon = r.data;
ck('monitoring: allocated 800, spent 520, remaining 280, variance 35 %', r.s === 200 && num(mon.allocated) === 800 && num(mon.spent) === 520 && num(mon.remaining) === 280 && num(mon.variancePercent) === 35, r.s + ' ' + r.code + ' ' + show({ a: mon?.allocated, s: mon?.spent, r: mon?.remaining, v: mon?.variancePercent }));
await je(today, [L('6428', 200, null), L('112', null, 200)]);
r = await c('GET', '/accounting/budgets/' + bud.id + '/monitoring', null, t); const line = r.data.lines.find(l => l.accountCode === '6428' || show(l).includes('6428'));
ck('6428 over budget (650 / 600, variance negative)', line && line.overBudget && num(line.variancePercent) < 0, show(line));
r = await c('GET', '/accounting/budget-periods/' + per.id + '/monitoring', null, t); ck('period monitoring', r.s === 200 && num(r.data.spent) === 720, r.s + ' ' + show({ s: r.data?.spent }));
// isolation & permissions
r = await c('GET', '/accounting/goals/' + goal.id, null, B.t); ck('company B reads A goal → 404 ACC_080', r.s === 404 && r.code === 'ACC_080', r.s + ' ' + r.code);
r = await c('POST', '/accounting/budgets/' + bud.id + '/close', null, B.t); ck('company B closes A budget → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('GET', '/accounting/budgets/' + bud.id + '/monitoring', null, B.t); ck('company B monitoring of A budget → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('POST', '/accounting/goals/' + g2.id + '/contributions', { amount: 1 }, B.t); ck('company B contributes to A goal → 404', r.s === 404, r.s + ' ' + r.code);
const roles = (await c('GET', '/iam/roles', null, t)).data; const empRole = roles.find(z => z.code === 'EMPLOYEE'); const ep = pw(); const eun = 'gb.emp.' + x.toLowerCase();
const eu = await c('POST', '/iam/users', { username: eun, email: eun + '@example.com', fullName: 'Budget Employee', roleIds: [Number(empRole.id)], password: ep }, t); const ep2 = pw();
await c('POST', '/auth/initial-password', { username: eun, currentPassword: eu.data?.temporaryPassword || ep, newPassword: ep2 });
const et = (await c('POST', '/auth/login', { username: eun, password: ep2 })).data?.accessToken;
r = await c('GET', '/accounting/goals', null, et); ck('employee goals → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('POST', '/accounting/budgets/' + bud.id + '/approve', null, et); ck('employee approve budget → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', '/accounting/budgets'); ck('no token → 401', r.s === 401, r.s);
console.log(`\n${n - f}/${n} passed`);

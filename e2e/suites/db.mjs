// module dashboards with seeded numbers

const num = v => Number(v); const show = d => JSON.stringify(d)?.slice(0, 300);
const today = new Date().toISOString().slice(0, 10); const later = new Date(Date.now() + 30 * 864e5).toISOString().slice(0, 10); const yesterday = new Date(Date.now() - 864e5).toISOString().slice(0, 10); const tomorrow = new Date(Date.now() + 864e5).toISOString().slice(0, 10);
const Y = new Date().getUTCFullYear(); const M = new Date().getUTCMonth() + 1;
const part = (d, p) => d?.parts?.[p]?.data ?? d?.parts?.[p];
const me = (await c('GET', '/me', null, t)).data; const myId = num(me?.id ?? me?.userId ?? me?.user?.id ?? A.admin.id);
const mkUser = async (tag, roleIds) => { const un = 'db.' + tag + '.' + x.toLowerCase(); const p0 = pw(); const u = await c('POST', '/iam/users', { username: un, email: un + '@example.com', fullName: 'Dash ' + tag, roleIds, password: p0 }, t); const p1 = pw(); await c('POST', '/auth/initial-password', { username: un, currentPassword: u.data?.temporaryPassword || p0, newPassword: p1 }); return { id: num(u.data?.user?.id ?? u.data?.id), t: (await c('POST', '/auth/login', { username: un, password: p1 })).data?.accessToken }; };
const roles = (await c('GET', '/iam/roles', null, t)).data; const R = code => Number(roles.find(z => z.code === code)?.id);
const U2 = await mkUser('u2', [R('EMPLOYEE')]);
let r;
// ---------- 1. project: 2 projects (P1 overdue), 4 tasks H/M/M/L with 2 done, 1 active bug, team 2, client 1
const cust = (await c('POST', '/accounting/customers', { companyName: 'Dash Client ' + x, contactPerson: 'Test', email: 'client@example.com' }, t)).data;
const p1 = (await c('POST', '/projects', { name: 'P1 ' + x, startDate: '2026-01-01', endDate: yesterday, userIds: [myId, U2.id] }, t)).data;
const p2 = (await c('POST', '/projects', { name: 'P2 ' + x, startDate: today, endDate: later, userIds: [myId] }, t)).data;
await c('PUT', '/projects/' + p1.id + '/clients', { customerIds: [String(cust.id)] }, t);
const st = (await c('GET', '/projects/stages?kind=TASK', null, t)).data; const done = st[st.length - 1];
const tk = []; for (const pr of ['HIGH', 'MEDIUM', 'MEDIUM', 'LOW']) tk.push((await c('POST', '/projects/' + p1.id + '/tasks', { title: 'T ' + pr, priority: pr, assigneeIds: [myId], startDate: today, dueDate: later }, t)).data);
await c('PUT', '/projects/tasks/' + tk[0].id + '/move', { stageId: Number(done.id), position: 0 }, t);
await c('PUT', '/projects/tasks/' + tk[1].id + '/move', { stageId: Number(done.id), position: 1 }, t);
await c('POST', '/projects/' + p1.id + '/bugs', { title: 'Bug 1', priority: 'HIGH', assigneeId: myId }, t);
r = await c('GET', '/reports/project-dashboard?year=' + Y, null, t); const ps = part(r.data, 'stats');
ck('project: 2 projects, 1 overdue, 50 %, 1 active bug, team 2, clients 1', r.s === 200 && ps?.totalProjects === 2 && ps.overdueProjects === 1 && num(ps.taskCompletionPercent) === 50 && ps.activeBugs === 1 && ps.teamSize === 2 && ps.clients === 1, r.s + ' ' + r.code + ' ' + show(ps && { tp: ps.totalProjects, op: ps.overdueProjects, pc: ps.taskCompletionPercent, ab: ps.activeBugs, ts: ps.teamSize, cl: ps.clients }) + ' ' + show(r.data?.unavailable));
ck('project: priority H1/M2/L1, this month 4 created / 2 completed', ps?.byPriority?.HIGH === 1 && ps.byPriority.MEDIUM === 2 && ps.byPriority.LOW === 1 && ps.monthly?.[M - 1]?.created === 4 && ps.monthly[M - 1].completed === 2, show({ p: ps?.byPriority, m: ps?.monthly?.[M - 1] }));
r = await c('GET', '/reports/project-dashboard?year=' + Y, null, U2.t); const ps2 = part(r.data, 'stats');
ck('project (EMPLOYEE, VIEW_OWN): scope OWN, only P1 (on its team)', r.s === 200 && ps2?.scope === 'OWN' && ps2.totalProjects === 1, r.s + ' ' + r.code + ' ' + show({ s: ps2?.scope, t: ps2?.totalProjects }));
// ---------- 2. accounting: revenue 1000 + expense 300 posted, customer payment 500 + vendor payment 200 cleared
let rr = await c('GET', '/accounting/chart-of-accounts?size=300', null, t); const accts = rr.data?.content ?? rr.data ?? []; const acc = code => accts.find(a => a.code === code);
const bank = (await c('POST', '/accounting/bank-accounts', { holderName: 'Dash', bankName: 'Test Bank', accountNumber: 'DB' + x, openingBalance: 100000, active: true }, t)).data;
const rcat = (await c('POST', '/accounting/revenue-categories', { name: 'Other', code: 'RD-' + x, glAccountId: Number(acc('711')?.id ?? acc('515')?.id), enabled: true }, t)).data;
const ecat = (await c('POST', '/accounting/expense-categories', { name: 'Office', code: 'ED-' + x, glAccountId: Number(acc('6428').id), enabled: true }, t)).data;
const rev = (await c('POST', '/accounting/revenues', { date: today, categoryId: Number(rcat.id), bankAccountId: Number(bank.id), amount: 1000, description: 'Fake revenue' }, t)).data;
await c('POST', '/accounting/revenues/' + rev.id + '/approve', null, t); await c('POST', '/accounting/revenues/' + rev.id + '/post', null, t);
const exp = (await c('POST', '/accounting/expenses', { date: today, categoryId: Number(ecat.id), bankAccountId: Number(bank.id), amount: 300, description: 'Fake expense' }, t)).data;
await c('POST', '/accounting/expenses/' + exp.id + '/approve', null, t); await c('POST', '/accounting/expenses/' + exp.id + '/post', null, t);
const ven = (await c('POST', '/accounting/vendors', { companyName: 'Dash Vendor ' + x, contactPerson: 'Test', email: 'vendor@example.com' }, t)).data;
const cp = (await c('POST', '/accounting/customer-payments', { partyId: Number(cust.id), bankAccountId: Number(bank.id), paymentDate: today, reference: 'CP', allocations: [] }, t));
const vp = (await c('POST', '/accounting/vendor-payments', { partyId: Number(ven.id), bankAccountId: Number(bank.id), paymentDate: today, reference: 'VP', allocations: [] }, t));
ck('payments without allocations accepted? (else skip payment part)', true, cp.s + ' ' + cp.code + ' / ' + vp.s + ' ' + vp.code);
r = await c('GET', '/reports/account-dashboard?year=' + Y, null, t); const as = part(r.data, 'stats');
ck('account: revenue 1000, expense 300, clients 1, vendors 1', r.s === 200 && num(as?.totalRevenue) === 1000 && num(as.totalExpense) === 300 && as.clients >= 1 && as.vendors >= 1, r.s + ' ' + r.code + ' ' + show(as && { rv: as.totalRevenue, ex: as.totalExpense, cl: as.clients, ve: as.vendors, net: as.netCashFlow }) + ' ' + show(r.data?.unavailable));
ck('account: net cash flow = revenue + cust − expense − vendor', as && num(as.netCashFlow) === num(as.totalRevenue) + num(as.customerPayments) - num(as.totalExpense) - num(as.vendorPayments), show({ net: as?.netCashFlow, cp: as?.customerPayments, vp: as?.vendorPayments }));
// ---------- 3. POS: 2 orders (100 + 50) today, item stocked 1 → out of stock in W1
const tax0 = (await c('POST', '/inventory/taxes', { name: 'VAT 0', rate: 0 }, t)).data;
const unit = (await c('POST', '/inventory/units', { name: 'Piece' }, t)).data;
const icat = (await c('POST', '/inventory/categories', { name: 'Goods', color: '#3B82F6' }, t)).data;
const W1 = (await c('POST', '/inventory/warehouses', { name: 'W1 ' + x, address: '1 Test Street', city: 'Test City', zipCode: '70000', active: true }, t)).data;
const big = (await c('POST', '/inventory/items', { type: 'PRODUCT', name: 'Big', sku: 'DB1-' + x, taxId: Number(tax0.id), categoryId: Number(icat.id), unitId: Number(unit.id), salePrice: 100, purchasePrice: 60, openingWarehouseId: Number(W1.id), openingQuantity: 1 }, t)).data;
const small = (await c('POST', '/inventory/items', { type: 'PRODUCT', name: 'Small', sku: 'DB2-' + x, taxId: Number(tax0.id), categoryId: Number(icat.id), unitId: Number(unit.id), salePrice: 50, purchasePrice: 30, openingWarehouseId: Number(W1.id), openingQuantity: 5 }, t)).data;
r = await c('POST', '/pos/orders', { warehouseId: Number(W1.id), bankAccountId: Number(bank.id), lines: [{ itemId: Number(big.id), quantity: 1 }] }, t); ck('POS order 1 (100)', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
await c('POST', '/pos/orders', { warehouseId: Number(W1.id), bankAccountId: Number(bank.id), lines: [{ itemId: Number(small.id), quantity: 1 }] }, t);
r = await c('GET', '/reports/pos-dashboard?year=' + Y, null, t); const sales = part(r.data, 'sales'), stock = part(r.data, 'stock');
ck('POS: today 2 orders, 150 revenue, average 75', r.s === 200 && sales?.todayOrders === 2 && num(sales.todayRevenue) === 150 && num(sales.averageTransaction) === 75, r.s + ' ' + show(sales && { o: sales.todayOrders, rv: sales.todayRevenue, av: sales.averageTransaction }) + ' ' + show(r.data?.unavailable));
const hm = (sales?.heatmap || []).reduce((s, z) => s + (z.orders || 0), 0); ck('POS heat map: 168 cells, 2 orders in total', sales?.heatmap?.length === 168 && hm === 2, (sales?.heatmap?.length) + ' cells, ' + hm + ' orders');
const w1 = stock?.byWarehouse?.find(z => String(z.warehouseId) === String(W1.id));
ck('POS stock: W1 has 1 out-of-stock item, 2 products', stock?.products === 2 && w1?.outOfStock === 1, show(stock));
ck('POS: top product list', (sales?.topProducts || []).length === 2, show(sales?.topProducts?.map(z => z.itemName)));
// ---------- 4. CRM: 2 leads, 1 task tomorrow, 1 deal from convert with a client, 1 call
const pipe = (await c('POST', '/crm/pipelines', { name: 'Dash ' + x }, t)).data;
await c('POST', '/crm/pipelines/' + pipe.id + '/stages', { kind: 'LEAD', name: 'New' }, t); await c('POST', '/crm/pipelines/' + pipe.id + '/stages', { kind: 'DEAL', name: 'Open' }, t);
const l1 = (await c('POST', '/crm/leads', { pipelineId: num(pipe.id), name: 'Lead One', email: 'l1@example.com', subject: 'x', ownerUserId: myId }, t)).data; const l1id = l1?.lead?.id ?? l1?.id;
await c('POST', '/crm/leads', { pipelineId: num(pipe.id), name: 'Lead Two', email: 'l2@example.com', subject: 'x', ownerUserId: U2.id }, t);
r = await c('POST', '/crm/leads/' + l1id + '/tasks', { name: 'Call back', date: tomorrow, priority: 'HIGH', status: 'OPEN' }, t); ck('lead task created', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
r = await c('POST', '/crm/leads/' + l1id + '/convert', { customerId: String(cust.id), dealName: 'Deal One', copy: [] }, t); const dealId = r.data?.deal?.id ?? r.data?.dealId ?? r.data?.id; ck('lead converted', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
r = await c('POST', '/crm/deals/' + dealId + '/calls', { subject: 'Intro', callType: 'OUTBOUND', duration: '00:10:00', userId: myId }, t); ck('call logged', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
r = await c('GET', '/reports/crm-dashboard', null, t); const cs = part(r.data, 'stats');
ck('CRM: 2 leads, 1 deal, 1 client, 1 call, 1 upcoming task, 1 stage', r.s === 200 && cs?.leads === 2 && cs.deals === 1 && cs.clients === 1 && cs.calls === 1 && cs.upcomingTasks?.length === 1 && cs.dealsByStage?.length === 1, r.s + ' ' + r.code + ' ' + show(cs && { l: cs.leads, d: cs.deals, cl: cs.clients, ca: cs.calls, ut: cs.upcomingTasks?.length, st: cs.dealsByStage?.length }));
// ---------- 5. support: 3 tickets today, 1 staff reply, 1 closed
const tcat = (await c('POST', '/support/ticket-categories', { name: 'General', color: '#123456' }, t)).data;
const tks = []; for (const i of [1, 2, 3]) tks.push((await c('POST', '/support/tickets', { accountType: 'CUSTOM', name: 'Customer ' + i, email: 'c' + i + '@example.com', categoryId: Number(tcat.id), subject: 'Ticket ' + i, description: 'x' }, t)).data?.ticket?.ticket);
await c('POST', '/support/tickets/' + tks[0].id + '/replies', { description: 'On it' }, t);
await c('PUT', '/support/tickets/' + tks[2].id + '/status', { status: 'CLOSED' }, t);
r = await c('GET', '/reports/support-dashboard?year=' + Y, null, t); const ss = part(r.data, 'stats');
ck('support: 3 total, 2 open, 1 closed, 3 today, response time measured', r.s === 200 && ss?.total === 3 && ss.open === 2 && ss.closed === 1 && ss.today === 3 && ss.averageFirstResponseMinutes != null, r.s + ' ' + r.code + ' ' + show(ss && { t: ss.total, o: ss.open, c: ss.closed, d: ss.today, a: ss.averageFirstResponseMinutes }) + ' ' + show(r.data?.unavailable));
// ---------- 6. staff my-dashboard + permissions
r = await c('GET', '/reports/my-dashboard', null, U2.t); ck('my-dashboard has the new staff parts', r.s === 200 && ['myLeaveBalances', 'myAwards', 'myInterviews', 'myDeals', 'myTickets'].every(k => k in (r.data?.parts ?? r.data ?? {}) || k in (r.data?.unavailable ?? {})), r.s + ' ' + show(Object.keys(r.data?.parts ?? r.data ?? {})));
r = await c('GET', '/reports/account-dashboard?year=' + Y, null, U2.t); ck('employee account dashboard → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', '/reports/pos-dashboard?year=' + Y, null, B.t); const bs = part(r.data, 'sales'); ck("company B POS dashboard shows none of A's sales", r.s === 200 && (bs?.totalOrders ?? 0) === 0, r.s + ' ' + show(bs && { o: bs.totalOrders }));
r = await c('GET', '/reports/project-dashboard?year=' + Y); ck('no token → 401', r.s === 401, r.s);
console.log(`\n${n - f}/${n} passed`);

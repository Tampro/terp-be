// POS checkout and reports
const today = new Date().toISOString().slice(0, 10);
const num = v => Number(v);
const raw = async (m, pa, b, tok) => { const res = await fetch(G + pa, { method: m, headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + tok }, body: b && JSON.stringify(b) }); return { s: res.status, type: res.headers.get('content-type'), buf: Buffer.from(await res.arrayBuffer()) }; };
const stock = async (item, wh) => num(((await c('GET', '/inventory/stock?itemId=' + item, null, t)).data || []).find(z => String(z.warehouseId) === String(wh))?.quantity ?? 0);
const tax = (await c('POST', '/inventory/taxes', { name: 'VAT 10', rate: 10 }, t)).data;
const unit = (await c('POST', '/inventory/units', { name: 'Piece' }, t)).data;
const cat = (await c('POST', '/inventory/categories', { name: 'Drinks', color: '#3B82F6' }, t)).data;
const wh = (await c('POST', '/inventory/warehouses', { name: 'Shop Floor', address: '1 Test Street', city: 'Test City', zipCode: '70000', active: true }, t)).data;
const off = (await c('POST', '/inventory/warehouses', { name: 'Closed Store', address: '2 Test Street', city: 'Test City', zipCode: '70000', active: false }, t)).data;
const tea = (await c('POST', '/inventory/items', { type: 'PRODUCT', name: 'Test Tea', sku: 'TEA-' + x, taxId: Number(tax.id), categoryId: Number(cat.id), unitId: Number(unit.id), salePrice: 100, purchasePrice: 60, openingWarehouseId: Number(wh.id), openingQuantity: 10 }, t)).data;
const wrap = (await c('POST', '/inventory/items', { type: 'SERVICE', name: 'Gift Wrap', generateSku: true, categoryId: Number(cat.id), unitId: Number(unit.id), salePrice: 20, purchasePrice: 0 }, t)).data;
const cust = (await c('POST', '/accounting/customers', { companyName: 'Test Buyer ' + x, contactPerson: 'Test Buyer', email: 'buyer@example.com' }, t)).data;
let r = await c('POST', '/accounting/bank-accounts', { holderName: 'Test Shop', bankName: 'Test Bank', accountNumber: '0001' + x, openingBalance: 1000, active: true }, t);
const bank = r.data; ck('bank account created, balance = opening', r.s === 200 && num(bank.balance) === 1000, r.s + ' ' + r.code + ' ' + r.msg);
r = await c('POST', '/accounting/bank-accounts', { holderName: 'Dup', bankName: 'Test Bank', accountNumber: '0001' + x, openingBalance: 0, active: true }, t); ck('duplicate account number → ACC_007', r.code === 'ACC_007', r.s + ' ' + r.code);
const shut = (await c('POST', '/accounting/bank-accounts', { holderName: 'Old Till', bankName: 'Test Bank', accountNumber: '0002' + x, openingBalance: 0, active: false }, t)).data;
// cashier: only POS permissions
const role = (await c('POST', '/iam/roles', { code: 'CASHIER_' + x, name: 'Cashier', permissions: ['POS_SELL', 'POS_VIEW'] }, t)).data;
const mkUser = async (un, roleIds) => { const ep = pw(); const u = await c('POST', '/iam/users', { username: un, email: un + '@example.com', fullName: 'Test ' + un.split('.')[1], roleIds, password: ep }, t); const ep2 = pw(); await c('POST', '/auth/initial-password', { username: un, currentPassword: u.data?.temporaryPassword || ep, newPassword: ep2 }); return (await c('POST', '/auth/login', { username: un, password: ep2 })).data?.accessToken; };
const ct = await mkUser('ps.cashier.' + x.toLowerCase(), [Number(role.id)]);
ck('cashier logged in', !!ct, String(!!ct));
const cart = (o) => ({ warehouseId: Number(wh.id), lines: [{ itemId: Number(tea.id), quantity: 2 }, { itemId: Number(wrap.id), quantity: 1 }, { itemId: Number(tea.id), quantity: 1 }], ...o });
// grid data for the cashier
r = await c('GET', '/inventory/items?categoryId=' + cat.id, null, ct); ck('cashier sees the product grid', r.s === 200 && r.data.content.length === 2, r.s + ' ' + r.code);
r = await c('GET', '/inventory/stock?warehouseId=' + wh.id, null, ct); ck('cashier sees stock', r.s === 200, r.s + ' ' + r.code);
r = await c('GET', '/accounting/bank-accounts?active=true', null, ct); ck('cashier lists bank accounts', r.s === 200 && r.data.content.length >= 1, r.s + ' ' + r.code);
// preview
r = await c('POST', '/pos/orders/preview', cart({ discount: 10 }), ct);
ck('preview: 3 tea + wrap, VAT 30, discount 10 → 340, walk-in', r.s === 200 && num(r.data.subtotal) === 320 && num(r.data.taxTotal) === 30 && num(r.data.total) === 340 && r.data.customerName === 'Walk-in Customer' && r.data.lines.length === 2, r.s + ' ' + r.code + ' ' + r.msg + ' ' + JSON.stringify(r.data && [r.data.subtotal, r.data.taxTotal, r.data.total]));
r = await c('POST', '/pos/orders/preview', cart({ discount: 351 }), ct); ck('discount above total → POS_005', r.code === 'POS_005', r.s + ' ' + r.code);
r = await c('POST', '/pos/orders/preview', cart({ warehouseId: Number(off.id) }), ct); ck('inactive warehouse → POS_006', r.code === 'POS_006', r.s + ' ' + r.code);
// checkout
r = await c('POST', '/pos/orders', cart({}), ct); ck('checkout without bank → POS_003', r.code === 'POS_003', r.s + ' ' + r.code);
r = await c('POST', '/pos/orders', cart({ bankAccountId: Number(shut.id) }), ct); ck('inactive bank account → POS_004', r.code === 'POS_004', r.s + ' ' + r.code);
r = await c('POST', '/pos/orders', { warehouseId: Number(wh.id), bankAccountId: Number(bank.id), lines: [{ itemId: Number(tea.id), quantity: 11 }] }, ct); ck('more than stock → INV_017, nothing sold', r.code === 'INV_017' && (await stock(tea.id, wh.id)) === 10, r.s + ' ' + r.code);
r = await c('POST', '/pos/orders', cart({ bankAccountId: Number(bank.id), discount: 10 }), ct); const o1 = r.data;
ck('checkout: POS + receipt numbers, total 340', r.s === 200 && /^POS-\d{4}-\d{2}-\d{3}$/.test(o1.order.number) && /^RCPT-/.test(o1.order.receiptNumber) && num(o1.order.total) === 340, r.s + ' ' + r.code + ' ' + r.msg + ' ' + o1?.order?.number);
ck('stock 10 → 7', (await stock(tea.id, wh.id)) === 7, String(await stock(tea.id, wh.id)));
r = await c('GET', '/accounting/bank-accounts/' + bank.id, null, t); ck('bank balance 1000 → 1340', num(r.data.balance) === 1340, r.data?.balance);
r = await c('GET', '/accounting/bank-accounts/' + bank.id + '/transactions', null, t); ck('income transaction from POS_ORDER', r.s === 200 && r.data.content[0].sourceType === 'POS_ORDER' && r.data.content[0].sourceId === o1.order.id, r.s + ' ' + JSON.stringify(r.data?.content?.[0]));
r = await c('POST', '/accounting/bank-accounts/' + bank.id + '/income', { sourceType: 'POS_ORDER', sourceId: Number(o1.order.id), amount: 340 }, ct);
ck('income retry is idempotent', r.s === 200 && num((await c('GET', '/accounting/bank-accounts/' + bank.id, null, t)).data.balance) === 1340, r.s + ' ' + r.code);
r = await c('POST', '/pos/orders', { customerId: Number(cust.id), warehouseId: Number(wh.id), bankAccountId: Number(bank.id), lines: [{ itemId: Number(tea.id), quantity: 5 }] }, ct); const o2 = r.data;
ck('checkout for a customer: name copied, 550', r.s === 200 && o2.order.customerName === 'Test Buyer ' + x && num(o2.order.total) === 550, r.s + ' ' + r.code + ' ' + r.msg);
// orders
r = await c('GET', '/pos/orders?warehouseId=' + wh.id, null, ct); ck('order list 2', r.s === 200 && r.data.content.length === 2, r.s + ' ' + r.data?.content?.length);
r = await c('GET', '/pos/orders?customerId=' + cust.id, null, ct); ck('filter by customer', r.s === 200 && r.data.content.length === 1, r.s + ' ' + r.data?.content?.length);
r = await c('GET', '/pos/orders/' + o1.order.id, null, ct); ck('order detail with tax rows', r.s === 200 && r.data.taxes.length === 1 && num(r.data.taxes[0].amount) === 30, r.s + ' ' + JSON.stringify(r.data?.taxes));
let pd = await raw('GET', '/pos/orders/' + o1.order.id + '/pdf', null, ct); ck('receipt PDF', pd.s === 200 && pd.type.startsWith('application/pdf') && pd.buf.subarray(0, 4).toString() === '%PDF', pd.s + ' ' + pd.type + ' ' + pd.buf.length);
pd = await raw('POST', '/pos/barcodes/pdf', { warehouseId: Number(wh.id), items: [{ itemId: Number(tea.id), copies: 3 }, { itemId: Number(wrap.id), copies: 1 }] }, ct); ck('barcode PDF', pd.s === 200 && pd.buf.subarray(0, 4).toString() === '%PDF', pd.s + ' ' + pd.buf.length);
r = await c('POST', '/pos/barcodes/pdf', { items: [{ itemId: Number(tea.id), copies: 200 }, { itemId: Number(wrap.id), copies: 200 }] }, ct); ck('too many labels → POS_008', r.code === 'POS_008', r.s + ' ' + r.code);
// reports
r = await c('GET', '/pos/reports/sales', null, ct); ck('cashier without POS_REPORT → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', '/pos/reports/sales?from=' + today + '&to=' + today, null, t); ck('sales report: 2 orders, 890', r.s === 200 && r.data.orders === 2 && num(r.data.total) === 890 && r.data.daily.length === 1 && r.data.monthly.length === 1 && r.data.byWarehouse[0].warehouseName === 'Shop Floor', r.s + ' ' + r.code + ' ' + JSON.stringify(r.data && [r.data.orders, r.data.total]));
r = await c('GET', '/pos/reports/products', null, t); ck('product report: tea first, 8 sold', r.s === 200 && r.data.top10[0].itemName === 'Test Tea' && num(r.data.top10[0].quantity) === 8 && r.data.top10[0].orders === 2, r.s + ' ' + JSON.stringify(r.data?.top10?.[0]));
r = await c('GET', '/pos/reports/customers', null, t); ck('customer report: buyer 550 first, walk-in 340', r.s === 200 && r.data.top10[0].customerName === 'Test Buyer ' + x && num(r.data.top10[1].spent) === 340 && r.data.top10[1].customerId === null, r.s + ' ' + JSON.stringify(r.data?.top10?.map(z => z.customerName + ':' + z.spent)));
r = await c('GET', '/pos/reports/sales?from=2026-01-01&to=2027-06-01', null, t); ck('period > 366 days → POS_007', r.code === 'POS_007', r.s + ' ' + r.code);
// isolation & permissions
r = await c('GET', '/pos/orders/' + o1.order.id, null, B.t); ck('company B reads A order → 404 POS_001', r.s === 404 && r.code === 'POS_001', r.s + ' ' + r.code);
pd = await raw('GET', '/pos/orders/' + o1.order.id + '/pdf', null, B.t); ck('company B receipt PDF → 404', pd.s === 404, pd.s);
// receipt reprint: POS_SELL without POS_VIEW prints only the orders it created (after the reports so totals stay)
const sellRole = (await c('POST', '/iam/roles', { code: 'SELLONLY_' + x, name: 'Sell only', permissions: ['POS_SELL'] }, t)).data;
const st = await mkUser('ps.seller.' + x.toLowerCase(), [Number(sellRole.id)]);
pd = await raw('GET', '/pos/orders/' + o1.order.id + '/pdf', null, st); ck('sell-only: another cashier receipt PDF → 404', pd.s === 404, pd.s);
r = await c('POST', '/pos/orders', { warehouseId: Number(wh.id), bankAccountId: Number(bank.id), lines: [{ itemId: Number(wrap.id), quantity: 1 }] }, st); ck('sell-only checkout', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
if (r.s === 200) { pd = await raw('GET', '/pos/orders/' + r.data.order.id + '/pdf', null, st); ck('sell-only: own receipt PDF', pd.s === 200 && pd.buf.subarray(0, 4).toString() === '%PDF', pd.s); pd = await raw('GET', '/pos/orders/' + r.data.order.id + '/pdf', null, ct); ck('POS_VIEW: another cashier receipt PDF', pd.s === 200, pd.s); }
r = await c('POST', '/pos/orders', cart({ bankAccountId: Number(bank.id) }), B.t); ck('company B sells from A warehouse/bank → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('POST', '/accounting/bank-accounts/' + bank.id + '/income', { sourceType: 'POS_ORDER', sourceId: 999, amount: 1 }, B.t); ck('company B income into A account → 404 ACC_006', r.s === 404 && r.code === 'ACC_006', r.s + ' ' + r.code);
r = await c('GET', '/accounting/bank-accounts/' + bank.id, null, B.t); ck('company B reads A bank account → 404', r.s === 404, r.s + ' ' + r.code);
const roles = (await c('GET', '/iam/roles', null, t)).data; const emp = roles.find(z => z.code === 'EMPLOYEE');
const et = await mkUser('ps.employee.' + x.toLowerCase(), [Number(emp.id)]);
r = await c('POST', '/pos/orders', cart({ bankAccountId: Number(bank.id) }), et); ck('employee checkout → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', '/accounting/bank-accounts', null, et); ck('employee bank accounts → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('POST', '/accounting/bank-accounts', { holderName: 'X', bankName: 'X', accountNumber: 'X' + x, openingBalance: 0, active: true }, ct); ck('cashier cannot create bank accounts → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', '/pos/orders'); ck('no token → 401', r.s === 401, r.s);
console.log(`\n${n - f}/${n} passed`);

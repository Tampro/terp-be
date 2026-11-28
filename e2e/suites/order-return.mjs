// order-return (order-return spec): a customer returns delivered items, Back Office approves / rejects and completes —
// refund = what was paid for the units (coupon + points pro rata) − pick-up fee for a change of mind; stock back,
// points spent back, points earned taken back, reversal journal SHOP_ORDER_RETURN, email ORDER_RETURNED.
// Needs `node e2e/customer-client.mjs`.
const KC_URL = (process.env.E2E_KEYCLOAK || 'http://localhost:18180').replace(/\/$/, '');
const [cuUser, cuPw] = fs.readFileSync(path.join(os.homedir(), '.terp-e2e', 'customer-e2e.txt'), 'utf8').trim().split('\n');
const tok = await fetch(KC_URL + '/realms/erp-customer/protocol/openid-connect/token', { method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({ grant_type: 'password', client_id: 'erp-shop-e2e', username: cuUser, password: cuPw }) });
const ct = (await tok.json()).access_token;
ck('customer token', tok.status === 200 && !!ct, String(tok.status));
const sleep = ms => new Promise(res => setTimeout(res, ms));
const waitFor = async (fn, ok, ms = 45000) => { const t0 = Date.now(); let v; while (Date.now() - t0 < ms) { v = await fn(); if (ok(v)) return v; await sleep(1500); } return v; };

let r = await c('PUT', '/settings/company/email', { enabled: true, host: 'smtp.no-such-host.invalid', port: 587, username: 'test', password: 'fake-smtp-pass', encryption: 'TLS', fromAddress: 'noreply@example.com', fromName: 'Test Co' }, t);
ck('company SMTP set to an unresolvable host', r.s === 200, r.s + ' ' + r.code);
const slug = 'rt-a-' + x.toLowerCase();
await c('PUT', '/store/shop-setting', { slug, enabled: true }, t);
const unit = (await c('POST', '/inventory/units', { name: 'Pack' }, t)).data;
const cat = (await c('POST', '/inventory/categories', { name: 'Return ' + x }, t)).data;
const wh = (await c('POST', '/inventory/warehouses', { name: 'Return WH ' + x, address: '1 Test Street', city: 'Test City', zipCode: '00000', active: true }, t)).data;
const store = (await c('POST', '/store/stores', { code: 'RT' + x.slice(0, 4), name: 'Return Store ' + x, warehouseId: Number(wh.id), lat: 37.40, lon: 126.80,
  orderStart: '00:00', orderEnd: '23:59', deliveryFee: 3000, freeDeliveryThreshold: 30000, minOrderAmount: 10000, firstOrderMinAmount: 10000, bagFee: 200 }, t)).data;
await c('POST', `/store/stores/${store.id}/zones`, { name: 'Return zone', area: { type: 'Polygon', coordinates: [[[126.79, 37.39], [126.81, 37.39], [126.81, 37.41], [126.79, 37.41], [126.79, 37.39]]] } }, t);
const egg = (await c('POST', '/inventory/items', { type: 'PRODUCT', name: 'Egg', sku: '__PFX__-REgg-' + x, categoryId: Number(cat.id), unitId: Number(unit.id), salePrice: 1000, purchasePrice: 500, openingWarehouseId: Number(wh.id), openingQuantity: 60 }, t)).data;
await c('POST', '/inventory/store-items', { warehouseId: Number(wh.id), itemId: Number(egg.id), salePrice: 5000 }, t);
const riderRole = (await c('POST', '/iam/roles', { code: 'RRIDER_' + x, name: 'Rider', permissions: ['DELIVERY_RIDE'] }, t)).data;
const un = '__pfx__.rrider.' + x.toLowerCase(); const p1 = pw(); const p2 = pw();
const ru = await c('POST', '/iam/users', { username: un, email: un + '@example.com', fullName: 'Return rider', roleIds: [Number(riderRole.id)], password: p1 }, t);
await c('POST', '/auth/initial-password', { username: un, currentPassword: ru.data?.temporaryPassword || p1, newPassword: p2 });
const rt = (await c('POST', '/auth/login', { username: un, password: p2 })).data?.accessToken;
const now = Date.now(); const iso = ms => new Date(ms).toISOString();
const code = 'T' + x.slice(0, 6).toUpperCase();
const coupon = (await c('POST', '/order/coupons', { code, name: 'Return coupon', type: 'PERCENT', value: 10, maxDiscount: null, minSubtotal: null,
  startsAt: iso(now - 3600e3), endsAt: iso(now + 86400e3), totalLimit: null, perCustomerLimit: null, active: true }, t)).data;
await c('PUT', '/order/mileage-policy', { active: true, earnRate: 1, expiryMonths: 12, minRedeem: 1000 }, t);
ck('setup', store?.id && egg?.id && rt && coupon?.id, [store?.id, egg?.id, !!rt, coupon?.id].join(','));

r = await c('GET', '/order/return-policy', null, t);
ck('default return policy: 3,000, 30 days, not saved', r.s === 200 && Number(r.data.pickupFee) === 3000 && r.data.windowDays === 30 && r.data.saved === false, r.s + ' ' + JSON.stringify(r.data));

const join = (await c('POST', `/shop/${slug}/me`, { termsAgreed: true, privacyAgreed: true, termsVersion: '2026-11', privacyVersion: '2026-11' }, ct)).data;
const customerId = join?.customerId;
const home = (await c('POST', `/shop/${slug}/me/addresses`, { label: 'Home', recipientName: 'E2E Customer', phone: '010-1234-5678', postcode: '08502', address1: '1 Test-ro', lat: 37.40, lon: 126.80 }, ct)).data;
const stock = async () => Number((await c('GET', `/inventory/stock?itemId=${egg.id}&warehouseId=${wh.id}`, null, t)).data?.[0]?.quantity);
const balance = async () => (await c('GET', `/shop/${slug}/mileage`, null, ct)).data?.balance;
const ask = (o, reason, qty, tk = ct) => c('POST', `/shop/${slug}/orders/${o.orderNo}/returns`, { reason, note: 'E2E', lines: [{ itemId: Number(egg.id), quantity: qty }] }, tk);

await c('PUT', `/shop/${slug}/stores/${store.id}/cart/items/${egg.id}`, { quantity: 4 }, ct);
const warm = (await c('POST', `/shop/${slug}/stores/${store.id}/orders`, { addressId: Number(home.id), substitution: 'ALLOW' }, ct)).data;
await c('POST', `/shop/${slug}/orders/${warm.orderNo}/cancel`, null, ct);
await c('POST', `/order/mileage/customers/${customerId}/adjust`, { points: 2000, reason: 'Welcome' }, t);

await c('PUT', `/shop/${slug}/stores/${store.id}/cart/items/${egg.id}`, { quantity: 4 }, ct);            // 20,000 goods
r = await c('POST', `/shop/${slug}/stores/${store.id}/orders`, { addressId: Number(home.id), substitution: 'ALLOW', couponCode: code, usePoints: 1000 }, ct);
const o = r.data;
ck('order: coupon 2,000 and 1,000 points', r.s === 200 && Number(o.discount) === 2000 && o.pointsUsed === 1000, r.s + ' ' + r.code);
await c('POST', `/shop/${slug}/orders/${o.orderNo}/pay`, { outcome: 'APPROVED' }, ct);
const before = await stock();
r = await ask(o, 'DEFECT', 1); ck('paid, not delivered → RET_001', r.s === 400 && r.code === 'RET_001', r.s + ' ' + r.code);
const d = await waitFor(async () => ((await c('GET', `/delivery/deliveries?storeId=${store.id}`, null, t)).data?.content || []).find(z => z.orderId === o.id), v => !!v);
await c('POST', `/delivery/deliveries/${d.id}/assign`, { riderUserId: Number(ru.data?.user?.id) }, t);
await c('POST', `/delivery/my-deliveries/${d.id}/pick-up`, null, rt);
await c('POST', `/delivery/my-deliveries/${d.id}/deliver`, null, rt);
const b1 = await waitFor(balance, v => v === 1170);
ck('delivered: 1 % of 17,000 earned → 1,170 points', b1 === 1170, String(b1));
await waitFor(async () => (await c('GET', `/shop/${slug}/orders/${o.orderNo}`, null, ct)).data?.deliveryStatus, v => v === 'DELIVERED');

r = await ask(o, 'DEFECT', 5); ck('5 of 4 → RET_003', r.s === 400 && r.code === 'RET_003', r.s + ' ' + r.code);
r = await ask(o, 'DEFECT', 1); const first = r.data;
ck('DEFECT 1 → REQUESTED, no fee, refund 4,250', r.s === 200 && first.status === 'REQUESTED' && Number(first.pickupFee) === 0 && Number(first.refundAmount) === 4250, r.s + ' ' + r.code + ' ' + JSON.stringify(first && [first.pickupFee, first.refundAmount]));
r = await ask(o, 'DEFECT', 1); ck('second open return → RET_005', r.s === 400 && r.code === 'RET_005', r.s + ' ' + r.code);
r = await c('POST', `/order/returns/${first.id}/reject`, { note: 'Not defective' }, t);
ck('Back Office rejects', r.s === 200 && r.data.status === 'REJECTED' && r.data.rejectNote === 'Not defective', r.s + ' ' + r.code);

r = await ask(o, 'CHANGE_OF_MIND', 2); const ret = r.data;
ck('change of mind 2 of 4: share 8,500 − fee 3,000 = 5,500', r.s === 200 && Number(ret.refundShare) === 8500 && Number(ret.pickupFee) === 3000 && Number(ret.refundAmount) === 5500 && ret.pointsBack === 500 && ret.earnedBack === 85,
  r.s + ' ' + r.code + ' ' + JSON.stringify(ret && [ret.refundShare, ret.pickupFee, ret.refundAmount, ret.pointsBack, ret.earnedBack]));
r = await c('GET', `/shop/${slug}/orders/${o.orderNo}/returns`, null, ct);
ck('customer sees both returns, newest first', r.s === 200 && r.data.length === 2 && r.data[0].id === ret.id, r.s + ' ' + r.data?.length);
r = await c('POST', `/order/returns/${ret.id}/complete`, null, t); ck('complete before approval → RET_006', r.s === 400 && r.code === 'RET_006', r.s + ' ' + r.code);
r = await c('GET', `/order/returns/${ret.id}`, null, B.t); ck('another company → 404 RET_007', r.s === 404 && r.code === 'RET_007', r.s + ' ' + r.code);
const emp = (await c('GET', '/iam/roles', null, t)).data.find(z => z.code === 'EMPLOYEE'); const eun = '__pfx__.rtemp.' + x.toLowerCase(); const e1 = pw(); const e2 = pw();
const eu = await c('POST', '/iam/users', { username: eun, email: eun + '@example.com', fullName: 'Return Employee', roleIds: [Number(emp.id)], password: e1 }, t);
await c('POST', '/auth/initial-password', { username: eun, currentPassword: eu.data?.temporaryPassword || e1, newPassword: e2 });
const et = (await c('POST', '/auth/login', { username: eun, password: e2 })).data?.accessToken;
r = await c('POST', `/order/returns/${ret.id}/approve`, {}, et); ck('employee without ORDER_RETURN_MANAGE → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', `/order/returns?status=REQUESTED&storeId=${store.id}`, null, t);
ck('Back Office list filters REQUESTED of the store', r.s === 200 && (r.data.content || []).some(z => z.id === ret.id), r.s + ' ' + r.code);

r = await c('POST', `/order/returns/${ret.id}/approve`, {}, t); ck('approved', r.s === 200 && r.data.status === 'APPROVED', r.s + ' ' + r.code);
r = await c('POST', `/order/returns/${ret.id}/complete`, null, t);
ck('completed', r.s === 200 && r.data.status === 'COMPLETED' && !!r.data.completedAt, r.s + ' ' + r.code);
ck('stock +2', (await stock()) === before + 2, String(await stock()));
ck('points: +500 spent back, −85 earned back → 1,585', (await balance()) === 1585, String(await balance()));
r = await c('POST', `/order/returns/${ret.id}/complete`, null, t); ck('second complete → RET_006', r.s === 400 && r.code === 'RET_006', r.s + ' ' + r.code);

let je;
for (let i = 0; i < 30 && !je; i++) { je = ((await c('GET', '/accounting/journal-entries?sourceType=SHOP_ORDER_RETURN&size=100', null, t)).data?.content || []).find(e => e.sourceId === ret.id); if (!je) await sleep(1000); }
const lines = je ? (await c('GET', '/accounting/journal-entries/' + je.id, null, t)).data?.lines || [] : [];
const sum = (acc, side) => lines.filter(l => l.accountCode === acc).reduce((s, l) => s + Number(l[side] || 0), 0);
ck('journal SHOP_ORDER_RETURN: Dr 5111 7,727, Dr 33311 500, Cr 5113 2,727, Cr 138 5,500, Dr 156 / Cr 632 1,000',
  sum('5111', 'debit') === 7727 && sum('33311', 'debit') === 500 && sum('138', 'credit') === 5500 && sum('156', 'debit') === 1000 && sum('632', 'credit') === 1000,
  JSON.stringify(lines.map(l => [l.accountCode, l.debit, l.credit])));
const mail = await waitFor(async () => ((await c('GET', '/notifications/deliveries?size=100&channel=EMAIL&eventType=order.returned', null, t)).data?.content || []), v => v.some(z => z.attempts > 0));
ck('email ORDER_RETURNED attempted', mail.some(z => z.attempts > 0), JSON.stringify(mail[0])?.slice(0, 200));

r = await c('PUT', '/order/return-policy', { pickupFee: 0 }, t); ck('policy: fee 0 saved', r.s === 200 && Number(r.data.pickupFee) === 0 && r.data.saved, r.s + ' ' + r.code);
r = await ask(o, 'CHANGE_OF_MIND', 3); ck('only 2 left → RET_003', r.s === 400 && r.code === 'RET_003', r.s + ' ' + r.code);
r = await ask(o, 'CHANGE_OF_MIND', 2); ck('fee 0: change of mind refunds the full share 8,500', r.s === 200 && Number(r.data.refundAmount) === 8500, r.s + ' ' + r.code + ' ' + r.data?.refundAmount);
await c('DELETE', `/shop/${slug}/me`, null, ct);
console.log(`\n${n - f}/${n} passed`);

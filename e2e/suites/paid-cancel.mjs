// paid-cancel (paid-order-cancel spec): Back Office cancels a PAID order before pick-up and refunds it in full —
// delivery CANCELLED, stock back, coupon use and points given back, reversal journal SHOP_ORDER_REFUND, email
// ORDER_REFUNDED. Refusals: not paid, picked up, another company, no ORDER_CANCEL. Needs `node e2e/customer-client.mjs`.
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
const slug = 'pc-a-' + x.toLowerCase();
await c('PUT', '/store/shop-setting', { slug, enabled: true }, t);
const unit = (await c('POST', '/inventory/units', { name: 'Pack' }, t)).data;
const cat = (await c('POST', '/inventory/categories', { name: 'Refund ' + x }, t)).data;
const wh = (await c('POST', '/inventory/warehouses', { name: 'Refund WH ' + x, address: '1 Test Street', city: 'Test City', zipCode: '00000', active: true }, t)).data;
const store = (await c('POST', '/store/stores', { code: 'PC' + x.slice(0, 4), name: 'Refund Store ' + x, warehouseId: Number(wh.id), lat: 37.40, lon: 126.80,
  orderStart: '00:00', orderEnd: '23:59', deliveryFee: 3000, freeDeliveryThreshold: 30000, minOrderAmount: 10000, firstOrderMinAmount: 10000, bagFee: 200 }, t)).data;
await c('POST', `/store/stores/${store.id}/zones`, { name: 'Refund zone', area: { type: 'Polygon', coordinates: [[[126.79, 37.39], [126.81, 37.39], [126.81, 37.41], [126.79, 37.41], [126.79, 37.39]]] } }, t);
const egg = (await c('POST', '/inventory/items', { type: 'PRODUCT', name: 'Egg', sku: '__PFX__-PEgg-' + x, categoryId: Number(cat.id), unitId: Number(unit.id), salePrice: 1000, purchasePrice: 500, openingWarehouseId: Number(wh.id), openingQuantity: 60 }, t)).data;
await c('POST', '/inventory/store-items', { warehouseId: Number(wh.id), itemId: Number(egg.id), salePrice: 5000 }, t);
const riderRole = (await c('POST', '/iam/roles', { code: 'PRIDER_' + x, name: 'Rider', permissions: ['DELIVERY_RIDE'] }, t)).data;
const un = '__pfx__.prider.' + x.toLowerCase(); const p1 = pw(); const p2 = pw();
const ru = await c('POST', '/iam/users', { username: un, email: un + '@example.com', fullName: 'Refund rider', roleIds: [Number(riderRole.id)], password: p1 }, t);
await c('POST', '/auth/initial-password', { username: un, currentPassword: ru.data?.temporaryPassword || p1, newPassword: p2 });
const rt = (await c('POST', '/auth/login', { username: un, password: p2 })).data?.accessToken;
const now = Date.now(); const iso = ms => new Date(ms).toISOString();
const code = 'R' + x.slice(0, 6).toUpperCase();
const coupon = (await c('POST', '/order/coupons', { code, name: 'Refund coupon', type: 'PERCENT', value: 10, maxDiscount: null, minSubtotal: null,
  startsAt: iso(now - 3600e3), endsAt: iso(now + 86400e3), totalLimit: null, perCustomerLimit: null, active: true }, t)).data;
ck('setup', store?.id && egg?.id && rt && coupon?.id, [store?.id, egg?.id, !!rt, coupon?.id].join(','));

const join = (await c('POST', `/shop/${slug}/me`, { termsAgreed: true, privacyAgreed: true, termsVersion: '2026-11', privacyVersion: '2026-11' }, ct)).data;
const customerId = join?.customerId;
const home = (await c('POST', `/shop/${slug}/me/addresses`, { label: 'Home', recipientName: 'E2E Customer', phone: '010-1234-5678', postcode: '08502', address1: '1 Test-ro', lat: 37.40, lon: 126.80 }, ct)).data;
const place = async (extra = {}) => {
  await c('PUT', `/shop/${slug}/stores/${store.id}/cart/items/${egg.id}`, { quantity: 4 }, ct);           // 20,000 goods
  return c('POST', `/shop/${slug}/stores/${store.id}/orders`, { addressId: Number(home.id), substitution: 'ALLOW', ...extra }, ct);
};
const pay = o => c('POST', `/shop/${slug}/orders/${o.orderNo}/pay`, { outcome: 'APPROVED' }, ct);
const cancel = (o, body = { reason: 'Packing damage' }, tk = t) => c('POST', `/order/orders/${o.id}/cancel`, body, tk);
const stock = async () => Number((await c('GET', `/inventory/stock?itemId=${egg.id}&warehouseId=${wh.id}`, null, t)).data?.[0]?.quantity);
const balance = async () => (await c('GET', `/shop/${slug}/mileage`, null, ct)).data?.balance;
const deliveryOf = o => waitFor(async () => ((await c('GET', `/delivery/deliveries?storeId=${store.id}`, null, t)).data?.content || []).find(z => z.orderId === o.id), v => !!v);

const oA = (await place()).data;
r = await cancel(oA); ck('pending order → ORD_019', r.s === 400 && r.code === 'ORD_019', r.s + ' ' + r.code);
await c('POST', `/shop/${slug}/orders/${oA.orderNo}/cancel`, null, ct);
r = await c('POST', `/order/mileage/customers/${customerId}/adjust`, { points: 2000, reason: 'Welcome' }, t);
ck('customer has 2,000 points', r.s === 200 && (await balance()) === 2000, r.s + ' ' + r.code);

const before = await stock();
r = await place({ couponCode: code, usePoints: 1000 }); const oB = r.data;
ck('order with coupon and 1,000 points', r.s === 200 && oB.pointsUsed === 1000 && oB.couponCode === code, r.s + ' ' + r.code);
await pay(oB);
ck('paid: stock −4, 1,000 points left', (await stock()) === before - 4 && (await balance()) === 1000, String(await stock()));
const dB = await deliveryOf(oB);
ck('delivery READY', dB?.status === 'READY', JSON.stringify(dB?.status));
r = await c('GET', '/order/coupons/' + coupon.id, null, t); ck('coupon used once', r.data?.usedCount === 1, String(r.data?.usedCount));

r = await cancel(oB, { reason: '' }); ck('no reason → 400', r.s === 400, r.s + ' ' + r.code);
r = await cancel(oB, undefined, B.t); ck('another company → 404 ORD_018', r.s === 404 && r.code === 'ORD_018', r.s + ' ' + r.code);
const emp = (await c('GET', '/iam/roles', null, t)).data.find(z => z.code === 'EMPLOYEE'); const eun = '__pfx__.pcemp.' + x.toLowerCase(); const e1 = pw(); const e2 = pw();
const eu = await c('POST', '/iam/users', { username: eun, email: eun + '@example.com', fullName: 'Refund Employee', roleIds: [Number(emp.id)], password: e1 }, t);
await c('POST', '/auth/initial-password', { username: eun, currentPassword: eu.data?.temporaryPassword || e1, newPassword: e2 });
const et = (await c('POST', '/auth/login', { username: eun, password: e2 })).data?.accessToken;
r = await cancel(oB, undefined, et); ck('employee without ORDER_CANCEL → 403', r.s === 403, r.s + ' ' + r.code);

r = await cancel(oB);
ck('Back Office cancel → REFUNDED with refundedAt and reason', r.s === 200 && r.data.status === 'REFUNDED' && !!r.data.refundedAt && r.data.cancelReason === 'Packing damage', r.s + ' ' + r.code + ' ' + r.data?.status);
r = await c('GET', '/delivery/deliveries/' + dB.id, null, t); ck('delivery CANCELLED', r.data?.delivery?.status === 'CANCELLED', String(r.data?.delivery?.status));
ck('stock back', (await stock()) === before, String(await stock()));
r = await c('GET', '/order/coupons/' + coupon.id, null, t); ck('coupon use given back (usedCount 0)', r.data?.usedCount === 0, String(r.data?.usedCount));
ck('points given back: 2,000', (await balance()) === 2000, String(await balance()));
r = await cancel(oB); ck('second cancel → ORD_019', r.s === 400 && r.code === 'ORD_019', r.s + ' ' + r.code);
r = await c('GET', `/shop/${slug}/orders/${oB.orderNo}`, null, ct);
ck('customer sees REFUNDED, not the reason', r.data?.status === 'REFUNDED' && !!r.data.refundedAt && r.data.cancelReason === null, JSON.stringify(r.data && [r.data.status, r.data.cancelReason]));

const entry = async (type) => { let e; for (let i = 0; i < 30 && !e; i++) { e = ((await c('GET', `/accounting/journal-entries?sourceType=${type}&size=100`, null, t)).data?.content || []).find(z => z.sourceId === oB.id); if (!e) await sleep(1000); } return e ? (await c('GET', '/accounting/journal-entries/' + e.id, null, t)).data?.lines || [] : []; };
const sum = (ls, acc, side) => ls.filter(l => l.accountCode === acc).reduce((s, l) => s + Number(l[side] || 0), 0);
const post = await entry('SHOP_ORDER'); const rev = await entry('SHOP_ORDER_REFUND');
ck('reversal journal posted', rev.length > 0, 'no SHOP_ORDER_REFUND entry');
ck('reversal mirrors the order: 5111, 33311, 138, 632, 156', ['5111', '33311'].every(a => sum(rev, a, 'debit') === sum(post, a, 'credit') && sum(post, a, 'credit') > 0)
  && sum(rev, '138', 'credit') === sum(post, '138', 'debit') && sum(rev, '156', 'debit') === sum(post, '156', 'credit') && sum(rev, '632', 'credit') === sum(post, '632', 'debit'),
  JSON.stringify(rev.map(l => [l.accountCode, l.debit, l.credit])));
const mail = await waitFor(async () => ((await c('GET', '/notifications/deliveries?size=100&channel=EMAIL&eventType=order.refunded', null, t)).data?.content || []), v => v.some(z => z.attempts > 0));
ck('email ORDER_REFUNDED attempted', mail.some(z => z.attempts > 0), JSON.stringify(mail[0])?.slice(0, 200));

const oC = (await place()).data; await pay(oC);
r = await c('POST', `/shop/${slug}/orders/${oC.orderNo}/cancel`, null, ct); ck('customer cancel of a paid order still ORD_017', r.s === 400 && r.code === 'ORD_017', r.s + ' ' + r.code);
const dC = await deliveryOf(oC);
await c('POST', `/delivery/deliveries/${dC.id}/assign`, { riderUserId: Number(ru.data?.user?.id) }, t);
await c('POST', `/delivery/my-deliveries/${dC.id}/pick-up`, null, rt);
r = await cancel(oC); ck('picked up → ORD_020, order stays PAID', r.s === 400 && r.code === 'ORD_020' && (await c('GET', '/order/orders/' + oC.id, null, t)).data?.status === 'PAID', r.s + ' ' + r.code);
await c('POST', `/delivery/my-deliveries/${dC.id}/deliver`, null, rt);
await c('DELETE', `/shop/${slug}/me`, null, ct);
console.log(`\n${n - f}/${n} passed`);

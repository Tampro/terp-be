// coupon (express-coupon spec): Back Office coupons (COUPON_VIEW / COUPON_MANAGE) → cart preview → order with a coupon
// (use reserved at placement, released on cancel), goods revenue net of the discount. Needs `node e2e/customer-client.mjs`.
const KC_URL = (process.env.E2E_KEYCLOAK || 'http://localhost:18180').replace(/\/$/, '');
const [cuUser, cuPw] = fs.readFileSync(path.join(os.homedir(), '.terp-e2e', 'customer-e2e.txt'), 'utf8').trim().split('\n');
const tok = await fetch(KC_URL + '/realms/erp-customer/protocol/openid-connect/token', { method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({ grant_type: 'password', client_id: 'erp-shop-e2e', username: cuUser, password: cuPw }) });
const ct = (await tok.json()).access_token;
ck('customer token', tok.status === 200 && !!ct, String(tok.status));
const sleep = ms => new Promise(res => setTimeout(res, ms));

const slug = 'cp-a-' + x.toLowerCase();
await c('PUT', '/store/shop-setting', { slug, enabled: true }, t);
const unit = (await c('POST', '/inventory/units', { name: 'Pack' }, t)).data;
const cat = (await c('POST', '/inventory/categories', { name: 'Coupon ' + x }, t)).data;
const wh = (await c('POST', '/inventory/warehouses', { name: 'Coupon WH ' + x, address: '1 Test Street', city: 'Test City', zipCode: '00000', active: true }, t)).data;
const store = (await c('POST', '/store/stores', { code: 'CP' + x.slice(0, 4), name: 'Coupon Store ' + x, warehouseId: Number(wh.id), lat: 37.40, lon: 126.80,
  orderStart: '00:00', orderEnd: '23:59', deliveryFee: 3000, freeDeliveryThreshold: 30000, minOrderAmount: 10000, firstOrderMinAmount: 10000, bagFee: 200 }, t)).data;
await c('POST', `/store/stores/${store.id}/zones`, { name: 'Coupon zone', area: { type: 'Polygon', coordinates: [[[126.79, 37.39], [126.81, 37.39], [126.81, 37.41], [126.79, 37.41], [126.79, 37.39]]] } }, t);
const egg = (await c('POST', '/inventory/items', { type: 'PRODUCT', name: 'Egg', sku: '__PFX__-CEgg-' + x, categoryId: Number(cat.id), unitId: Number(unit.id), salePrice: 1000, purchasePrice: 500, openingWarehouseId: Number(wh.id), openingQuantity: 50 }, t)).data;
await c('POST', '/inventory/store-items', { warehouseId: Number(wh.id), itemId: Number(egg.id), salePrice: 5000 }, t);
ck('setup', store?.id && egg?.id, [store?.id, egg?.id].join(','));

const now = Date.now(); const iso = ms => new Date(ms).toISOString();
const coupon = (code, extra = {}) => ({ code, name: 'Coupon ' + code, type: 'PERCENT', value: 10, maxDiscount: null, minSubtotal: null,
  startsAt: iso(now - 3600e3), endsAt: iso(now + 86400e3), totalLimit: null, perCustomerLimit: null, active: true, ...extra });
const pc = 'P' + x.slice(0, 6).toUpperCase(), ac = 'A' + x.slice(0, 6).toUpperCase(), lc = 'L' + x.slice(0, 6).toUpperCase(), oc = 'O' + x.slice(0, 6).toUpperCase();
let r = await c('POST', '/order/coupons', coupon(pc.toLowerCase(), { maxDiscount: 1500, perCustomerLimit: 1 }), t);
const percent = r.data;
ck('PERCENT coupon created, code upper-cased', r.s === 200 && percent.code === pc, r.s + ' ' + r.code + ' ' + percent?.code);
r = await c('POST', '/order/coupons', coupon(pc), t); ck('duplicate code → CPN_001', r.s === 400 && r.code === 'CPN_001', r.s + ' ' + r.code);
r = await c('POST', '/order/coupons', coupon('BAD' + x.slice(0, 4), { value: 120 }), t); ck('percent over 100 → CPN_002', r.s === 400 && r.code === 'CPN_002', r.s + ' ' + r.code);
const amount = (await c('POST', '/order/coupons', coupon(ac, { type: 'AMOUNT', value: 3000, totalLimit: 1 }), t)).data;
const late = (await c('POST', '/order/coupons', coupon(lc, { startsAt: iso(now + 86400e3), endsAt: iso(now + 2 * 86400e3) }), t)).data;
const unused = (await c('POST', '/order/coupons', coupon(oc), t)).data;
ck('more coupons created', amount?.id && late?.id && unused?.id, [amount?.id, late?.id, unused?.id].join(','));

r = await c('POST', `/shop/${slug}/me`, { termsAgreed: true, privacyAgreed: true, termsVersion: '2026-11', privacyVersion: '2026-11' }, ct);
const home = (await c('POST', `/shop/${slug}/me/addresses`, { label: 'Home', recipientName: 'E2E Customer', phone: '010-1234-5678', postcode: '08502', address1: '1 Test-ro', lat: 37.40, lon: 126.80 }, ct)).data;
const cart = (code) => c('GET', `/shop/${slug}/stores/${store.id}/cart` + (code ? '?couponCode=' + code : ''), null, ct);
const place = (code) => c('POST', `/shop/${slug}/stores/${store.id}/orders`, { addressId: Number(home.id), substitution: 'ALLOW', couponCode: code }, ct);
const pay = (no) => c('POST', `/shop/${slug}/orders/${no}/pay`, { outcome: 'APPROVED' }, ct);
await c('PUT', `/shop/${slug}/stores/${store.id}/cart/items/${egg.id}`, { quantity: 4 }, ct);   // 20,000 of goods

r = await cart(pc.toLowerCase());
ck('preview: 10% capped at 1,500', r.s === 200 && Number(r.data.discount) === 1500 && Number(r.data.total) === 21500 && r.data.couponCode === pc,
  r.s + ' ' + JSON.stringify(r.data && [r.data.discount, r.data.total, r.data.couponError]));
r = await cart(lc); ck('preview of a coupon not started → couponError CPN_008', r.s === 200 && r.data.couponError === 'CPN_008' && Number(r.data.discount) === 0, r.s + ' ' + r.data?.couponError);
r = await cart('NOPE' + x.slice(0, 3)); ck('preview of an unknown code → couponError CPN_007', r.data?.couponError === 'CPN_007', String(r.data?.couponError));

r = await place(lc); ck('placing with a coupon not started → CPN_008', r.s === 400 && r.code === 'CPN_008', r.s + ' ' + r.code);
r = await place(pc); const o1 = r.data;
ck('order with the PERCENT coupon: discount snapshot, totals', r.s === 200 && o1.couponCode === pc && Number(o1.discount) === 1500 && Number(o1.subtotal) === 20000 && Number(o1.total) === 21500,
  r.s + ' ' + r.code + ' ' + JSON.stringify(o1 && [o1.couponCode, o1.discount, o1.total]));
r = await c('GET', '/order/coupons/' + percent.id, null, t); ck('the use is reserved (usedCount 1)', r.data?.usedCount === 1, String(r.data?.usedCount));
await c('POST', `/shop/${slug}/orders/${o1.orderNo}/cancel`, null, ct);
r = await c('GET', '/order/coupons/' + percent.id, null, t); ck('cancel gives the use back (usedCount 0)', r.data?.usedCount === 0, String(r.data?.usedCount));
r = await place(pc); const o2 = r.data; ck('the coupon can be used again after the cancel', r.s === 200 && Number(o2?.discount) === 1500, r.s + ' ' + r.code);
r = await pay(o2.orderNo); ck('paid', r.s === 200 && r.data.status === 'PAID', r.s + ' ' + r.code);
await c('PUT', `/shop/${slug}/stores/${store.id}/cart/items/${egg.id}`, { quantity: 4 }, ct);
r = await place(pc); ck('per-customer limit 1 → CPN_011', r.s === 400 && r.code === 'CPN_011', r.s + ' ' + r.code);

r = await place(ac); const o3 = r.data; ck('AMOUNT coupon 3,000', r.s === 200 && Number(o3?.discount) === 3000 && Number(o3?.total) === 20000, r.s + ' ' + r.code + ' ' + o3?.total);
await pay(o3.orderNo);
await c('PUT', `/shop/${slug}/stores/${store.id}/cart/items/${egg.id}`, { quantity: 4 }, ct);
r = await place(ac); ck('total limit 1 reached → CPN_010', r.s === 400 && r.code === 'CPN_010', r.s + ' ' + r.code);

let bo;
for (let i = 0; i < 20; i++) { bo = (await c('GET', '/order/orders/' + o2.id, null, t)).data; if (bo?.journalStatus === 'POSTED') break; await sleep(1000); }
ck('Back Office order shows the coupon', bo?.couponCode === pc && Number(bo?.discount) === 1500, JSON.stringify(bo && [bo.couponCode, bo.discount]));
const je = ((await c('GET', '/accounting/journal-entries?sourceType=SHOP_ORDER&size=100', null, t)).data?.content || []).find(e => e.sourceId === o2.id);
const lines = je ? (await c('GET', '/accounting/journal-entries/' + je.id, null, t)).data?.lines || [] : [];
const cr = code => lines.filter(l => l.accountCode === code).reduce((s, l) => s + Number(l.credit || 0), 0);
// goods 20,000 − 1,500 = 18,500 incl. VAT → 16,818 + 1,682; delivery 3,000 → 2,727 + 273
ck('journal: 5111 net of the discount, 138 = total', cr('5111') === 16818 && cr('33311') === 1955 && lines.filter(l => l.accountCode === '138').reduce((s, l) => s + Number(l.debit || 0), 0) === 21500,
  JSON.stringify([cr('5111'), cr('33311')]));

r = await c('DELETE', '/order/coupons/' + percent.id, null, t); ck('delete a used coupon → CPN_004', r.s === 400 && r.code === 'CPN_004', r.s + ' ' + r.code);
r = await c('DELETE', '/order/coupons/' + unused.id, null, t); ck('delete an unused coupon', r.s === 200, r.s + ' ' + r.code);
r = await c('POST', '/order/coupons', coupon(oc), t); ck('its code is free again', r.s === 200, r.s + ' ' + r.code);
r = await c('GET', '/order/coupons/' + percent.id, null, B.t); ck('another company → CPN_005 404', r.s === 404 && r.code === 'CPN_005', r.s + ' ' + r.code);
const emp = (await c('GET', '/iam/roles', null, t)).data.find(z => z.code === 'EMPLOYEE'); const un = '__pfx__.cpemp.' + x.toLowerCase(); const p1 = pw(); const p2 = pw();
const eu = await c('POST', '/iam/users', { username: un, email: un + '@example.com', fullName: 'Coupon Employee', roleIds: [Number(emp.id)], password: p1 }, t);
await c('POST', '/auth/initial-password', { username: un, currentPassword: eu.data?.temporaryPassword || p1, newPassword: p2 });
const et = (await c('POST', '/auth/login', { username: un, password: p2 })).data?.accessToken;
r = await c('GET', '/order/coupons', null, et); ck('employee without COUPON_VIEW → 403', r.s === 403, r.s + ' ' + r.code);
await c('DELETE', `/shop/${slug}/me`, null, ct);
console.log(`\n${n - f}/${n} passed`);

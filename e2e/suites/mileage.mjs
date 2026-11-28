// mileage (express-mileage spec): points earned when the delivery is DELIVERED, spent FIFO at checkout (reserved at
// placement, given back on cancel), Back Office policy / statement / adjustment, revenue net of points.
// Needs `node e2e/customer-client.mjs`.
const KC_URL = (process.env.E2E_KEYCLOAK || 'http://localhost:18180').replace(/\/$/, '');
const [cuUser, cuPw] = fs.readFileSync(path.join(os.homedir(), '.terp-e2e', 'customer-e2e.txt'), 'utf8').trim().split('\n');
const tok = await fetch(KC_URL + '/realms/erp-customer/protocol/openid-connect/token', { method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({ grant_type: 'password', client_id: 'erp-shop-e2e', username: cuUser, password: cuPw }) });
const ct = (await tok.json()).access_token;
ck('customer token', tok.status === 200 && !!ct, String(tok.status));
const sleep = ms => new Promise(res => setTimeout(res, ms));
const waitFor = async (fn, ok, ms = 30000) => { const t0 = Date.now(); let v; while (Date.now() - t0 < ms) { v = await fn(); if (ok(v)) return v; await sleep(1000); } return v; };

const slug = 'ml-a-' + x.toLowerCase();
await c('PUT', '/store/shop-setting', { slug, enabled: true }, t);
const unit = (await c('POST', '/inventory/units', { name: 'Pack' }, t)).data;
const cat = (await c('POST', '/inventory/categories', { name: 'Mileage ' + x }, t)).data;
const wh = (await c('POST', '/inventory/warehouses', { name: 'Mileage WH ' + x, address: '1 Test Street', city: 'Test City', zipCode: '00000', active: true }, t)).data;
const store = (await c('POST', '/store/stores', { code: 'ML' + x.slice(0, 4), name: 'Mileage Store ' + x, warehouseId: Number(wh.id), lat: 37.40, lon: 126.80,
  orderStart: '00:00', orderEnd: '23:59', deliveryFee: 3000, freeDeliveryThreshold: 30000, minOrderAmount: 10000, firstOrderMinAmount: 10000, bagFee: 200 }, t)).data;
await c('POST', `/store/stores/${store.id}/zones`, { name: 'Mileage zone', area: { type: 'Polygon', coordinates: [[[126.79, 37.39], [126.81, 37.39], [126.81, 37.41], [126.79, 37.41], [126.79, 37.39]]] } }, t);
const egg = (await c('POST', '/inventory/items', { type: 'PRODUCT', name: 'Egg', sku: '__PFX__-MEgg-' + x, categoryId: Number(cat.id), unitId: Number(unit.id), salePrice: 1000, purchasePrice: 500, openingWarehouseId: Number(wh.id), openingQuantity: 60 }, t)).data;
await c('POST', '/inventory/store-items', { warehouseId: Number(wh.id), itemId: Number(egg.id), salePrice: 5000 }, t);
const riderRole = (await c('POST', '/iam/roles', { code: 'MRIDER_' + x, name: 'Rider', permissions: ['DELIVERY_RIDE'] }, t)).data;
const un = '__pfx__.mrider.' + x.toLowerCase(); const p1 = pw(); const p2 = pw();
const ru = await c('POST', '/iam/users', { username: un, email: un + '@example.com', fullName: 'Mileage rider', roleIds: [Number(riderRole.id)], password: p1 }, t);
await c('POST', '/auth/initial-password', { username: un, currentPassword: ru.data?.temporaryPassword || p1, newPassword: p2 });
const rt = (await c('POST', '/auth/login', { username: un, password: p2 })).data?.accessToken;
ck('setup', store?.id && egg?.id && rt, [store?.id, egg?.id, !!rt].join(','));

let r = await c('GET', '/order/mileage-policy', null, t);
ck('default policy (not saved): on, 1 %, 12 months, 1,000', r.s === 200 && r.data.saved === false && r.data.active && Number(r.data.earnRate) === 1 && r.data.minRedeem === 1000, r.s + ' ' + JSON.stringify(r.data));
r = await c('PUT', '/order/mileage-policy', { active: true, earnRate: 10, expiryMonths: 12, minRedeem: 1005 }, t);
ck('minRedeem not a multiple of 10 → MIL_001', r.s === 400 && r.code === 'MIL_001', r.s + ' ' + r.code);
r = await c('PUT', '/order/mileage-policy', { active: true, earnRate: 10, expiryMonths: 12, minRedeem: 1000 }, t);
ck('policy saved: 10 %', r.s === 200 && r.data.saved && Number(r.data.earnRate) === 10, r.s + ' ' + r.code);

const join = (await c('POST', `/shop/${slug}/me`, { termsAgreed: true, privacyAgreed: true, termsVersion: '2026-11', privacyVersion: '2026-11' }, ct)).data;
const customerId = join?.customerId;
const home = (await c('POST', `/shop/${slug}/me/addresses`, { label: 'Home', recipientName: 'E2E Customer', phone: '010-1234-5678', postcode: '08502', address1: '1 Test-ro', lat: 37.40, lon: 126.80 }, ct)).data;
const fill = () => c('PUT', `/shop/${slug}/stores/${store.id}/cart/items/${egg.id}`, { quantity: 4 }, ct);   // 20,000 goods
const place = (usePoints) => c('POST', `/shop/${slug}/stores/${store.id}/orders`, { addressId: Number(home.id), substitution: 'ALLOW', usePoints }, ct);
const balance = async () => (await c('GET', `/shop/${slug}/mileage`, null, ct)).data?.balance;
r = await c('GET', `/shop/${slug}/mileage`, null, ct); ck('a new member has 0 points', r.s === 200 && r.data.balance === 0, r.s + ' ' + r.code);

await fill();
const o1 = (await place()).data;
await c('POST', `/shop/${slug}/orders/${o1.orderNo}/pay`, { outcome: 'APPROVED' }, ct);
const d1 = await waitFor(async () => ((await c('GET', `/delivery/deliveries?storeId=${store.id}`, null, t)).data?.content || []).find(z => z.orderId === o1.id), v => !!v);
await c('POST', `/delivery/deliveries/${d1.id}/assign`, { riderUserId: Number(ru.data?.user?.id) }, t);
await c('POST', `/delivery/my-deliveries/${d1.id}/pick-up`, null, rt);
ck('paid but not delivered: still 0 points', (await balance()) === 0, 'earned too early');
await c('POST', `/delivery/my-deliveries/${d1.id}/deliver`, null, rt);
const b1 = await waitFor(balance, v => v === 2000);
ck('delivered → 10 % of 20,000 = 2,000 points', b1 === 2000, String(b1));
r = await c('GET', `/shop/${slug}/mileage`, null, ct);
ck('statement shows the EARN with its order', r.data?.entries?.[0]?.type === 'EARN' && r.data.entries[0].points === 2000 && r.data.entries[0].orderNo === o1.orderNo, JSON.stringify(r.data?.entries?.[0]));

await fill();
const cart = (n) => c('GET', `/shop/${slug}/stores/${store.id}/cart?usePoints=${n}`, null, ct);
r = await cart(500); ck('preview 500 points (under the minimum) → MIL_004', r.data?.pointsError === 'MIL_004' && Number(r.data.pointsUsed) === 0, String(r.data?.pointsError));
r = await cart(1005); ck('preview 1,005 points (not a step of 10) → MIL_004', r.data?.pointsError === 'MIL_004', String(r.data?.pointsError));
r = await cart(5000); ck('preview 5,000 points (over the balance) → MIL_005', r.data?.pointsError === 'MIL_005', String(r.data?.pointsError));
r = await cart(1000); ck('preview 1,000 points: total 22,000', r.data?.pointsError === null && Number(r.data.pointsUsed) === 1000 && Number(r.data.total) === 22000, JSON.stringify(r.data && [r.data.pointsUsed, r.data.total]));
r = await place(5000); ck('placing with more than the balance → MIL_005', r.s === 400 && r.code === 'MIL_005', r.s + ' ' + r.code);
r = await place(1000); const o2 = r.data;
ck('order with 1,000 points: total 22,000', r.s === 200 && o2.pointsUsed === 1000 && Number(o2.total) === 22000, r.s + ' ' + r.code + ' ' + JSON.stringify(o2 && [o2.pointsUsed, o2.total]));
ck('balance reserved: 1,000 left', (await balance()) === 1000, 'balance');
await c('POST', `/shop/${slug}/orders/${o2.orderNo}/cancel`, null, ct);
ck('cancel gives the points back: 2,000', (await balance()) === 2000, 'balance');
r = await place(2000); const o3 = r.data; ck('order with all 2,000 points', r.s === 200 && o3.pointsUsed === 2000 && Number(o3.total) === 21000, r.s + ' ' + r.code);
await c('POST', `/shop/${slug}/orders/${o3.orderNo}/pay`, { outcome: 'APPROVED' }, ct);
ck('balance 0 after paying with the points', (await balance()) === 0, 'balance');

let je;
for (let i = 0; i < 20 && !je; i++) { je = ((await c('GET', '/accounting/journal-entries?sourceType=SHOP_ORDER&size=100', null, t)).data?.content || []).find(e => e.sourceId === o3.id); if (!je) await sleep(1000); }
const lines = je ? (await c('GET', '/accounting/journal-entries/' + je.id, null, t)).data?.lines || [] : [];
const cr = code => lines.filter(l => l.accountCode === code).reduce((s, l) => s + Number(l.credit || 0), 0);
// goods 20,000 − 2,000 points = 18,000 incl. VAT → 16,364 + 1,636
ck('journal: 5111 net of the points', cr('5111') === 16364, String(cr('5111')));

r = await c('POST', `/order/mileage/customers/${customerId}/adjust`, { points: 500, reason: 'Goodwill' }, t);
ck('Back Office adds 500 points', r.s === 200 && r.data.balance === 500 && r.data.entries[0].type === 'ADJUST', r.s + ' ' + r.code + ' ' + r.data?.balance);
r = await c('POST', `/order/mileage/customers/${customerId}/adjust`, { points: -10000, reason: 'Too much' }, t);
ck('taking more than the balance → MIL_005', r.s === 400 && r.code === 'MIL_005', r.s + ' ' + r.code);
r = await c('GET', `/order/mileage/customers/${customerId}`, null, t);
const types = (r.data?.entries || []).map(e => e.type);
ck('Back Office statement: ADJUST, USE, RELEASE, USE, EARN', r.s === 200 && ['ADJUST', 'USE', 'RELEASE', 'EARN'].every(z => types.includes(z)), JSON.stringify(types));
r = await c('GET', `/order/mileage/customers/${customerId}`, null, B.t); ck('another company → MIL_007 404', r.s === 404 && r.code === 'MIL_007', r.s + ' ' + r.code);

await c('PUT', '/order/mileage-policy', { active: false, earnRate: 10, expiryMonths: 12, minRedeem: 1000 }, t);
await fill();
r = await place(1000); ck('policy off → MIL_003', r.s === 400 && r.code === 'MIL_003', r.s + ' ' + r.code);
const emp = (await c('GET', '/iam/roles', null, t)).data.find(z => z.code === 'EMPLOYEE'); const eun = '__pfx__.mlemp.' + x.toLowerCase(); const e1 = pw(); const e2 = pw();
const eu = await c('POST', '/iam/users', { username: eun, email: eun + '@example.com', fullName: 'Mileage Employee', roleIds: [Number(emp.id)], password: e1 }, t);
await c('POST', '/auth/initial-password', { username: eun, currentPassword: eu.data?.temporaryPassword || e1, newPassword: e2 });
const et = (await c('POST', '/auth/login', { username: eun, password: e2 })).data?.accessToken;
r = await c('GET', '/order/mileage-policy', null, et); ck('employee without MILEAGE_VIEW → 403', r.s === 403, r.s + ' ' + r.code);
await c('DELETE', `/shop/${slug}/me`, null, ct);
console.log(`\n${n - f}/${n} passed`);

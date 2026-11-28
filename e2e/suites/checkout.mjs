// checkout (checkout spec): cart → PENDING_PAYMENT order (snapshot) → sandbox payment → stock out once → PAID;
// refusals, cancel rules, Back Office view (ORDER_VIEW, masked phone). Needs `node e2e/customer-client.mjs`.
const KC_URL = (process.env.E2E_KEYCLOAK || 'http://localhost:18180').replace(/\/$/, '');
const [cuUser, cuPw] = fs.readFileSync(path.join(os.homedir(), '.terp-e2e', 'customer-e2e.txt'), 'utf8').trim().split('\n');
const tok = await fetch(KC_URL + '/realms/erp-customer/protocol/openid-connect/token', { method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({ grant_type: 'password', client_id: 'erp-shop-e2e', username: cuUser, password: cuPw }) });
const ct = (await tok.json()).access_token;
ck('customer token', tok.status === 200 && !!ct, String(tok.status));

const slug = 'co-a-' + x.toLowerCase();
await c('PUT', '/store/shop-setting', { slug, enabled: true }, t);
const unit = (await c('POST', '/inventory/units', { name: 'Pack' }, t)).data;
const cat = (await c('POST', '/inventory/categories', { name: 'Checkout ' + x }, t)).data;
const wh = (await c('POST', '/inventory/warehouses', { name: 'Checkout WH ' + x, address: '1 Test Street', city: 'Test City', zipCode: '00000', active: true }, t)).data;
const store = (await c('POST', '/store/stores', { code: 'CO' + x.slice(0, 4), name: 'Checkout Store ' + x, warehouseId: Number(wh.id), lat: 37.40, lon: 126.80,
  orderStart: '00:00', orderEnd: '23:59', deliveryFee: 3000, freeDeliveryThreshold: 30000, minOrderAmount: 20000, firstOrderMinAmount: 10000, bagFee: 200 }, t)).data;
await c('POST', `/store/stores/${store.id}/zones`, { name: 'Checkout zone', area: { type: 'Polygon', coordinates: [[[126.79, 37.39], [126.81, 37.39], [126.81, 37.41], [126.79, 37.41], [126.79, 37.39]]] } }, t);
const mkItem = async (name, qty) => (await c('POST', '/inventory/items', { type: 'PRODUCT', name, sku: '__PFX__-O' + name + '-' + x, categoryId: Number(cat.id), unitId: Number(unit.id), salePrice: 1000, purchasePrice: 500, openingWarehouseId: Number(wh.id), openingQuantity: qty }, t)).data;
const egg = await mkItem('Egg', 20), rare = await mkItem('Rare', 1);
await c('POST', '/inventory/store-items', { warehouseId: Number(wh.id), itemId: Number(egg.id), salePrice: 5000 }, t);
await c('POST', '/inventory/store-items', { warehouseId: Number(wh.id), itemId: Number(rare.id), salePrice: 6000 }, t);
ck('checkout setup', store?.id && egg?.id && rare?.id, [store?.id, egg?.id, rare?.id].join(','));
const stockOf = async (itemId) => Number((await c('GET', `/inventory/stock?itemId=${itemId}&warehouseId=${wh.id}`, null, t)).data?.[0]?.quantity);

let r = await c('POST', `/shop/${slug}/me`, { termsAgreed: true, privacyAgreed: true, termsVersion: '2026-11', privacyVersion: '2026-11' }, ct);
ck('join', r.s === 200, r.s + ' ' + r.code);
const home = (await c('POST', `/shop/${slug}/me/addresses`, { label: 'Home', recipientName: 'E2E Customer', phone: '010-1234-5678', postcode: '08502', address1: '1 Test-ro', lat: 37.40, lon: 126.80 }, ct)).data;
const far = (await c('POST', `/shop/${slug}/me/addresses`, { label: 'Far', recipientName: 'E2E Customer', phone: '01012345678', postcode: '08502', address1: '2 Far-ro', lat: 35.10, lon: 129.00 }, ct)).data;
const place = (body) => c('POST', `/shop/${slug}/stores/${store.id}/orders`, { substitution: 'ALLOW', ...body }, ct);
const cartPut = (itemId, quantity) => c('PUT', `/shop/${slug}/stores/${store.id}/cart/items/${itemId}`, { quantity }, ct);

r = await place({ addressId: Number(home.id) }); ck('empty cart → ORD_008', r.s === 400 && r.code === 'ORD_008', r.s + ' ' + r.code);
await cartPut(egg.id, 1);
r = await place({ addressId: Number(home.id) }); ck('under the minimum → ORD_010', r.s === 400 && r.code === 'ORD_010', r.s + ' ' + r.code);
await cartPut(egg.id, 2);
r = await place({ addressId: Number(far.id) }); ck('address not served by the store → ORD_013', r.s === 400 && r.code === 'ORD_013', r.s + ' ' + r.code);
r = await place({ addressId: 999999999 }); ck('address not mine → ORD_012', r.s === 404 && r.code === 'ORD_012', r.s + ' ' + r.code);
r = await place({ addressId: Number(home.id), note: 'Ring twice' }); const o1 = r.data;
ck('order placed: pending, snapshot', r.s === 200 && o1.status === 'PENDING_PAYMENT' && /^HE-\d{8}-\d{6}$/.test(o1.orderNo) && Number(o1.subtotal) === 10000
  && Number(o1.deliveryFee) === 3000 && Number(o1.total) === 13000 && o1.phone === '01012345678' && o1.lines.length === 1, r.s + ' ' + r.code + ' ' + JSON.stringify(o1 && { n: o1.orderNo, t: o1.total }));
r = await place({ addressId: Number(home.id) }); ck('second pending order → ORD_014', r.s === 400 && r.code === 'ORD_014', r.s + ' ' + r.code);
const pay = (no, outcome) => c('POST', `/shop/${slug}/orders/${no}/pay`, { outcome }, ct);
r = await pay(o1.orderNo, 'DECLINED'); ck('declined → PAYMENT_FAILED', r.s === 200 && r.data.status === 'PAYMENT_FAILED' && r.data.failureReason === 'DECLINED', r.s + ' ' + r.code);
r = await c('POST', `/shop/${slug}/orders/${o1.orderNo}/cancel`, null, ct); ck('cancel a failed order', r.s === 200 && r.data.status === 'CANCELLED', r.s + ' ' + r.code);

const before = await stockOf(egg.id);
r = await place({ addressId: Number(home.id) }); const o2 = r.data; ck('place again', r.s === 200, r.s + ' ' + r.code);
r = await pay(o2.orderNo, 'APPROVED');
ck('approved → PAID', r.s === 200 && r.data.status === 'PAID' && !!r.data.paidAt, r.s + ' ' + r.code + ' ' + r.data?.status);
ck('stock moved out once (−2)', (await stockOf(egg.id)) === before - 2, before + ' → ' + (await stockOf(egg.id)));
r = await c('GET', `/shop/${slug}/stores/${store.id}/cart`, null, ct); ck('bought lines left the cart', r.s === 200 && r.data.lines.length === 0, r.s + ' ' + r.data?.lines?.length);
r = await pay(o2.orderNo, 'APPROVED'); ck('pay again → ORD_016', r.s === 400 && r.code === 'ORD_016', r.s + ' ' + r.code);
ck('stock still moved once', (await stockOf(egg.id)) === before - 2, String(await stockOf(egg.id)));
r = await c('POST', `/shop/${slug}/orders/${o2.orderNo}/cancel`, null, ct); ck('paid order cannot be cancelled → ORD_017', r.s === 400 && r.code === 'ORD_017', r.s + ' ' + r.code);

await cartPut(rare.id, 2);
r = await place({ addressId: Number(home.id) }); const o3 = r.data; ck('order more than in stock', r.s === 200, r.s + ' ' + r.code);
r = await pay(o3.orderNo, 'APPROVED'); ck('out of stock → ORD_015', r.s === 400 && r.code === 'ORD_015', r.s + ' ' + r.code);
r = await c('GET', `/shop/${slug}/orders/${o3.orderNo}`, null, ct); ck('order left PAYMENT_FAILED / OUT_OF_STOCK', r.s === 200 && r.data.status === 'PAYMENT_FAILED' && r.data.failureReason === 'OUT_OF_STOCK', r.s + ' ' + JSON.stringify(r.data && [r.data.status, r.data.failureReason]));
ck('no stock taken for the failed order', (await stockOf(rare.id)) === 1, String(await stockOf(rare.id)));
r = await c('GET', `/shop/${slug}/orders`, null, ct); ck('my orders, newest first', r.s === 200 && r.data.content[0].orderNo === o3.orderNo && r.data.totalElements === 3, r.s + ' ' + r.data?.totalElements);

r = await c('GET', `/order/orders?storeId=${store.id}&status=PAID`, null, t);
ck('Back Office list (ORDER_VIEW), phone masked', r.s === 200 && r.data.content.length === 1 && r.data.content[0].orderNo === o2.orderNo && r.data.content[0].phone === '010-****-5678', r.s + ' ' + r.code + ' ' + r.data?.content?.[0]?.phone);
r = await c('GET', '/order/orders/' + o2.id, null, B.t); ck('another company → ORD_018 404', r.s === 404 && r.code === 'ORD_018', r.s + ' ' + r.code);
const roles = (await c('GET', '/iam/roles', null, t)).data; const emp = roles.find(z => z.code === 'EMPLOYEE'); const ep = pw(); const eun = '__pfx__.emp.' + x.toLowerCase();
const eu = await c('POST', '/iam/users', { username: eun, email: eun + '@example.com', fullName: 'Checkout Employee', roleIds: [Number(emp.id)], password: ep }, t); const ep2 = pw();
await c('POST', '/auth/initial-password', { username: eun, currentPassword: eu.data?.temporaryPassword || ep, newPassword: ep2 });
const et = (await c('POST', '/auth/login', { username: eun, password: ep2 })).data?.accessToken;
r = await c('GET', '/order/orders', null, et); ck('employee without ORDER_VIEW → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', `/shop/${slug}/orders`, null, t); ck('staff token on customer orders → 401', r.s === 401, String(r.s));
await c('POST', `/shop/${slug}/orders/${o3.orderNo}/cancel`, null, ct);
await c('DELETE', `/shop/${slug}/me`, null, ct);
console.log(`\n${n - f}/${n} passed`);

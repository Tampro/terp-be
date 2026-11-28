// delivery (delivery-service-core spec): a paid order becomes a READY delivery (order.paid, outbox) → Back Office
// assigns a rider (DELIVERY_RIDE) → the rider picks it up and delivers it. Needs `node e2e/customer-client.mjs`.
const KC_URL = (process.env.E2E_KEYCLOAK || 'http://localhost:18180').replace(/\/$/, '');
const [cuUser, cuPw] = fs.readFileSync(path.join(os.homedir(), '.terp-e2e', 'customer-e2e.txt'), 'utf8').trim().split('\n');
const tok = await fetch(KC_URL + '/realms/erp-customer/protocol/openid-connect/token', { method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({ grant_type: 'password', client_id: 'erp-shop-e2e', username: cuUser, password: cuPw }) });
const ct = (await tok.json()).access_token;
ck('customer token', tok.status === 200 && !!ct, String(tok.status));

const slug = 'dl-a-' + x.toLowerCase();
await c('PUT', '/store/shop-setting', { slug, enabled: true }, t);
const unit = (await c('POST', '/inventory/units', { name: 'Pack' }, t)).data;
const cat = (await c('POST', '/inventory/categories', { name: 'Delivery ' + x }, t)).data;
const wh = (await c('POST', '/inventory/warehouses', { name: 'Delivery WH ' + x, address: '1 Test Street', city: 'Test City', zipCode: '00000', active: true }, t)).data;
const store = (await c('POST', '/store/stores', { code: 'DL' + x.slice(0, 4), name: 'Delivery Store ' + x, warehouseId: Number(wh.id), lat: 37.40, lon: 126.80,
  orderStart: '00:00', orderEnd: '23:59', deliveryFee: 3000, freeDeliveryThreshold: 30000, minOrderAmount: 10000, firstOrderMinAmount: 10000, bagFee: 200 }, t)).data;
await c('POST', `/store/stores/${store.id}/zones`, { name: 'Delivery zone', area: { type: 'Polygon', coordinates: [[[126.79, 37.39], [126.81, 37.39], [126.81, 37.41], [126.79, 37.41], [126.79, 37.39]]] } }, t);
const egg = (await c('POST', '/inventory/items', { type: 'PRODUCT', name: 'Egg', sku: '__PFX__-DEgg-' + x, categoryId: Number(cat.id), unitId: Number(unit.id), salePrice: 1000, purchasePrice: 500, openingWarehouseId: Number(wh.id), openingQuantity: 20 }, t)).data;
await c('POST', '/inventory/store-items', { warehouseId: Number(wh.id), itemId: Number(egg.id), salePrice: 5000 }, t);
ck('setup', store?.id && egg?.id, [store?.id, egg?.id].join(','));

// staff: a rider (role with DELIVERY_RIDE) and an employee without it
const staff = async (tag, roleIds) => {
  const un = '__pfx__.' + tag + '.' + x.toLowerCase(); const p1 = pw(); const p2 = pw();
  const u = await c('POST', '/iam/users', { username: un, email: un + '@example.com', fullName: 'Delivery ' + tag, roleIds, password: p1 }, t);
  await c('POST', '/auth/initial-password', { username: un, currentPassword: u.data?.temporaryPassword || p1, newPassword: p2 });
  return { id: u.data?.user?.id, token: (await c('POST', '/auth/login', { username: un, password: p2 })).data?.accessToken };
};
const riderRole = (await c('POST', '/iam/roles', { code: 'RIDER_' + x, name: 'Rider', permissions: ['DELIVERY_RIDE'] }, t)).data;
const emp = (await c('GET', '/iam/roles', null, t)).data.find(z => z.code === 'EMPLOYEE');
const rider = await staff('rider', [Number(riderRole.id)]);
const other = await staff('rider2', [Number(riderRole.id)]);
const clerk = await staff('clerk', [Number(emp.id)]);
ck('staff ready', rider.token && other.token && clerk.token, [!!rider.token, !!other.token, !!clerk.token].join(','));

let r = await c('POST', `/shop/${slug}/me`, { termsAgreed: true, privacyAgreed: true, termsVersion: '2026-11', privacyVersion: '2026-11' }, ct);
ck('join', r.s === 200, r.s + ' ' + r.code);
const home = (await c('POST', `/shop/${slug}/me/addresses`, { label: 'Home', recipientName: 'E2E Customer', phone: '010-1234-5678', postcode: '08502', address1: '1 Test-ro', lat: 37.40, lon: 126.80 }, ct)).data;
await c('PUT', `/shop/${slug}/stores/${store.id}/cart/items/${egg.id}`, { quantity: 2 }, ct);
const o = (await c('POST', `/shop/${slug}/stores/${store.id}/orders`, { addressId: Number(home.id), substitution: 'ALLOW' }, ct)).data;
r = await c('POST', `/shop/${slug}/orders/${o.orderNo}/pay`, { outcome: 'APPROVED' }, ct);
ck('order paid', r.s === 200 && r.data.status === 'PAID', r.s + ' ' + r.code);

const waitStatus = async (no, want) => {
  let st;
  for (let i = 0; i < 20; i++) {
    st = (await c('GET', `/shop/${slug}/orders/${no}`, null, ct)).data?.deliveryStatus;
    if (st === want) break;
    await new Promise(res => setTimeout(res, 1000));
  }
  return st;
};
let d;
for (let i = 0; i < 30 && !d; i++) {
  d = ((await c('GET', `/delivery/deliveries?storeId=${store.id}`, null, t)).data?.content || []).find(z => z.orderId === o.id);
  if (!d) await new Promise(res => setTimeout(res, 1000));
}
ck('a READY delivery for the paid order', d?.status === 'READY' && d.orderNo === o.orderNo, JSON.stringify(d && [d.status, d.orderNo]));
ck('customer order shows READY', (await waitStatus(o.orderNo, 'READY')) === 'READY', 'timeout');
r = await c('GET', '/delivery/deliveries/' + d?.id, null, t);
ck('Back Office detail: destination, phone masked', r.s === 200 && r.data.destination.phone === '010-****-5678' && r.data.destination.lines[0].quantity === 2,
  r.s + ' ' + r.code + ' ' + r.data?.destination?.phone);

r = await c('POST', `/delivery/deliveries/${d.id}/assign`, { riderUserId: Number(clerk.id) }, t);
ck('assign to a user without DELIVERY_RIDE → DLV_004', r.s === 400 && r.code === 'DLV_004', r.s + ' ' + r.code);
r = await c('POST', `/delivery/deliveries/${d.id}/assign`, { riderUserId: Number(B.adminUserId || 999999999) }, t);
ck('assign to another company\'s user → DLV_004', r.s === 400 && r.code === 'DLV_004', r.s + ' ' + r.code);
r = await c('POST', `/delivery/deliveries/${d.id}/assign`, { riderUserId: Number(rider.id) }, t);
ck('assigned to the rider', r.s === 200 && r.data.status === 'ASSIGNED' && r.data.riderName === 'Delivery rider', r.s + ' ' + r.code + ' ' + r.data?.riderName);
ck('customer order shows ASSIGNED', (await waitStatus(o.orderNo, 'ASSIGNED')) === 'ASSIGNED', 'timeout');

r = await c('GET', '/delivery/my-deliveries', null, rider.token);
ck('rider sees it in my deliveries', r.s === 200 && r.data.content.some(z => z.id === d.id), r.s + ' ' + r.code);
r = await c('GET', '/delivery/my-deliveries/' + d.id, null, rider.token);
ck('rider detail: plain phone to call', r.s === 200 && r.data.destination.phone === '01012345678', r.s + ' ' + r.data?.destination?.phone);
r = await c('GET', '/delivery/my-deliveries/' + d.id, null, other.token);
ck('another rider → DLV_001 404', r.s === 404 && r.code === 'DLV_001', r.s + ' ' + r.code);
r = await c('POST', `/delivery/my-deliveries/${d.id}/deliver`, null, rider.token);
ck('deliver before pick-up → DLV_005', r.s === 400 && r.code === 'DLV_005', r.s + ' ' + r.code);
r = await c('POST', `/delivery/my-deliveries/${d.id}/pick-up`, null, rider.token);
ck('picked up', r.s === 200 && r.data.status === 'PICKED_UP' && !!r.data.pickedUpAt, r.s + ' ' + r.code);
ck('customer order shows PICKED_UP', (await waitStatus(o.orderNo, 'PICKED_UP')) === 'PICKED_UP', 'timeout');
r = await c('POST', `/delivery/deliveries/${d.id}/cancel`, { reason: 'too late' }, t);
ck('cancel after pick-up → DLV_005', r.s === 400 && r.code === 'DLV_005', r.s + ' ' + r.code);
r = await c('POST', `/delivery/my-deliveries/${d.id}/deliver`, null, rider.token);
ck('delivered', r.s === 200 && r.data.status === 'DELIVERED' && !!r.data.deliveredAt, r.s + ' ' + r.code);
r = await c('GET', `/shop/${slug}/orders/${o.orderNo}`, null, ct);
ck('customer order shows DELIVERED with its time', (await waitStatus(o.orderNo, 'DELIVERED')) === 'DELIVERED', 'timeout');
r = await c('GET', '/order/orders/' + o.id, null, t);
ck('Back Office order shows DELIVERED', r.s === 200 && r.data.deliveryStatus === 'DELIVERED' && !!r.data.deliveryStatusAt, r.s + ' ' + r.data?.deliveryStatus);
r = await c('GET', `/shop/${slug}/orders`, null, ct);
ck('customer list carries the status', r.s === 200 && r.data.content.find(z => z.orderNo === o.orderNo)?.deliveryStatus === 'DELIVERED', String(r.s));

// a second order whose delivery is cancelled before pick-up
await c('PUT', `/shop/${slug}/stores/${store.id}/cart/items/${egg.id}`, { quantity: 2 }, ct);
const o2 = (await c('POST', `/shop/${slug}/stores/${store.id}/orders`, { addressId: Number(home.id), substitution: 'ALLOW' }, ct)).data;
ck('unpaid order has no delivery status', o2?.deliveryStatus === null, String(o2?.deliveryStatus));
await c('POST', `/shop/${slug}/orders/${o2.orderNo}/pay`, { outcome: 'APPROVED' }, ct);
let d2;
for (let i = 0; i < 30 && !d2; i++) {
  d2 = ((await c('GET', `/delivery/deliveries?storeId=${store.id}`, null, t)).data?.content || []).find(z => z.orderId === o2.id);
  if (!d2) await new Promise(res => setTimeout(res, 1000));
}
r = await c('POST', `/delivery/deliveries/${d2?.id}/cancel`, { reason: 'Customer unreachable' }, t);
ck('second delivery cancelled', r.s === 200 && r.data.status === 'CANCELLED', r.s + ' ' + r.code);
ck('customer order shows CANCELLED', (await waitStatus(o2.orderNo, 'CANCELLED')) === 'CANCELLED', 'timeout');

r = await c('GET', '/delivery/deliveries/' + d.id, null, B.t); ck('another company → DLV_001 404', r.s === 404 && r.code === 'DLV_001', r.s + ' ' + r.code);
r = await c('GET', '/delivery/deliveries', null, clerk.token); ck('employee without DELIVERY_VIEW → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', '/delivery/my-deliveries', null, clerk.token); ck('employee without DELIVERY_RIDE → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', '/delivery/deliveries', null, ct); ck('customer token → 401', r.s === 401, String(r.s));
await c('DELETE', `/shop/${slug}/me`, null, ct);
console.log(`\n${n - f}/${n} passed`);

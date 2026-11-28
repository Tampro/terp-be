// customer-notification (customer-delivery-notification spec): the customer is emailed when the order is paid, picked
// up, delivered or its delivery cancelled. The address is snapshotted on the order and read by notification-service
// from order-service (never in the event). Company SMTP points at a host that cannot resolve: an EMAIL row that was
// attempted (PENDING / FAILED on SMTP) proves the send. Needs `node e2e/customer-client.mjs`.
const KC_URL = (process.env.E2E_KEYCLOAK || 'http://localhost:18180').replace(/\/$/, '');
const [cuUser, cuPw] = fs.readFileSync(path.join(os.homedir(), '.terp-e2e', 'customer-e2e.txt'), 'utf8').trim().split('\n');
const tok = await fetch(KC_URL + '/realms/erp-customer/protocol/openid-connect/token', { method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({ grant_type: 'password', client_id: 'erp-shop-e2e', username: cuUser, password: cuPw, scope: 'openid email' }) });
const ct = (await tok.json()).access_token;
ck('customer token', tok.status === 200 && !!ct, String(tok.status));
const claims = JSON.parse(Buffer.from(ct.split('.')[1], 'base64url').toString());
ck('customer token carries an email', !!claims.email, 'no email claim');

const sleep = ms => new Promise(res => setTimeout(res, ms));
const waitFor = async (fn, ok, ms = 45000) => { const t0 = Date.now(); let v; while (Date.now() - t0 < ms) { v = await fn(); if (ok(v)) return v; await sleep(2000); } return v; };
const emails = async (type) => ((await c('GET', '/notifications/deliveries?size=100&channel=EMAIL&eventType=' + type, null, t)).data?.content || []);
const attempted = z => z.attempts > 0 && ['PENDING', 'FAILED'].includes(z.status) && /SMTP|CONNECTION|TIMEOUT/.test(z.lastErrorCode || '');
const show = d => JSON.stringify(d)?.slice(0, 300);

let r = await c('PUT', '/settings/company/email', { enabled: true, host: 'smtp.no-such-host.invalid', port: 587, username: 'test', password: 'fake-smtp-pass', encryption: 'TLS', fromAddress: 'noreply@example.com', fromName: 'Test Co' }, t);
ck('company SMTP set to an unresolvable host', r.s === 200, r.s + ' ' + r.code);

const slug = 'cn-a-' + x.toLowerCase();
await c('PUT', '/store/shop-setting', { slug, enabled: true }, t);
const unit = (await c('POST', '/inventory/units', { name: 'Pack' }, t)).data;
const cat = (await c('POST', '/inventory/categories', { name: 'Notify ' + x }, t)).data;
const wh = (await c('POST', '/inventory/warehouses', { name: 'Notify WH ' + x, address: '1 Test Street', city: 'Test City', zipCode: '00000', active: true }, t)).data;
const store = (await c('POST', '/store/stores', { code: 'CN' + x.slice(0, 4), name: 'Notify Store ' + x, warehouseId: Number(wh.id), lat: 37.40, lon: 126.80,
  orderStart: '00:00', orderEnd: '23:59', deliveryFee: 3000, freeDeliveryThreshold: 30000, minOrderAmount: 10000, firstOrderMinAmount: 10000, bagFee: 200 }, t)).data;
await c('POST', `/store/stores/${store.id}/zones`, { name: 'Notify zone', area: { type: 'Polygon', coordinates: [[[126.79, 37.39], [126.81, 37.39], [126.81, 37.41], [126.79, 37.41], [126.79, 37.39]]] } }, t);
const egg = (await c('POST', '/inventory/items', { type: 'PRODUCT', name: 'Egg', sku: '__PFX__-NEgg-' + x, categoryId: Number(cat.id), unitId: Number(unit.id), salePrice: 1000, purchasePrice: 500, openingWarehouseId: Number(wh.id), openingQuantity: 40 }, t)).data;
await c('POST', '/inventory/store-items', { warehouseId: Number(wh.id), itemId: Number(egg.id), salePrice: 5000 }, t);
const riderRole = (await c('POST', '/iam/roles', { code: 'NRIDER_' + x, name: 'Rider', permissions: ['DELIVERY_RIDE'] }, t)).data;
const un = '__pfx__.nrider.' + x.toLowerCase(); const p1 = pw(); const p2 = pw();
const ru = await c('POST', '/iam/users', { username: un, email: un + '@example.com', fullName: 'Notify rider', roleIds: [Number(riderRole.id)], password: p1 }, t);
await c('POST', '/auth/initial-password', { username: un, currentPassword: ru.data?.temporaryPassword || p1, newPassword: p2 });
const rt = (await c('POST', '/auth/login', { username: un, password: p2 })).data?.accessToken;
ck('setup', store?.id && egg?.id && rt, [store?.id, egg?.id, !!rt].join(','));

r = await c('POST', `/shop/${slug}/me`, { termsAgreed: true, privacyAgreed: true, termsVersion: '2026-11', privacyVersion: '2026-11' }, ct);
ck('join', r.s === 200, r.s + ' ' + r.code);
const home = (await c('POST', `/shop/${slug}/me/addresses`, { label: 'Home', recipientName: 'E2E Customer', phone: '010-1234-5678', postcode: '08502', address1: '1 Test-ro', lat: 37.40, lon: 126.80 }, ct)).data;
const order = async () => {
  await c('PUT', `/shop/${slug}/stores/${store.id}/cart/items/${egg.id}`, { quantity: 2 }, ct);
  const o = (await c('POST', `/shop/${slug}/stores/${store.id}/orders`, { addressId: Number(home.id), substitution: 'ALLOW' }, ct)).data;
  await c('POST', `/shop/${slug}/orders/${o.orderNo}/pay`, { outcome: 'APPROVED' }, ct);
  return o;
};
const deliveryOf = async (o) => waitFor(async () => ((await c('GET', `/delivery/deliveries?storeId=${store.id}`, null, t)).data?.content || []).find(z => z.orderId === o.id), v => !!v);

const o1 = await order();
ck('the order keeps the customer email', o1?.email === claims.email, String(o1?.email));
r = await c('GET', '/order/orders/' + o1.id, null, t);
ck('Back Office sees the email masked', r.s === 200 && /^.\*\*\*@/.test(r.data.email || ''), String(r.data?.email));
const domain = '*@' + claims.email.split('@')[1];
let ds = await waitFor(() => emails('order.paid'), v => v.some(attempted));
ck('paid → ORDER_PAID email attempted to the customer (masked)', ds.length === 1 && attempted(ds[0]) && ds[0].target === domain, show(ds));

const d1 = await deliveryOf(o1);
await c('POST', `/delivery/deliveries/${d1.id}/assign`, { riderUserId: Number(ru.data?.user?.id) }, t);
await sleep(6000);
ds = await emails('delivery.status.changed');
ck('READY and ASSIGNED send no email', ds.length === 0, show(ds));
await c('POST', `/delivery/my-deliveries/${d1.id}/pick-up`, null, rt);
ds = await waitFor(() => emails('delivery.status.changed'), v => v.length >= 1 && v.every(attempted));
ck('picked up → ORDER_PICKED_UP email', ds.length === 1 && attempted(ds[0]) && ds[0].target === domain, show(ds));
await c('POST', `/delivery/my-deliveries/${d1.id}/deliver`, null, rt);
ds = await waitFor(() => emails('delivery.status.changed'), v => v.length >= 2 && v.every(attempted));
ck('delivered → ORDER_DELIVERED email', ds.length === 2 && ds.every(attempted), show(ds));

const o2 = await order();
const d2 = await deliveryOf(o2);
await c('POST', `/delivery/deliveries/${d2.id}/cancel`, { reason: 'Customer unreachable' }, t);
ds = await waitFor(() => emails('delivery.status.changed'), v => v.length >= 3 && v.every(attempted));
ck('delivery cancelled → ORDER_DELIVERY_CANCELLED email', ds.length === 3, show(ds));
ck('no chat or SMS for customer emails', !((await c('GET', '/notifications/deliveries?size=100&eventType=order.paid', null, t)).data?.content || []).some(z => z.channel !== 'EMAIL'), 'other channel');

await c('PUT', '/settings/email-notifications/ORDER_PAID', { enabled: false }, t);
await order();
ds = await waitFor(() => emails('order.paid'), v => v.length >= 3);
ck('ORDER_PAID switched off → SKIPPED EMAIL_DISABLED', ds.some(z => z.status === 'SKIPPED' && z.lastErrorCode === 'EMAIL_DISABLED'), show(ds.map(z => [z.status, z.lastErrorCode])));
await c('DELETE', `/shop/${slug}/me`, null, ct);
console.log(`\n${n - f}/${n} passed`);

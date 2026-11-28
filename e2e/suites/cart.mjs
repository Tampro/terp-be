// cart (cart spec): a member's cart per store, priced live from inventory, with the Express rules
// (delivery free from the threshold, paid bag, minimum order). Needs `node e2e/customer-client.mjs` (e2e.customer).
const KC_URL = (process.env.E2E_KEYCLOAK || 'http://localhost:18180').replace(/\/$/, '');
const [cuUser, cuPw] = fs.readFileSync(path.join(os.homedir(), '.terp-e2e', 'customer-e2e.txt'), 'utf8').trim().split('\n');
const tok = await fetch(KC_URL + '/realms/erp-customer/protocol/openid-connect/token', { method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({ grant_type: 'password', client_id: 'erp-shop-e2e', username: cuUser, password: cuPw }) });
const ct = (await tok.json()).access_token;
ck('customer token', tok.status === 200 && !!ct, String(tok.status));

// company A: shop, store (fees 3,000 / free from 30,000 / first order 10,000 / bag 200), three items on sale, one suspended
const slug = 'cart-a-' + x.toLowerCase();
await c('PUT', '/store/shop-setting', { slug, enabled: true }, t);
const unit = (await c('POST', '/inventory/units', { name: 'Pack' }, t)).data;
const cat = (await c('POST', '/inventory/categories', { name: 'Cart ' + x }, t)).data;
const wh = (await c('POST', '/inventory/warehouses', { name: 'Cart WH ' + x, address: '1 Test Street', city: 'Test City', zipCode: '00000', active: true }, t)).data;
const store = (await c('POST', '/store/stores', { code: 'CT' + x.slice(0, 4), name: 'Cart Store ' + x, warehouseId: Number(wh.id), orderStart: '00:00', orderEnd: '23:59',
  deliveryFee: 3000, freeDeliveryThreshold: 30000, minOrderAmount: 20000, firstOrderMinAmount: 10000, bagFee: 200 }, t)).data;
const mkItem = async (name, qty) => (await c('POST', '/inventory/items', { type: 'PRODUCT', name, sku: '__PFX__-C' + name + '-' + x, categoryId: Number(cat.id), unitId: Number(unit.id), salePrice: 1000, purchasePrice: 500, ...(qty ? { openingWarehouseId: Number(wh.id), openingQuantity: qty } : {}) }, t)).data;
const egg = await mkItem('Egg', 20), milk = await mkItem('Milk', 0), tofu = await mkItem('Tofu', 5), rice = await mkItem('Rice', 5);
await c('POST', '/inventory/store-items', { warehouseId: Number(wh.id), itemId: Number(egg.id), salePrice: 5000, purchaseLimitQty: 5 }, t);
await c('POST', '/inventory/store-items', { warehouseId: Number(wh.id), itemId: Number(milk.id), salePrice: 2500 }, t);
const tofuSi = (await c('POST', '/inventory/store-items', { warehouseId: Number(wh.id), itemId: Number(tofu.id), salePrice: 1500 }, t)).data;
ck('cart setup', store?.id && egg?.id && tofuSi?.id, [store?.id, egg?.id, milk?.id, tofu?.id].join(','));

const cart = (p) => `/shop/${slug}/stores/${store.id}/cart${p || ''}`;
let r = await c('GET', cart(), null, ct); ck('not a member → ORD_001', r.s === 404 && r.code === 'ORD_001', r.s + ' ' + r.code);
r = await c('POST', `/shop/${slug}/me`, { termsAgreed: true, privacyAgreed: true, termsVersion: '2026-11', privacyVersion: '2026-11' }, ct);
ck('join the shop', r.s === 200, r.s + ' ' + r.code);
r = await c('GET', cart(), null, ct); ck('empty cart', r.s === 200 && r.data.lines.length === 0 && Number(r.data.subtotal) === 0 && r.data.minimumReached === false, r.s + ' ' + r.code);

r = await c('PUT', cart('/items/' + egg.id), { quantity: 2 }, ct);
ck('add egg ×2 at the store price', r.s === 200 && r.data.lines.length === 1 && Number(r.data.lines[0].salePrice) === 5000 && Number(r.data.subtotal) === 10000
  && Number(r.data.deliveryFee) === 3000 && r.data.minimumReached === true && Number(r.data.minOrderAmount) === 10000, r.s + ' ' + r.code + ' ' + JSON.stringify(r.data && { s: r.data.subtotal, d: r.data.deliveryFee, m: r.data.minOrderAmount }));
r = await c('PUT', cart('/items/' + egg.id), { quantity: 5 }, ct); ck('quantity replaced, not added', r.s === 200 && r.data.lines[0].quantity === 5 && Number(r.data.subtotal) === 25000, r.s + ' ' + r.data?.subtotal);
r = await c('PUT', cart('/items/' + egg.id), { quantity: 6 }, ct); ck('over the purchase limit → ORD_002', r.s === 400 && r.code === 'ORD_002', r.s + ' ' + r.code);
r = await c('PUT', cart('/items/' + egg.id), { quantity: 0 }, ct); ck('quantity 0 → COMMON_001', r.s === 400 && r.code === 'COMMON_001', r.s + ' ' + r.code);
r = await c('PUT', cart('/items/' + rice.id), { quantity: 1 }, ct); ck('item not on sale here → ORD_003', r.s === 404 && r.code === 'ORD_003', r.s + ' ' + r.code);
r = await c('PUT', cart('/items/' + milk.id), { quantity: 2 }, ct);
ck('sold-out item flagged, still in the cart', r.s === 200 && r.data.lines.find(l => l.itemId === milk.id)?.soldOut === true, r.s + ' ' + r.code);
ck('subtotal 30,000 → free delivery', Number(r.data.subtotal) === 30000 && Number(r.data.deliveryFee) === 0 && Number(r.data.total) === 30000, JSON.stringify({ s: r.data.subtotal, d: r.data.deliveryFee }));
r = await c('PUT', cart('/bag'), { bagRequested: true }, ct); ck('bag adds 200', r.s === 200 && Number(r.data.bagFee) === 200 && Number(r.data.total) === 30200, r.s + ' ' + r.data?.total);
await c('PUT', cart('/items/' + tofu.id), { quantity: 2 }, ct);
await c('PUT', '/inventory/store-items/' + tofuSi.id + '/status', { status: 'SUSPENDED' }, t);
r = await c('GET', cart(), null, ct);
const tofuLine = r.data.lines.find(l => l.itemId === tofu.id);
ck('suspended item: available=false, out of the total', r.s === 200 && tofuLine?.available === false && Number(r.data.subtotal) === 30000, JSON.stringify(tofuLine) + ' ' + r.data?.subtotal);
r = await c('PUT', cart('/items/' + egg.id + '/keep'), { keep: true }, ct); ck('keep a line', r.s === 200 && r.data.lines.find(l => l.itemId === egg.id)?.keep === true, r.s + ' ' + r.code);
r = await c('DELETE', cart('/items/' + milk.id), null, ct); ck('remove a line', r.s === 200 && !r.data.lines.some(l => l.itemId === milk.id), r.s + ' ' + r.code);
r = await c('DELETE', cart('/items/' + milk.id), null, ct); ck('remove again → ORD_006', r.s === 404 && r.code === 'ORD_006', r.s + ' ' + r.code);

const bWh = (await c('POST', '/inventory/warehouses', { name: 'Cart WH B ' + x, address: '1 Test Street', city: 'Test City', zipCode: '00000', active: true }, B.t)).data;
const bStore = (await c('POST', '/store/stores', { code: 'CB' + x.slice(0, 4), name: 'Cart Store B ' + x, warehouseId: Number(bWh.id), orderStart: '00:00', orderEnd: '23:59' }, B.t)).data;
r = await c('GET', `/shop/${slug}/stores/${bStore.id}/cart`, null, ct); ck("company B's store through A's shop → ORD_005", r.s === 404 && r.code === 'ORD_005', r.s + ' ' + r.code);
r = await c('GET', cart(), null, t); ck('staff token → 401', r.s === 401, String(r.s));
r = await c('GET', cart()); ck('no token → 401', r.s === 401, String(r.s));
r = await c('DELETE', cart(), null, ct); ck('clear the cart', r.s === 200 && r.data.lines.length === 0 && r.data.bagRequested === true, r.s + ' ' + r.code);
await c('DELETE', `/shop/${slug}/me`, null, ct);
console.log(`\n${n - f}/${n} passed`);

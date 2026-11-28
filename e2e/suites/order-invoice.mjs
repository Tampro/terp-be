// order-invoice (order-invoice spec): a PAID Express order is journalled once in accounting through the outbox —
// Dr 138 PG clearing / Cr 5111 goods, 5113 fees, 33311 VAT (10% included); Dr 632 / Cr 156 at the purchase price.
// Needs `node e2e/customer-client.mjs`.
const KC_URL = (process.env.E2E_KEYCLOAK || 'http://localhost:18180').replace(/\/$/, '');
const [cuUser, cuPw] = fs.readFileSync(path.join(os.homedir(), '.terp-e2e', 'customer-e2e.txt'), 'utf8').trim().split('\n');
const tok = await fetch(KC_URL + '/realms/erp-customer/protocol/openid-connect/token', { method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({ grant_type: 'password', client_id: 'erp-shop-e2e', username: cuUser, password: cuPw }) });
const ct = (await tok.json()).access_token;
ck('customer token', tok.status === 200 && !!ct, String(tok.status));

const slug = 'oi-a-' + x.toLowerCase();
await c('PUT', '/store/shop-setting', { slug, enabled: true }, t);
const unit = (await c('POST', '/inventory/units', { name: 'Pack' }, t)).data;
const cat = (await c('POST', '/inventory/categories', { name: 'Journal ' + x }, t)).data;
const wh = (await c('POST', '/inventory/warehouses', { name: 'Journal WH ' + x, address: '1 Test Street', city: 'Test City', zipCode: '00000', active: true }, t)).data;
const store = (await c('POST', '/store/stores', { code: 'OI' + x.slice(0, 4), name: 'Journal Store ' + x, warehouseId: Number(wh.id), lat: 37.40, lon: 126.80,
  orderStart: '00:00', orderEnd: '23:59', deliveryFee: 3000, freeDeliveryThreshold: 30000, minOrderAmount: 10000, firstOrderMinAmount: 10000, bagFee: 200 }, t)).data;
await c('POST', `/store/stores/${store.id}/zones`, { name: 'Journal zone', area: { type: 'Polygon', coordinates: [[[126.79, 37.39], [126.81, 37.39], [126.81, 37.41], [126.79, 37.41], [126.79, 37.39]]] } }, t);
const egg = (await c('POST', '/inventory/items', { type: 'PRODUCT', name: 'Egg', sku: '__PFX__-JEgg-' + x, categoryId: Number(cat.id), unitId: Number(unit.id), salePrice: 1000, purchasePrice: 500, openingWarehouseId: Number(wh.id), openingQuantity: 20 }, t)).data;
await c('POST', '/inventory/store-items', { warehouseId: Number(wh.id), itemId: Number(egg.id), salePrice: 5000 }, t);
ck('setup', store?.id && egg?.id, [store?.id, egg?.id].join(','));

let r = await c('POST', `/shop/${slug}/me`, { termsAgreed: true, privacyAgreed: true, termsVersion: '2026-11', privacyVersion: '2026-11' }, ct);
ck('join', r.s === 200, r.s + ' ' + r.code);
const home = (await c('POST', `/shop/${slug}/me/addresses`, { label: 'Home', recipientName: 'E2E Customer', phone: '010-1234-5678', postcode: '08502', address1: '1 Test-ro', lat: 37.40, lon: 126.80 }, ct)).data;
await c('PUT', `/shop/${slug}/stores/${store.id}/cart/items/${egg.id}`, { quantity: 2 }, ct);
r = await c('POST', `/shop/${slug}/stores/${store.id}/orders`, { addressId: Number(home.id), substitution: 'ALLOW' }, ct); const o = r.data;
ck('order placed (10,000 + delivery 3,000)', r.s === 200 && Number(o.total) === 13000, r.s + ' ' + r.code + ' ' + o?.total);
r = await c('GET', '/order/orders/' + o.id, null, t); ck('unpaid order has no journal', r.s === 200 && r.data.journalStatus === null, r.s + ' ' + r.data?.journalStatus);
r = await c('POST', `/shop/${slug}/orders/${o.orderNo}/pay`, { outcome: 'APPROVED' }, ct);
ck('paid', r.s === 200 && r.data.status === 'PAID', r.s + ' ' + r.code);
ck('customer view has no journal fields', r.data.journalStatus === null && r.data.journalEntryNo === null, JSON.stringify([r.data.journalStatus, r.data.journalEntryNo]));

let bo;
for (let i = 0; i < 30; i++) {
  bo = (await c('GET', '/order/orders/' + o.id, null, t)).data;
  if (bo?.journalStatus && bo.journalStatus !== 'PENDING') break;
  await new Promise(res => setTimeout(res, 1000));
}
ck('journal POSTED through the outbox', bo?.journalStatus === 'POSTED' && /^JE-/.test(bo.journalEntryNo || ''), JSON.stringify([bo?.journalStatus, bo?.journalEntryNo, bo?.journalError]));

const list = (await c('GET', '/accounting/journal-entries?sourceType=SHOP_ORDER&size=100', null, t)).data?.content || [];
const mine = list.filter(e => e.sourceId === o.id);
ck('one journal entry for the order', mine.length === 1 && mine[0].number === bo?.journalEntryNo && mine[0].reference === o.orderNo, mine.length + ' ' + mine[0]?.number);
const je = (await c('GET', '/accounting/journal-entries/' + mine[0]?.id, null, t)).data;
const amt = (code, side) => (je?.lines || []).filter(l => l.accountCode === code).reduce((s, l) => s + Number(l[side] || 0), 0);
ck('Dr 138 PG clearing = total 13,000', amt('138', 'debit') === 13000, String(amt('138', 'debit')));
ck('Cr 5111 goods net 9,091', amt('5111', 'credit') === 9091, String(amt('5111', 'credit')));
ck('Cr 5113 delivery net 2,727', amt('5113', 'credit') === 2727, String(amt('5113', 'credit')));
ck('Cr 33311 VAT 909 + 273', amt('33311', 'credit') === 1182, String(amt('33311', 'credit')));
ck('Dr 632 / Cr 156 cost 2 × 500', amt('632', 'debit') === 1000 && amt('156', 'credit') === 1000, amt('632', 'debit') + ' / ' + amt('156', 'credit'));
ck('entry balanced', Number(je?.totalDebit) === Number(je?.totalCredit), je?.totalDebit + ' / ' + je?.totalCredit);

r = await c('POST', `/shop/${slug}/orders/${o.orderNo}/pay`, { outcome: 'APPROVED' }, ct); ck('pay again → ORD_016', r.s === 400 && r.code === 'ORD_016', r.s + ' ' + r.code);
await new Promise(res => setTimeout(res, 3000));
const again = ((await c('GET', '/accounting/journal-entries?sourceType=SHOP_ORDER&size=100', null, t)).data?.content || []).filter(e => e.sourceId === o.id);
ck('still one entry', again.length === 1, String(again.length));
r = await c('POST', '/accounting/journal-postings', { sourceType: 'SHOP_ORDER', sourceId: 999999, date: '2026-11-28', reference: 'X', description: 'X', goodsAmount: 1, validateOnly: true }, t);
ck('SHOP_ORDER refused over REST to staff → 403', r.s === 403, r.s + ' ' + r.code);
await c('DELETE', `/shop/${slug}/me`, null, ct);
console.log(`\n${n - f}/${n} passed`);

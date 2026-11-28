import { execFileSync } from 'child_process';
// member (member-service-core spec): a customer of realm erp-customer joins a shop; customer tokens only on
// /api/v1/shop/**, staff tokens refused there. Needs `node e2e/customer-client.mjs` (e2e.customer).
const KC_URL = (process.env.E2E_KEYCLOAK || 'http://localhost:18180').replace(/\/$/, '');
const MEMBER = (process.env.E2E_MEMBER_URL || 'http://localhost:18099').replace(/\/$/, '');
const [cuUser, cuPw] = fs.readFileSync(path.join(os.homedir(), '.terp-e2e', 'customer-e2e.txt'), 'utf8').trim().split('\n');
const tok = await fetch(KC_URL + '/realms/erp-customer/protocol/openid-connect/token', { method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({ grant_type: 'password', client_id: 'erp-shop-e2e', username: cuUser, password: cuPw }) });
const ct = (await tok.json()).access_token;
ck('customer token', tok.status === 200 && !!ct, String(tok.status));

const slugA = 'mem-a-' + x.toLowerCase(), slugB = 'mem-b-' + x.toLowerCase();
ck('shop A open', (await c('PUT', '/store/shop-setting', { slug: slugA, enabled: true }, t)).s === 200, slugA);
ck('shop B open', (await c('PUT', '/store/shop-setting', { slug: slugB, enabled: true }, B.t)).s === 200, slugB);
const me = (slug) => '/shop/' + slug + '/me';
const join = { termsAgreed: true, privacyAgreed: true, marketingAgreed: true, termsVersion: '2026-11', privacyVersion: '2026-11' };

let r = await c('GET', me(slugA), null, ct); ck('not a member yet → MEM_004', r.s === 404 && r.code === 'MEM_004', r.s + ' ' + r.code);
r = await c('POST', me(slugA), { ...join, privacyAgreed: false }, ct); ck('privacy not accepted → MEM_005', r.s === 400 && r.code === 'MEM_005', r.s + ' ' + r.code);
r = await c('POST', me(slugA), { ...join, termsVersion: '' }, ct); ck('blank version → COMMON_001', r.s === 400 && r.code === 'COMMON_001', r.s + ' ' + r.code);
r = await c('POST', me(slugA.toUpperCase()), join, ct);
ck('join shop A (slug any case)', r.s === 200 && r.data.status === 'ACTIVE' && r.data.shopSlug === slugA && r.data.marketingAgreed === true, r.s + ' ' + r.code + ' ' + JSON.stringify(r.data));
r = await c('POST', me(slugA), join, ct); ck('join again → MEM_002', r.s === 400 && r.code === 'MEM_002', r.s + ' ' + r.code);
r = await c('GET', me(slugA), null, ct); ck('my membership of A', r.s === 200 && r.data.status === 'ACTIVE', r.s + ' ' + r.code);
r = await c('GET', me(slugB), null, ct); ck('shop B is another membership → MEM_004', r.s === 404 && r.code === 'MEM_004', r.s + ' ' + r.code);
r = await c('GET', me('nope-' + x.toLowerCase()), null, ct); ck('unknown shop → MEM_001', r.s === 404 && r.code === 'MEM_001', r.s + ' ' + r.code);
await c('PUT', '/store/shop-setting', { slug: slugB, enabled: false }, B.t);
r = await c('POST', me(slugB), join, ct); ck('disabled shop → MEM_001', r.s === 404 && r.code === 'MEM_001', r.s + ' ' + r.code);

r = await c('GET', me(slugA), null, t); ck('staff token on /shop/** → 401 (gateway)', r.s === 401, String(r.s));
let d = await fetch(MEMBER + '/api/v1' + me(slugA), { headers: { Authorization: 'Bearer ' + t } });
ck('staff token on member-service directly → 401', d.status === 401, String(d.status));
d = await fetch(MEMBER + '/api/v1' + me(slugA), { headers: { Authorization: 'Bearer ' + ct } });
ck('customer token on member-service directly → 200', d.status === 200, String(d.status));
r = await c('GET', me(slugA)); ck('no token → 401', r.s === 401, String(r.s));
r = await c('GET', '/store/stores', null, ct); ck('customer token on a staff API → 401', r.s === 401, String(r.s));

r = await c('DELETE', me(slugA), null, ct); ck('leave shop A', r.s === 200, r.s + ' ' + r.code);
r = await c('GET', me(slugA), null, ct); ck('after leaving → MEM_004', r.s === 404 && r.code === 'MEM_004', r.s + ' ' + r.code);
r = await c('POST', me(slugA), join, ct); ck('re-join within 5 days → MEM_003', r.s === 400 && r.code === 'MEM_003', r.s + ' ' + r.code);
// ---- delivery addresses (member-addresses spec)
// Addresses on company B's shop: the customer left company A's shop in this run (5-day rule blocks re-joining it).
const slugC = slugB;
const coB = await c('PUT', '/store/shop-setting', { slug: slugC, enabled: true }, B.t);
ck('shop B open again', coB.s === 200, String(coB.s));
// a store of company B with a zone around 37.40 / 126.80 so servingStore resolves
const whC = (await c('POST', '/inventory/warehouses', { name: 'Addr WH ' + x, address: '1 Test Street', city: 'Test City', zipCode: '00000', active: true }, B.t)).data;
const stC = (await c('POST', '/store/stores', { code: 'AD' + x.slice(0, 4), name: 'Addr Store ' + x, warehouseId: Number(whC.id), lat: 37.40, lon: 126.80, orderStart: '00:00', orderEnd: '23:59' }, B.t)).data;
await c('POST', `/store/stores/${stC.id}/zones`, { name: 'Addr zone', area: { type: 'Polygon', coordinates: [[[126.79, 37.39], [126.81, 37.39], [126.81, 37.41], [126.79, 37.41], [126.79, 37.39]]] } }, B.t);
r = await c('POST', me(slugC), join, ct);
ck('join shop B', r.s === 200, r.s + ' ' + r.code);
const addr = (p) => me(slugC) + '/addresses' + (p || '');
const home = { label: 'Home', recipientName: 'E2E Customer', phone: '010-1234-5678', postcode: '08502', address1: '1 Test-ro', lat: 37.40, lon: 126.80, deliveryNote: 'Door' };
r = await c('POST', addr(), home, ct); const a1 = r.data;
ck('first address is default, store serving it', r.s === 200 && a1.isDefault === true && a1.phone === '01012345678' && a1.servingStore?.id === stC.id, r.s + ' ' + r.code + ' ' + JSON.stringify(a1?.servingStore));
r = await c('POST', addr(), { ...home, label: 'Far', lat: 35.10, lon: 129.00 }, ct); const a2 = r.data;
ck('address outside every zone: servingStore null, not default', r.s === 200 && a2.servingStore === null && a2.isDefault === false, r.s + ' ' + JSON.stringify(a2?.servingStore));
r = await c('PUT', addr('/' + a2.id + '/default'), null, ct); ck('set default', r.s === 200 && r.data.isDefault === true, r.s + ' ' + r.code);
r = await c('GET', addr(), null, ct); ck('list: default first', r.s === 200 && r.data[0].id === a2.id && r.data.length === 2 && r.data[1].isDefault === false, r.s + ' ' + JSON.stringify(r.data?.map(z => [z.id, z.isDefault])));
r = await c('PUT', addr('/' + a1.id), { ...home, label: 'Home 2', phone: '01099990000' }, ct); ck('PUT full replacement', r.s === 200 && r.data.label === 'Home 2' && r.data.phone === '01099990000' && r.data.deliveryNote === 'Door', r.s + ' ' + r.code);
for (const [label, bad] of [['phone 12-34', { phone: '12-34' }], ['postcode 123', { postcode: '123' }], ['lat 91', { lat: 91 }]]) {
  r = await c('POST', addr(), { ...home, ...bad }, ct); ck(`${label} → COMMON_001`, r.s === 400 && r.code === 'COMMON_001', r.s + ' ' + r.code);
}
for (let i = 0; i < 8; i++) await c('POST', addr(), { ...home, label: 'Fill ' + i }, ct);
r = await c('POST', addr(), { ...home, label: 'Eleventh' }, ct); ck('11th address → MEM_008', r.s === 400 && r.code === 'MEM_008', r.s + ' ' + r.code);
r = await c('DELETE', addr('/999999999'), null, ct); ck('unknown address id → MEM_007', r.s === 404 && r.code === 'MEM_007', r.s + ' ' + r.code);
r = await c('GET', me(slugA) + '/addresses', null, ct); ck('addresses of a shop I left → MEM_004', r.s === 404 && r.code === 'MEM_004', r.s + ' ' + r.code);
r = await c('GET', addr(), null, t); ck('staff token on addresses → 401', r.s === 401, String(r.s));
const sql = (q) => execFileSync('docker', ['exec', 'erp-mysql-1', 'sh', '-c', `mysql -N -u"$MEMBER_DB_USERNAME" -p"$MEMBER_DB_PASSWORD" erp_member -e "${q}" 2>/dev/null`]).toString().trim();
ck('phone never stored in clear', sql("SELECT COUNT(*) FROM customer_address WHERE phone_enc LIKE '%0101234%' OR phone_enc LIKE '%0109999%'") === '0', 'count of plain phones');
const before = Number(sql(`SELECT COUNT(*) FROM customer_address a JOIN customer c ON c.id = a.customer_id WHERE c.company_id = ${B.id}`));
r = await c('DELETE', me(slugC), null, ct); ck('leave shop B', r.s === 200, r.s + ' ' + r.code);
const after = Number(sql(`SELECT COUNT(*) FROM customer_address WHERE company_id = ${B.id}`));
ck('leaving deletes the addresses', before >= 10 && after === 0, before + ' → ' + after);
const queues = execFileSync('docker', ['exec', 'erp-rabbitmq-1', 'rabbitmqctl', 'list_queues', 'name', '-q']).toString();
ck('retention queue erp.member.retention declared', /^erp\.member\.retention$/m.test(queues) && /^erp\.member\.retention\.dlq$/m.test(queues), 'queues');
console.log(`\n${n - f}/${n} passed`);

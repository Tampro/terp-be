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
console.log(`\n${n - f}/${n} passed`);

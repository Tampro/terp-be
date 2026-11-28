// customer realm (customer-realm spec, ADR-001): realm erp-customer exists with the decided settings, and a customer
// token is refused by every ERP API (gateway and service), while the public storefront needs no token.
// Standalone: no ERP company. Needs `node e2e/customer-client.mjs` first (client erp-shop-e2e + e2e.customer).
import fs from 'fs'; import os from 'os'; import path from 'path'; import { execFileSync } from 'child_process';

const G = (process.env.E2E_GATEWAY || 'http://localhost:18080') + '/api/v1';
const KC_URL = (process.env.E2E_KEYCLOAK || 'http://localhost:18180').replace(/\/$/, '');
const STORE = (process.env.E2E_STORE_URL || 'http://localhost:18098').replace(/\/$/, '');
const KC = process.env.E2E_KEYCLOAK_CONTAINER || 'erp-keycloak-1';
const ISSUER = KC_URL + '/realms/erp-customer';
let n = 0, f = 0;
const ck = (name, ok, info) => { n++; if (!ok) f++; console.log((ok ? 'PASS ' : 'FAIL ') + name + '  — ' + info); };

const [user, pw] = fs.readFileSync(path.join(os.homedir(), '.terp-e2e', 'customer-e2e.txt'), 'utf8').trim().split('\n');

let r = await fetch(ISSUER + '/.well-known/openid-configuration');
let j = await r.json().catch(() => ({}));
ck('realm erp-customer discovery, issuer', r.status === 200 && j.issuer === ISSUER, r.status + ' ' + j.issuer);

r = await fetch(ISSUER + '/protocol/openid-connect/token', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({ grant_type: 'password', client_id: 'erp-shop-e2e', username: user, password: pw }) });
j = await r.json().catch(() => ({}));
const token = j.access_token;
const claims = token ? JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString()) : {};
ck('customer token issued by erp-customer', r.status === 200 && claims.iss === ISSUER && claims.azp === 'erp-shop-e2e', r.status + ' ' + claims.iss);
ck('access token lives 5 min', claims.exp - claims.iat === 300, String(claims.exp - claims.iat));

for (const p of ['/store/stores', '/me/authorities', '/iam/users']) {
  r = await fetch(G + p, { headers: { Authorization: 'Bearer ' + token } });
  ck(`gateway refuses a customer token on ${p}`, r.status === 401, String(r.status));
}
r = await fetch(STORE + '/api/v1/store/stores', { headers: { Authorization: 'Bearer ' + token } });
ck('store-service itself refuses a customer token', r.status === 401, String(r.status));
r = await fetch(G + '/public/shop/nope-' + Date.now().toString(36) + '/categories');
j = await r.json().catch(() => ({}));
ck('public storefront reached without a token', r.status === 404 && j.code === 'STO_016', r.status + ' ' + j.code);

const kc = (args) => JSON.parse(execFileSync('docker', ['exec', KC, 'sh', '-c',
  `K=/opt/keycloak/bin/kcadm.sh; $K config credentials --server http://localhost:8080 --realm master --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null 2>&1 && $K ${args}`]).toString());
const realm = kc('get realms/erp-customer');
ck('realm settings as decided', realm.registrationAllowed === true && realm.loginWithEmailAllowed === true && realm.verifyEmail === false
  && realm.bruteForceProtected === true && realm.accessTokenLifespan === 300 && realm.ssoSessionIdleTimeout === 2592000
  && realm.revokeRefreshToken === true && /length\(8\)/.test(realm.passwordPolicy || ''),
  JSON.stringify({ reg: realm.registrationAllowed, bf: realm.bruteForceProtected, at: realm.accessTokenLifespan, rr: realm.revokeRefreshToken }));
// Keycloak's own clients (admin-cli, account, …) are in every realm; only the ones this repo adds are checked here.
const BUILT_IN = ['account', 'account-console', 'broker', 'realm-management', 'security-admin-console']; // admin-cli is checked: the realm JSON turns its direct grant off
const clients = kc('get clients -r erp-customer').filter(c => !BUILT_IN.includes(c.clientId));
const byId = Object.fromEntries(clients.map(c => [c.clientId, c]));
const pkce = (c) => c?.publicClient === true && c?.directAccessGrantsEnabled === false && c?.attributes?.['pkce.code.challenge.method'] === 'S256';
ck('shop web / app clients: public, PKCE S256, no direct grant', pkce(byId['erp-shop-web']) && pkce(byId['erp-shop-app']), JSON.stringify(['erp-shop-web', 'erp-shop-app'].map(k => byId[k]?.attributes?.['pkce.code.challenge.method'])));
ck('only the E2E client allows direct grant (admin-cli included)', clients.filter(c => c.directAccessGrantsEnabled).map(c => c.clientId).join() === 'erp-shop-e2e', clients.filter(c => c.directAccessGrantsEnabled).map(c => c.clientId).join());
r = await fetch(ISSUER + '/protocol/openid-connect/token', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({ grant_type: 'password', client_id: 'admin-cli', username: user, password: pw }) });
ck('password grant through admin-cli refused (no PKCE bypass)', r.status === 400 || r.status === 401, String(r.status));
const profile = kc('get users/profile -r erp-customer');
const req = (name) => !!profile.attributes.find(a => a.name === name)?.required;
ck('first / last name optional for customers', !req('firstName') && !req('lastName') && req('email'), JSON.stringify({ first: req('firstName'), last: req('lastName'), email: req('email') }));
console.log(`\n${n - f}/${n} passed`);

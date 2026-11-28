#!/usr/bin/env node
// E2E only (local stack, CI): creates in realm erp-customer a direct-grant client `erp-shop-e2e` and a test customer
// `e2e.customer`, through kcadm inside the Keycloak container. The realm JSON never holds this client: a real environment
// only has the PKCE clients. The password is random, written to ~/.terp-e2e/customer-e2e.txt (mode 600) and never printed.
// Re-runnable: keeps an existing client and user; resets the password only when the file is missing.
//   E2E_KEYCLOAK_CONTAINER default erp-keycloak-1
import { execFileSync } from 'child_process'; import fs from 'fs'; import crypto from 'crypto'; import os from 'os'; import path from 'path';

const KC = process.env.E2E_KEYCLOAK_CONTAINER || 'erp-keycloak-1';
const OUT = path.join(os.homedir(), '.terp-e2e', 'customer-e2e.txt');
const USER = 'e2e.customer';

const kc = (script, env = {}) => execFileSync('docker', ['exec', ...Object.entries(env).flatMap(([k, v]) => ['-e', `${k}=${v}`]), KC, 'sh', '-c', `
K=/opt/keycloak/bin/kcadm.sh
ok=0; for _ in 1 2 3 4 5 6 7 8 9 10; do
  $K config credentials --server http://localhost:8080 --realm master --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null 2>&1 && { ok=1; break; }; sleep 3
done
[ "$ok" = 1 ] || { echo "kcadm login failed" >&2; exit 1; }
${script}`], { stdio: ['ignore', 'pipe', 'inherit'] }).toString().trim();

const client = kc(`
if [ -n "$($K get clients -r erp-customer -q clientId=erp-shop-e2e --fields id --format csv --noquotes)" ]; then echo exists; else
  $K create clients -r erp-customer -s clientId=erp-shop-e2e -s "name=E2E only (direct grant)" -s enabled=true -s publicClient=true \
    -s standardFlowEnabled=false -s directAccessGrantsEnabled=true -s implicitFlowEnabled=false -s serviceAccountsEnabled=false >/dev/null && echo created
fi`);
console.log('client erp-shop-e2e:', client);

const user = kc(`
if [ -n "$($K get users -r erp-customer -q username=${USER} -q exact=true --fields id --format csv --noquotes)" ]; then echo exists; else
  $K create users -r erp-customer -s username=${USER} -s email=${USER}@example.com -s firstName=E2E -s lastName=Customer \
    -s enabled=true -s emailVerified=true >/dev/null && echo created
fi`);
console.log('user ' + USER + ':', user);

if (user === 'created' || !fs.existsSync(OUT)) {
  const pw = 'Cu-' + crypto.randomBytes(9).toString('base64url') + '9a';
  kc(`$K set-password -r erp-customer --username ${USER} --new-password "$PW" >/dev/null && echo set`, { PW: pw });
  fs.mkdirSync(path.dirname(OUT), { recursive: true });
  fs.writeFileSync(OUT, `${USER}\n${pw}\n`, { mode: 0o600 });
  console.log('password saved to', OUT);
} else {
  console.log('password file exists, kept');
}

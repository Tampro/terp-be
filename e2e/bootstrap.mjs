#!/usr/bin/env node
// First-run setup of a fresh ERP stack for the E2E suites (CI, or a local stack after `down -v`): reads the
// temporary superadmin password from the Keycloak container env (never printed), sets a random permanent one and
// saves it to ~/.terp-e2e/erp-superadmin.txt (label, username, password), the file e2e/run.mjs reads.
// Does nothing when the file already exists. Exit code 1 when the stack refuses.
//   E2E_GATEWAY            default http://localhost:18080
//   E2E_KEYCLOAK_CONTAINER default erp-keycloak-1
import { execFileSync } from 'child_process'; import fs from 'fs'; import crypto from 'crypto'; import os from 'os'; import path from 'path';

const GATEWAY = (process.env.E2E_GATEWAY || 'http://localhost:18080').replace(/\/$/, '');
const KC = process.env.E2E_KEYCLOAK_CONTAINER || 'erp-keycloak-1';
const OUT = path.join(os.homedir(), '.terp-e2e', 'erp-superadmin.txt');

if (fs.existsSync(OUT)) {
  console.log('bootstrap: password file exists, nothing to do');
} else {
  const post = async (p, body) => {
    const r = await fetch(GATEWAY + '/api/v1' + p, { method: 'POST', headers: { 'Content-Type': 'application/json', 'Accept-Language': 'en' }, body: JSON.stringify(body) });
    const j = await r.json().catch(() => ({}));
    return { s: r.status, code: j.code, data: j.data };
  };
  const initial = execFileSync('docker', ['exec', KC, 'printenv', 'ERP_SUPERADMIN_INITIAL_PASSWORD']).toString().trim();
  const pw = 'E2e-' + crypto.randomBytes(9).toString('base64url') + '9aA';
  const r = await post('/auth/initial-password', { username: 'superadmin', currentPassword: initial, newPassword: pw });
  const ok = r.s === 200 && !!(await post('/auth/login', { username: 'superadmin', password: pw })).data?.accessToken;
  if (!ok) {
    console.log('bootstrap: superadmin first password refused —', r.s, r.code || '');
    process.exitCode = 1;
  } else {
    fs.mkdirSync(path.dirname(OUT), { recursive: true });
    fs.writeFileSync(OUT, `ERP stack (${GATEWAY}) superadmin password set by e2e/bootstrap ${new Date().toISOString()}\nsuperadmin\n${pw}\n`, { mode: 0o600 });
    console.log('bootstrap: superadmin password set, saved to', OUT);
  }
}

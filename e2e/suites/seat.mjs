// Plan downgrade: the newest active accounts over the new plan's user limit are locked; admins are kept;
// re-activating is refused (SAAS_011) while all accounts, locked ones included, exceed the limit.
const base = (await c('GET', '/saas/admin/plans?size=50', null, sa)).data.find(p => p.defaultPlan) || {};
const big = (await c('POST', '/saas/admin/plans', { code: 'SEATBIG_' + x, name: 'Seat big ' + x, monthlyPrice: 0, maxUsers: 50, storageLimitGb: 1, enabled: true, modules: base.modules || [] }, sa)).data;
const small = (await c('POST', '/saas/admin/plans', { code: 'SEATSM_' + x, name: 'Seat small ' + x, monthlyPrice: 0, maxUsers: 3, storageLimitGb: 1, enabled: true, modules: base.modules || [] }, sa)).data;
ck('two test plans created', big?.id && small?.id, JSON.stringify([big?.id, small?.id]));
let r = await c('PUT', `/saas/admin/companies/${A.id}/subscription`, { planId: Number(big.id) }, sa);
ck('company on the big plan', r.s === 200, r.s + ' ' + r.code);
const roles = (await c('GET', '/iam/roles', null, t)).data; const empRole = roles.find(z => z.code === 'EMPLOYEE');
const made = [];
for (let i = 1; i <= 3; i++) {
  const un = 'seat.u' + i + '.' + x.toLowerCase(); const p = pw();
  const u = await c('POST', '/iam/users', { username: un, email: un + '@example.com', fullName: 'Seat User ' + i, roleIds: [Number(empRole.id)], password: p }, t);
  const np = pw(); await c('POST', '/auth/initial-password', { username: un, currentPassword: u.data?.temporaryPassword || p, newPassword: np });
  made.push({ id: u.data?.user?.id, un, p: np });
  await new Promise(res => setTimeout(res, 1100)); // distinct creation times so "newest" is well defined
}
ck('three employees created (4 active with the admin)', made.every(m => m.id), JSON.stringify(made.map(m => m.id)));
r = await c('PUT', `/saas/admin/companies/${A.id}/subscription`, { planId: Number(small.id) }, sa);
ck('downgrade to a 3-user plan', r.s === 200, r.s + ' ' + r.code);
const st = async id => (await c('GET', '/iam/users/' + id, null, t)).data?.status;
ck('newest employee locked', await st(made[2].id) === 'LOCKED', await st(made[2].id));
ck('older employees still active', await st(made[0].id) === 'ACTIVE' && await st(made[1].id) === 'ACTIVE');
r = await c('GET', '/iam/roles', null, t);
ck('company admin still works', r.s === 200, r.s);
r = await c('POST', '/auth/login', { username: made[1].un, password: made[1].p });
ck('active employee still logs in', r.s === 200, r.s + ' ' + r.code);
r = await c('POST', '/auth/login', { username: made[2].un, password: made[2].p });
ck('locked employee cannot log in', r.s >= 400, r.s + ' ' + r.code);
r = await c('PUT', '/iam/users/' + made[2].id + '/status', { status: 'ACTIVE' }, t);
ck('re-activate over the limit → SAAS_011', r.s === 400 && r.code === 'SAAS_011', r.s + ' ' + r.code);
r = await c('PUT', '/iam/users/' + made[0].id + '/status', { status: 'LOCKED' }, t);
r = await c('PUT', '/iam/users/' + made[2].id + '/status', { status: 'ACTIVE' }, t);
ck('locking another account frees no seat (locked accounts count)', r.s === 400 && r.code === 'SAAS_011', r.s + ' ' + r.code);
r = await c('DELETE', '/iam/users/' + made[0].id, null, t);
r = await c('PUT', '/iam/users/' + made[2].id + '/status', { status: 'ACTIVE' }, t);
ck('re-activate after deleting an account', r.s === 200, r.s + ' ' + r.code);
r = await c('PUT', '/iam/users/' + made[1].id + '/status', { status: 'LOCKED' }, t);
r = await c('PUT', `/saas/admin/companies/${A.id}/subscription`, { planId: Number(big.id) }, sa);
ck('upgrade does not unlock anyone', r.s === 200 && await st(made[1].id) === 'LOCKED');
r = await c('PUT', `/saas/admin/companies/${B.id}/subscription`, { planId: Number(small.id) }, sa);
ck('other company unaffected', await st(made[2].id) === 'ACTIVE');
console.log(`\n${n - f}/${n} passed`);

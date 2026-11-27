// stores: a store sells from one warehouse, with one daily order window and delivery fees (store-service)
const mkWh = async (name, tok, active = true) => (await c('POST', '/inventory/warehouses', { name, address: '1 Test Street', city: 'Test City', zipCode: '00000', active }, tok)).data;
const w1 = await mkWh('Store WH One', t), w2 = await mkWh('Store WH Two', t), wOff = await mkWh('Store WH Off', t, false), bWh = await mkWh('B Store WH', B.t);
ck('setup', w1?.id && w2?.id && wOff?.id && bWh?.id, [w1?.id, w2?.id, wOff?.id, bWh?.id].join(','));
const form = (code, wid, o = {}) => ({ code, name: 'Express ' + code, warehouseId: Number(wid), orderStart: '10:00', orderEnd: '22:00', ...o });

let r = await c('POST', '/store/stores', form('S1', w1.id, { deliveryFee: 3000, freeDeliveryThreshold: 30000 }), t);
const s1 = r.data; ck('create with defaults', r.s === 200 && s1.timeZone === 'Asia/Seoul' && s1.active === true && Number(s1.deliveryFee) === 3000 && Number(s1.bagFee) === 0 && s1.warehouseId === String(w1.id), r.s + ' ' + r.code + ' ' + JSON.stringify(s1));
r = await c('POST', '/store/stores', form('S1', w2.id), t); ck('duplicate code → STO_002', r.s === 400 && r.code === 'STO_002', r.s + ' ' + r.code);
r = await c('POST', '/store/stores', form('S2', w1.id), t); ck('warehouse of another store → STO_003', r.s === 400 && r.code === 'STO_003' && /Express S1/.test(r.msg), r.s + ' ' + r.code + ' ' + r.msg);
r = await c('POST', '/store/stores', form('S2', bWh.id), t); ck('company B warehouse → STO_004', r.s === 400 && r.code === 'STO_004', r.s + ' ' + r.code);
r = await c('POST', '/store/stores', form('S2', wOff.id), t); ck('inactive warehouse → STO_004', r.s === 400 && r.code === 'STO_004', r.s + ' ' + r.code);
r = await c('POST', '/store/stores', form('S2', w2.id, { orderStart: '22:00', orderEnd: '10:00' }), t); ck('start after end → STO_005', r.s === 400 && r.code === 'STO_005', r.s + ' ' + r.code);
r = await c('POST', '/store/stores', form('S2', w2.id, { timeZone: 'Mars/Base' }), t); ck('unknown zone → STO_005', r.s === 400 && r.code === 'STO_005', r.s + ' ' + r.code);
r = await c('POST', '/store/stores', form('S2', w2.id, { deliveryFee: -1 }), t); ck('negative fee → COMMON_001', r.s === 400 && r.code === 'COMMON_001', r.s + ' ' + r.code);

r = await c('GET', '/store/stores/' + s1.id + '/orderable?at=2026-11-27T12:59:59Z', null, t); ck('orderable 21:59:59 KST', r.s === 200 && r.data.orderableNow === true && r.data.opensAt === null, r.s + ' ' + JSON.stringify(r.data));
r = await c('GET', '/store/stores/' + s1.id + '/orderable?at=2026-11-27T13:00:00Z', null, t); ck('closed 22:00 KST, opens 10:00 KST next day', r.s === 200 && r.data.orderableNow === false && r.data.opensAt === '2026-11-28T01:00:00Z', r.s + ' ' + JSON.stringify(r.data));

r = await c('PUT', '/store/stores/' + s1.id, form('S1', w1.id, { orderStart: '08:00', orderEnd: '23:00' }), t); ck('update = full replacement, fee back to 0', r.s === 200 && r.data.orderStart.startsWith('08:00') && Number(r.data.deliveryFee) === 0, r.s + ' ' + JSON.stringify(r.data));
r = await c('GET', '/store/stores?keyword=s1', null, t); ck('list by keyword', r.s === 200 && r.data.content.length === 1 && r.data.content[0].id === s1.id, r.s + ' ' + r.data?.content?.length);

for (const [m, p, b] of [['GET', '', null], ['GET', '/orderable', null], ['PUT', '', form('S1', w1.id)], ['DELETE', '', null]]) {
  r = await c(m, '/store/stores/' + s1.id + p, b, B.t); ck(`company B ${m}${p} → 404 STO_001`, r.s === 404 && r.code === 'STO_001', r.s + ' ' + r.code);
}
r = await c('GET', '/store/stores', null, B.t); ck('company B list is empty', r.s === 200 && r.data.content.length === 0, r.s + ' ' + r.data?.content?.length);
r = await c('GET', '/store/stores', null, sa); ck('super admin without companyId → STO_007', r.s === 400 && r.code === 'STO_007', r.s + ' ' + r.code);

r = await c('DELETE', '/store/stores/' + s1.id, null, t); ck('delete', r.s === 200, r.s + ' ' + r.code);
r = await c('GET', '/store/stores/' + s1.id, null, t); ck('deleted → 404', r.s === 404 && r.code === 'STO_001', r.s + ' ' + r.code);
r = await c('POST', '/store/stores', form('S1', w1.id), t); ck('re-create same code + warehouse after delete', r.s === 200 && r.data.id !== s1.id, r.s + ' ' + r.code);

const roles = (await c('GET', '/iam/roles', null, t)).data; const emp = roles.find(z => z.code === 'EMPLOYEE'); const ep = pw(); const eun = '__pfx__.emp.' + x.toLowerCase();
const eu = await c('POST', '/iam/users', { username: eun, email: eun + '@example.com', fullName: 'Store Employee', roleIds: [Number(emp.id)], password: ep }, t); const ep2 = pw();
await c('POST', '/auth/initial-password', { username: eun, currentPassword: eu.data?.temporaryPassword || ep, newPassword: ep2 });
const et = (await c('POST', '/auth/login', { username: eun, password: ep2 })).data?.accessToken;
r = await c('POST', '/store/stores', form('S9', w2.id), et); ck('employee create → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', '/store/stores'); ck('no token → 401', r.s === 401, r.s);
console.log(`\n${n - f}/${n} passed`);

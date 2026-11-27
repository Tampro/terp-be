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
// ---- zones + resolve (store-zones spec)
const sq = (lon0, lat0, lon1, lat1, close = true) => ({ type: 'Polygon', coordinates: [[[lon0, lat0], [lon1, lat0], [lon1, lat1], [lon0, lat1], ...(close ? [[lon0, lat0]] : [])]] });
const zw1 = await mkWh('Zone WH One', t), zw2 = await mkWh('Zone WH Two', t), zwB = await mkWh('Zone WH B', B.t);
const z1 = (await c('POST', '/store/stores', form('Z1', zw1.id, { lat: 37.48, lon: 126.89, orderStart: '00:00', orderEnd: '00:01' }), t)).data;
const z2 = (await c('POST', '/store/stores', form('Z2', zw2.id, { lat: 37.48, lon: 126.905 }), t)).data;
const zB = (await c('POST', '/store/stores', form('ZB', zwB.id, { lat: 37.70, lon: 126.89 }), B.t)).data;
ck('zone setup', z1?.id && z2?.id && zB?.id, [z1?.id, z2?.id, zB?.id].join(','));
r = await c('POST', `/store/stores/${z1.id}/zones`, { name: 'Z1 area', area: sq(126.88, 37.47, 126.90, 37.49, false) }, t);
const za = r.data; ck('zone created, open ring closed', r.s === 200 && za.active === true && za.area.coordinates[0].length === 5 && JSON.stringify(za.area.coordinates[0][4]) === JSON.stringify(za.area.coordinates[0][0]), r.s + ' ' + r.code + ' ' + JSON.stringify(za?.area));
const zb = (await c('POST', `/store/stores/${z2.id}/zones`, { name: 'Z2 area', area: sq(126.89, 37.47, 126.91, 37.49) }, t)).data;
await c('POST', `/store/stores/${zB.id}/zones`, { name: 'B area', area: sq(126.88, 37.69, 126.90, 37.71) }, B.t);
for (const [label, area] of [['bow-tie', { type: 'Polygon', coordinates: [[[126.88, 37.47], [126.90, 37.49], [126.90, 37.47], [126.88, 37.49], [126.88, 37.47]]] }],
  ['2 vertices', { type: 'Polygon', coordinates: [[[126.88, 37.47], [126.90, 37.49]]] }],
  ['hole', { type: 'Polygon', coordinates: [sq(126.88, 37.47, 126.90, 37.49).coordinates[0], sq(126.885, 37.475, 126.895, 37.485).coordinates[0]] }],
  ['lat 91', sq(126.88, 37.47, 126.90, 91)]]) {
  r = await c('POST', `/store/stores/${z1.id}/zones`, { name: 'bad', area }, t); ck(`${label} → STO_009`, r.s === 400 && r.code === 'STO_009', r.s + ' ' + r.code);
}
r = await c('POST', `/store/stores/${z1.id}/zones`, { name: '', area: sq(126.88, 37.47, 126.90, 37.49) }, t); ck('blank name → COMMON_001', r.s === 400 && r.code === 'COMMON_001', r.s + ' ' + r.code);
r = await c('GET', `/store/stores/${z1.id}/zones`, null, t); ck('list zones', r.s === 200 && r.data.length === 1 && r.data[0].id === za.id, r.s + ' ' + r.data?.length);
r = await c('PUT', `/store/stores/${z1.id}/zones/${zb.id}`, { name: 'x', area: sq(126.88, 37.47, 126.90, 37.49) }, t); ck('zone of another store → STO_008', r.s === 404 && r.code === 'STO_008', r.s + ' ' + r.code);
r = await c('GET', `/store/stores/${z1.id}/zones`, null, B.t); ck('company B lists A zones → STO_001', r.s === 404 && r.code === 'STO_001', r.s + ' ' + r.code);
r = await c('DELETE', `/store/stores/${zB.id}/zones/${za.id}`, null, B.t); ck('company B deletes A zone under its store → STO_008', r.s === 404 && r.code === 'STO_008', r.s + ' ' + r.code);
r = await c('POST', `/store/stores/${z1.id}/zones`, { name: 'emp', area: sq(126.88, 37.47, 126.90, 37.49) }, et); ck('employee add zone → 403', r.s === 403, r.s + ' ' + r.code);

const res = (lat, lon, tok = t) => c('GET', `/store/resolve?lat=${lat}&lon=${lon}`, null, tok);
r = await res(37.475, 126.885); ck('resolve inside → Z1, closed hour still returned', r.s === 200 && r.data.store.id === z1.id && r.data.zoneId === za.id && r.data.orderableNow === false && !!r.data.opensAt, r.s + ' ' + r.code + ' ' + JSON.stringify(r.data && { s: r.data.store?.id, o: r.data.orderableNow, a: r.data.opensAt }));
r = await res(37.48, 126.88); ck('resolve on the (meridian) edge → Z1', r.s === 200 && r.data.store.id === z1.id, r.s + ' ' + r.code + ' ' + r.data?.store?.id);
r = await res(37.48, 126.895); ck('overlap → nearest Z1', r.s === 200 && r.data.store.id === z1.id, r.s + ' ' + r.data?.store?.id);
r = await res(37.48, 126.905); ck('overlap → nearest Z2', r.s === 200 && r.data.store.id === z2.id, r.s + ' ' + r.data?.store?.id);
r = await res(37.60, 126.89); ck('outside → STO_010', r.s === 404 && r.code === 'STO_010', r.s + ' ' + r.code);
r = await res(37.70, 126.89); ck('company B zone never returned to A', r.s === 404 && r.code === 'STO_010', r.s + ' ' + r.code);
r = await res(37.70, 126.89, B.t); ck('company B resolves its own store', r.s === 200 && r.data.store.id === zB.id, r.s + ' ' + r.data?.store?.id);
r = await res(91, 126.89); ck('lat 91 → COMMON_001', r.s === 400 && r.code === 'COMMON_001', r.s + ' ' + r.code);
r = await c('PUT', `/store/stores/${z1.id}/zones/${za.id}`, { name: 'Z1 off', area: sq(126.88, 37.47, 126.90, 37.49), active: false }, t);
ck('PUT zone (full replacement, inactive)', r.s === 200 && r.data.name === 'Z1 off' && r.data.active === false, r.s + ' ' + r.code);
r = await res(37.48, 126.895); ck('inactive zone ignored → Z2', r.s === 200 && r.data.store.id === z2.id, r.s + ' ' + r.data?.store?.id);
await c('PUT', '/store/stores/' + z2.id, form('Z2', zw2.id, { lat: 37.48, lon: 126.905, active: false }), t);
r = await res(37.48, 126.895); ck('inactive store ignored → STO_010', r.s === 404 && r.code === 'STO_010', r.s + ' ' + r.code);
r = await c('DELETE', `/store/stores/${z1.id}/zones/${za.id}`, null, t); ck('delete zone', r.s === 200, r.s + ' ' + r.code);
r = await c('GET', `/store/stores/${z1.id}/zones`, null, t); ck('deleted zone gone (hard)', r.s === 200 && r.data.length === 0, r.s + ' ' + r.data?.length);
// ---- day-offs (store-day-offs spec); dates are local dates of Asia/Seoul
const kst = (plus) => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul' }).format(new Date(Date.now() + plus * 86400000));
const dToday = kst(0), dTomorrow = kst(1), dYesterday = kst(-1), dAfter = kst(2);
const dw = await mkWh('DayOff WH', t);
const d1 = (await c('POST', '/store/stores', form('D1', dw.id, { lat: 37.30, lon: 126.89 }), t)).data;
await c('POST', `/store/stores/${d1.id}/zones`, { name: 'D1 area', area: sq(126.88, 37.29, 126.90, 37.31) }, t);
const dB = (await c('POST', '/store/stores', form('DB', (await mkWh('DayOff WH B', B.t)).id), B.t)).data;
ck('day-off setup', d1?.id && dB?.id, [d1?.id, dB?.id].join(','));
const dOff = (p) => `/store/stores/${d1.id}/day-offs${p || ''}`;
r = await c('POST', dOff(), { date: dTomorrow, reason: 'Inventory count' }, t); const off2 = r.data;
ck('add tomorrow', r.s === 200 && off2.date === dTomorrow && off2.reason === 'Inventory count' && off2.storeId === d1.id, r.s + ' ' + r.code + ' ' + JSON.stringify(off2));
r = await c('POST', dOff(), { date: dToday }, t); const off1 = r.data; ck('add today (KST) allowed', r.s === 200 && off1.reason === null, r.s + ' ' + r.code);
r = await c('POST', dOff(), { date: dToday }, t); ck('same date → STO_011', r.s === 400 && r.code === 'STO_011', r.s + ' ' + r.code);
r = await c('POST', dOff(), { date: dYesterday }, t); ck('yesterday → STO_012', r.s === 400 && r.code === 'STO_012', r.s + ' ' + r.code);
r = await c('POST', dOff(), { date: '2026-13-40' }, t); ck('bad date → COMMON_001', r.s === 400 && r.code === 'COMMON_001', r.s + ' ' + r.code);
r = await c('POST', dOff(), { reason: 'x' }, t); ck('no date → COMMON_001', r.s === 400 && r.code === 'COMMON_001', r.s + ' ' + r.code);
r = await c('GET', dOff(), null, t); ck('list ordered from today', r.s === 200 && r.data.map(z => z.date).join() === [dToday, dTomorrow].join(), r.s + ' ' + JSON.stringify(r.data?.map(z => z.date)));
r = await c('GET', dOff(`?from=${dTomorrow}&to=${dTomorrow}`), null, t); ck('list from/to inclusive', r.s === 200 && r.data.length === 1 && r.data[0].id === off2.id, r.s + ' ' + r.data?.length);
r = await c('GET', dOff(), null, B.t); ck('company B lists A day-offs → STO_001', r.s === 404 && r.code === 'STO_001', r.s + ' ' + r.code);
r = await c('DELETE', `/store/stores/${dB.id}/day-offs/${off1.id}`, null, B.t); ck('company B deletes A day-off under its store → STO_013', r.s === 404 && r.code === 'STO_013', r.s + ' ' + r.code);
r = await c('POST', dOff(), { date: dAfter }, et); ck('employee add day-off → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', `/store/stores/${d1.id}/orderable`, null, t);
ck('orderable today (day-off) → false, opens after tomorrow 10:00 KST', r.s === 200 && r.data.orderableNow === false && r.data.opensAt === dAfter + 'T01:00:00Z', r.s + ' ' + JSON.stringify(r.data));
r = await res(37.30, 126.89); ck('resolve on a day-off still returns the store', r.s === 200 && r.data.store.id === d1.id && r.data.orderableNow === false && r.data.opensAt === dAfter + 'T01:00:00Z', r.s + ' ' + r.code + ' ' + JSON.stringify(r.data && { s: r.data.store?.id, o: r.data.orderableNow, a: r.data.opensAt }));
r = await c('DELETE', dOff('/' + off1.id), null, t); ck('delete today', r.s === 200, r.s + ' ' + r.code);
r = await c('DELETE', dOff('/' + off2.id), null, t); ck('delete tomorrow', r.s === 200, r.s + ' ' + r.code);
r = await c('DELETE', dOff('/' + off2.id), null, t); ck('deleted again → STO_013 (hard delete)', r.s === 404 && r.code === 'STO_013', r.s + ' ' + r.code);
r = await c('GET', `/store/stores/${d1.id}/orderable`, null, t);
ck('without day-offs: open now or opens by tomorrow', r.s === 200 && (r.data.orderableNow === true || r.data.opensAt <= dTomorrow + 'T01:00:00Z'), r.s + ' ' + JSON.stringify(r.data));
console.log(`\n${n - f}/${n} passed`);

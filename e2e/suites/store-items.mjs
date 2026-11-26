// store items: price and on-sale status per store (warehouse)
const unit = (await c('POST', '/inventory/units', { name: 'Pack' }, t)).data;
const cat = (await c('POST', '/inventory/categories', { name: 'Fresh', color: '#43A047' }, t)).data;
const mkWh = async (name, tok) => (await c('POST', '/inventory/warehouses', { name, address: '1 Test Street', city: 'Test City', zipCode: '00000', active: true }, tok)).data;
const s1 = await mkWh('Store One', t), s2 = await mkWh('Store Two', t), bWh = await mkWh('B Store', B.t);
const mkItem = async (name, type, tok) => (await c('POST', '/inventory/items', { type, name, sku: '__PFX__-' + name.replace(/\W/g, '') + '-' + x, categoryId: Number(cat.id), unitId: Number(unit.id), salePrice: 5000, purchasePrice: 3000, ...(type === 'SERVICE' ? {} : { openingWarehouseId: Number(s1.id), openingQuantity: 4 }) }, tok)).data;
const egg = await mkItem('Egg', 'PRODUCT', t), milk = await mkItem('Milk', 'PRODUCT', t), svc = await mkItem('Delivery', 'SERVICE', t);
ck('setup', s1?.id && s2?.id && bWh?.id && egg?.id && milk?.id && svc?.id, [s1?.id, s2?.id, egg?.id, milk?.id, svc?.id].join(','));

let r = await c('POST', '/inventory/store-items', { warehouseId: Number(s1.id), itemId: Number(egg.id), salePrice: 5990, purchaseLimitQty: 10 }, t);
const si = r.data; ck('create at store 1 (REQ-01)', r.s === 200 && si.status === 'ON_SALE' && Number(si.salePrice) === 5990 && Number(si.stockQuantity) === 4 && si.sku === egg.sku, r.s + ' ' + r.code + ' ' + JSON.stringify(si));
r = await c('POST', '/inventory/store-items', { warehouseId: Number(s2.id), itemId: Number(egg.id), salePrice: 6490 }, t);
ck('same item, other price at store 2', r.s === 200 && Number(r.data.salePrice) === 6490 && Number(r.data.stockQuantity) === 0, r.s + ' ' + r.code);
r = await c('POST', '/inventory/store-items', { warehouseId: Number(s1.id), itemId: Number(egg.id), salePrice: 1 }, t); ck('duplicate → INV_026 (REQ-02)', r.s === 400 && r.code === 'INV_026', r.s + ' ' + r.code);
r = await c('POST', '/inventory/store-items', { warehouseId: Number(s1.id), itemId: Number(svc.id), salePrice: 1 }, t); ck('service item → INV_016 (REQ-03)', r.s === 400 && r.code === 'INV_016', r.s + ' ' + r.code);
r = await c('POST', '/inventory/store-items', { warehouseId: Number(bWh.id), itemId: Number(egg.id), salePrice: 1 }, t); ck('company B warehouse → INV_015 (REQ-03)', r.s === 400 && r.code === 'INV_015', r.s + ' ' + r.code);
r = await c('POST', '/inventory/store-items', { warehouseId: Number(s1.id), itemId: Number(milk.id), salePrice: -1 }, t); ck('negative price → COMMON_001 (REQ-03)', r.s === 400 && r.code === 'COMMON_001', r.s + ' ' + r.code);
r = await c('POST', '/inventory/store-items', { warehouseId: Number(s1.id), itemId: Number(milk.id), salePrice: 1, purchaseLimitQty: 0 }, t); ck('limit 0 → COMMON_001 (REQ-03)', r.s === 400 && r.code === 'COMMON_001', r.s + ' ' + r.code);

r = await c('PUT', '/inventory/store-items/' + si.id, { salePrice: 5490, status: 'ON_SALE' }, t); ck('update = full replacement, limit cleared (REQ-04)', r.s === 200 && Number(r.data.salePrice) === 5490 && r.data.purchaseLimitQty === null, r.s + ' ' + JSON.stringify(r.data));
r = await c('PUT', '/inventory/store-items/' + si.id + '/status', { status: 'SUSPENDED' }, t); ck('suspend (REQ-04)', r.s === 200 && r.data.status === 'SUSPENDED' && Number(r.data.salePrice) === 5490, r.s + ' ' + r.code);
r = await c('GET', '/inventory/store-items?warehouseId=' + s1.id + '&status=SUSPENDED', null, t); ck('list filtered by store + status (REQ-06)', r.s === 200 && r.data.content.length === 1 && r.data.content[0].id === si.id, r.s + ' ' + JSON.stringify(r.data?.content?.map(z => z.id)));
r = await c('GET', '/inventory/store-items?warehouseId=' + s2.id + '&keyword=egg', null, t); ck('list store 2 by keyword (REQ-06)', r.s === 200 && r.data.content.length === 1 && r.data.content[0].warehouseId === String(s2.id), r.s + ' ' + r.data?.content?.length);

for (const [m, p, b] of [['GET', '', null], ['PUT', '', { salePrice: 1, status: 'ON_SALE' }], ['PUT', '/status', { status: 'ON_SALE' }], ['DELETE', '', null]]) {
  r = await c(m, '/inventory/store-items/' + si.id + p, b, B.t); ck(`company B ${m}${p} → 404 INV_027 (REQ-05)`, r.s === 404 && r.code === 'INV_027', r.s + ' ' + r.code);
}
r = await c('GET', '/inventory/store-items', null, B.t); ck('company B list is empty', r.s === 200 && r.data.content.length === 0, r.s + ' ' + r.data?.content?.length);

r = await c('DELETE', '/inventory/store-items/' + si.id, null, t); ck('delete (REQ-07)', r.s === 200, r.s + ' ' + r.code);
r = await c('GET', '/inventory/store-items/' + si.id, null, t); ck('deleted → 404', r.s === 404 && r.code === 'INV_027', r.s + ' ' + r.code);
r = await c('POST', '/inventory/store-items', { warehouseId: Number(s1.id), itemId: Number(egg.id), salePrice: 5990 }, t); ck('re-create after delete (REQ-07)', r.s === 200 && r.data.id !== si.id, r.s + ' ' + r.code);

const lines = [{ warehouseId: Number(s1.id), sku: milk.sku, salePrice: 2500 }, { warehouseId: Number(s2.id), sku: milk.sku, salePrice: 2700, status: 'SUSPENDED' }, { warehouseId: Number(s1.id), sku: egg.sku, salePrice: 5800 }];
r = await c('POST', '/inventory/store-items/bulk', { lines }, t); ck('bulk upsert: 2 created, 1 updated (REQ-08)', r.s === 200 && r.data.created === 2 && r.data.updated === 1, r.s + ' ' + r.code + ' ' + JSON.stringify(r.data));
r = await c('POST', '/inventory/store-items/bulk', { lines }, t); ck('bulk again: 0 created, 3 updated (REQ-08)', r.s === 200 && r.data.created === 0 && r.data.updated === 3, r.s + ' ' + JSON.stringify(r.data));
const before = (await c('GET', '/inventory/store-items?size=100', null, t)).data.totalElements;
r = await c('POST', '/inventory/store-items/bulk', { lines: [{ warehouseId: Number(s2.id), sku: egg.sku, salePrice: 1 }, { warehouseId: Number(s1.id), sku: 'NOPE-' + x, salePrice: 1 }] }, t);
ck('bad line 2 → INV_028 (REQ-08)', r.s === 400 && r.code === 'INV_028' && /2/.test(r.msg) && /INV_020/.test(r.msg), r.s + ' ' + r.code + ' ' + r.msg);
r = await c('GET', '/inventory/store-items?size=100', null, t); ck('bad bulk wrote nothing', r.data.totalElements === before, before + ' → ' + r.data.totalElements);
r = await c('POST', '/inventory/store-items/bulk', { lines: Array.from({ length: 1001 }, () => lines[0]) }, t); ck('1001 lines → INV_029 (REQ-08)', r.s === 400 && r.code === 'INV_029', r.s + ' ' + r.code);

const roles = (await c('GET', '/iam/roles', null, t)).data; const emp = roles.find(z => z.code === 'EMPLOYEE'); const ep = pw(); const eun = '__pfx__.emp.' + x.toLowerCase();
const eu = await c('POST', '/iam/users', { username: eun, email: eun + '@example.com', fullName: 'Store Item Employee', roleIds: [Number(emp.id)], password: ep }, t); const ep2 = pw();
await c('POST', '/auth/initial-password', { username: eun, currentPassword: eu.data?.temporaryPassword || ep, newPassword: ep2 });
const et = (await c('POST', '/auth/login', { username: eun, password: ep2 })).data?.accessToken;
r = await c('POST', '/inventory/store-items', { warehouseId: Number(s1.id), itemId: Number(milk.id), salePrice: 1 }, et); ck('employee create → 403 (REQ-09)', r.s === 403, r.s + ' ' + r.code);
r = await c('POST', '/inventory/store-items/bulk', { lines }, et); ck('employee bulk → 403 (REQ-09)', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', '/inventory/store-items'); ck('no token → 401', r.s === 401, r.s);
console.log(`\n${n - f}/${n} passed`);

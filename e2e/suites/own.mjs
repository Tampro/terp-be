// Manage Own scope + client/vendor portal

const num = v => Number(v); const show = d => JSON.stringify(d)?.slice(0, 240);
const today = new Date().toISOString().slice(0, 10); const later = new Date(Date.now() + 60 * 864e5).toISOString().slice(0, 10); const due = new Date(Date.now() + 30 * 864e5).toISOString().slice(0, 10);
const mkUser = async (tag, roleIds) => { const un = 'ow.' + tag + '.' + x.toLowerCase(); const p0 = pw(); const u = await c('POST', '/iam/users', { username: un, email: un + '@example.com', fullName: 'Own ' + tag, roleIds, password: p0 }, t); const p1 = pw(); await c('POST', '/auth/initial-password', { username: un, currentPassword: u.data?.temporaryPassword || p0, newPassword: p1 }); const tk = (await c('POST', '/auth/login', { username: un, password: p1 })).data?.accessToken; return { id: num(u.data?.user?.id ?? u.data?.id), t: tk, email: un + '@example.com' }; };
const roles = (await c('GET', '/iam/roles', null, t)).data; const R = code => Number(roles.find(z => z.code === code)?.id);
const OWN = ['SALES_VIEW_OWN', 'SALES_MANAGE_OWN', 'QUOTATION_VIEW_OWN', 'QUOTATION_MANAGE_OWN', 'PURCHASE_VIEW_OWN', 'PURCHASE_MANAGE_OWN', 'CRM_VIEW_OWN', 'CRM_MANAGE_OWN', 'CRM_CONTRACT_VIEW_OWN', 'CRM_CONTRACT_MANAGE_OWN', 'PROJECT_VIEW_OWN', 'PROJECT_MANAGE_OWN', 'PROJECT_TASK_MANAGE_OWN', 'SUPPORT_TICKET_VIEW_OWN', 'SUPPORT_TICKET_MANAGE_OWN', 'INVENTORY_VIEW', 'CUSTOMER_VIEW', 'VENDOR_VIEW'];
let r = await c('POST', '/iam/roles', { code: 'OWNONLY_' + x, name: 'Own only', permissions: OWN }, t); const ownRole = r.data;
ck('role with only *_OWN codes', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
const me = (await c('GET', '/me', null, t)).data; const adminId = num(me?.id ?? me?.userId ?? me?.user?.id ?? A.admin.id);
const S1 = await mkUser('s1', [num(ownRole?.id)]);
const C1 = await mkUser('c1', [R('CLIENT')]); const C2 = await mkUser('c2', [R('CLIENT')]); const V1 = await mkUser('v1', [R('VENDOR')]);
ck('users S1 (own-only), C1/C2 (client), V1 (vendor)', S1.t && C1.t && C2.t && V1.t, [!!S1.t, !!C1.t, !!C2.t, !!V1.t].join());
// parties linked to portal users
const cust = (await c('POST', '/accounting/customers', { companyName: 'Client Co ' + x, contactPerson: 'Test Client', email: C1.email, userId: C1.id }, t)).data;
const cust2 = (await c('POST', '/accounting/customers', { companyName: 'Other Client ' + x, contactPerson: 'Other', email: 'other@example.com' }, t)).data;
const ven = (await c('POST', '/accounting/vendors', { companyName: 'Supplier Co ' + x, contactPerson: 'Test Vendor', email: V1.email, userId: V1.id }, t)).data;
r = await c('GET', '/accounting/me/party', null, C1.t); ck('C1 me/party → my customer', r.s === 200 && r.data.customerId === cust.id && r.data.vendorId == null, r.s + ' ' + r.code + ' ' + show(r.data));
r = await c('GET', '/accounting/me/party', null, C2.t); ck('C2 not linked → 404 ACC_098', r.s === 404 && r.code === 'ACC_098', r.s + ' ' + r.code);
r = await c('GET', '/accounting/me/party', null, V1.t); ck('V1 me/party → my vendor', r.data?.vendorId === ven.id, show(r.data));
// commerce master data
const tax = (await c('POST', '/inventory/taxes', { name: 'VAT 10', rate: 10 }, t)).data;
const unit = (await c('POST', '/inventory/units', { name: 'Piece' }, t)).data;
const icat = (await c('POST', '/inventory/categories', { name: 'Goods', color: '#3B82F6' }, t)).data;
const wh = (await c('POST', '/inventory/warehouses', { name: 'Main', address: '1 Test Street', city: 'Test City', zipCode: '70000', active: true }, t)).data;
const item = (await c('POST', '/inventory/items', { type: 'PRODUCT', name: 'Widget', sku: 'O-' + x, taxId: Number(tax.id), categoryId: Number(icat.id), unitId: Number(unit.id), salePrice: 100, purchasePrice: 60 }, t)).data;
const qForm = cid => ({ customerId: Number(cid), warehouseId: Number(wh.id), docDate: today, dueDate: due, paymentTerms: 'Net 30', lines: [{ itemId: Number(item.id), quantity: 1 }] });
// 1. own scope in commerce
const qS1 = (await c('POST', '/sales/quotations', qForm(cust.id), S1.t)).data?.document;
const qAd = (await c('POST', '/sales/quotations', qForm(cust.id), t)).data?.document;
ck('S1 creates a quotation with QUOTATION_MANAGE_OWN', !!qS1?.id, show(qS1));
r = await c('GET', '/sales/quotations', null, S1.t); ck('S1 lists only own quotations', r.s === 200 && r.data.content.length === 1 && r.data.content[0].id === qS1.id, r.s + ' ' + r.code + ' ' + show(r.data?.content?.map(z => z.id)));
r = await c('GET', '/sales/quotations/' + qAd.id, null, S1.t); ck("S1 reads admin's quotation → 404 SAL_001", r.s === 404 && r.code === 'SAL_001', r.s + ' ' + r.code);
r = await c('DELETE', '/sales/quotations/' + qAd.id, null, S1.t); ck("S1 deletes admin's quotation → 404", r.s === 404, r.s + ' ' + r.code);
r = await c('GET', '/sales/quotations', null, t); ck('admin (full) lists both', r.data?.content?.length === 2, show(r.data?.content?.length));
r = await c('POST', '/purchase/invoices', { vendorId: Number(ven.id), warehouseId: Number(wh.id), invoiceDate: today, dueDate: due, lines: [{ itemId: Number(item.id), quantity: 5 }] }, t); const pi = r.data?.invoice;
r = await c('GET', '/purchase/invoices/' + pi.id, null, S1.t); ck("S1 reads admin's purchase invoice → 404 PUR_001", r.s === 404 && r.code === 'PUR_001', r.s + ' ' + r.code);
// 2. client portal (commerce)
r = await c('GET', '/portal/client/quotations', null, C1.t); ck('C1 sees no draft quotations', r.s === 200 && r.data.content.length === 0, r.s + ' ' + r.code + ' ' + show(r.data?.content?.length));
await c('POST', '/sales/quotations/' + qAd.id + '/send', null, t);
const qOther = (await c('POST', '/sales/quotations', qForm(cust2.id), t)).data?.document; await c('POST', '/sales/quotations/' + qOther.id + '/send', null, t);
r = await c('GET', '/portal/client/quotations', null, C1.t); ck("C1 sees only my customer's sent quotation", r.data?.content?.length === 1 && r.data.content[0].id === qAd.id, show(r.data?.content?.map(z => z.id)));
ck('portal row hides internal fields', !('notes' in (r.data?.content?.[0] || {})) && !('createdBy' in (r.data?.content?.[0] || {})), show(r.data?.content?.[0]));
r = await c('GET', '/portal/client/quotations/' + qOther.id, null, C1.t); ck("C1 reads another customer's quotation → 404", r.s === 404, r.s + ' ' + r.code);
r = await c('GET', '/portal/client/quotations/' + qS1.id, null, C1.t); ck('C1 reads a draft → 404', r.s === 404, r.s + ' ' + r.code);
const pdf = await fetch(G + '/portal/client/quotations/' + qAd.id + '/pdf', { headers: { Authorization: 'Bearer ' + C1.t } }); const pb = Buffer.from(await pdf.arrayBuffer());
ck('C1 downloads the quotation PDF', pdf.status === 200 && pb.subarray(0, 5).toString() === '%PDF-', pdf.status + ' ' + pb.subarray(0, 20).toString());
const tA = Date.now(); r = await c('POST', '/portal/client/quotations/' + qAd.id + '/accept', null, C1.t); console.log('accept ms', Date.now() - tA); ck('C1 accepts the quotation', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
r = await c('POST', '/portal/client/quotations/' + qAd.id + '/accept', null, C1.t); ck('accept again → SAL_003', r.code === 'SAL_003', r.s + ' ' + r.code);
r = await c('GET', '/sales/quotations/' + qAd.id, null, t); ck('admin sees the quotation ACCEPTED', r.data?.document?.status === 'ACCEPTED', r.data?.document?.status);
r = await c('GET', '/portal/client/quotations', null, C2.t); ck('unlinked client → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('GET', '/sales/quotations', null, C1.t); ck('client on the staff API → 403', r.s === 403, r.s + ' ' + r.code);
// vendor portal
await c('POST', '/purchase/invoices/' + pi.id + '/post', null, t);
r = await c('GET', '/portal/vendor/purchase-invoices', null, V1.t); ck('V1 sees my posted purchase invoice', r.s === 200 && r.data.content.length === 1 && r.data.content[0].id === pi.id, r.s + ' ' + r.code + ' ' + show(r.data?.content?.map(z => z.id)));
r = await c('GET', '/portal/vendor/purchase-invoices', null, C1.t); ck('client on the vendor portal → 403', r.s === 403, r.s + ' ' + r.code);
// accounting portal
const bank = (await c('POST', '/accounting/bank-accounts', { holderName: 'Test', bankName: 'Test Bank', accountNumber: 'OW' + x, openingBalance: 1000000, active: true }, t)).data;
const pay = (await c('POST', '/accounting/vendor-payments', { partyId: Number(ven.id), bankAccountId: Number(bank.id), paymentDate: today, reference: 'VP1', allocations: [{ invoiceId: Number(pi.id), amount: 100 }] }, t)).data;
r = await c('GET', '/portal/vendor/payments', null, V1.t); ck('V1 sees my vendor payment, no bank account id', r.s === 200 && r.data.content.length === 1 && !('bankAccountId' in r.data.content[0]), r.s + ' ' + r.code + ' ' + show(r.data?.content));
r = await c('GET', '/portal/client/payments', null, C1.t); ck('C1 has no payments', r.s === 200 && r.data.content.length === 0, r.s + ' ' + show(r.data?.content));
// 3. CRM own scope + contract portal
const pipe = (await c('POST', '/crm/pipelines', { name: 'Sales ' + x }, t)).data;
for (const nm of ['New', 'Won']) await c('POST', '/crm/pipelines/' + pipe.id + '/stages', { kind: 'LEAD', name: nm }, t);
await c('POST', '/crm/pipelines/' + pipe.id + '/stages', { kind: 'DEAL', name: 'Open' }, t);
const lAd = (await c('POST', '/crm/leads', { pipelineId: num(pipe.id), name: 'Admin Lead', email: 'a@example.com', subject: 'x', ownerUserId: adminId }, t)).data;
r = await c('POST', '/crm/leads', { pipelineId: num(pipe.id), name: 'S1 Lead', email: 's@example.com', subject: 'x', ownerUserId: S1.id }, S1.t); const lS1 = r.data;
ck('S1 creates own lead', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
r = await c('GET', '/crm/leads?pipelineId=' + pipe.id, null, S1.t); ck('S1 lists only own leads', r.s === 200 && (r.data.content ?? r.data).length === 1, r.s + ' ' + r.code + ' ' + show((r.data?.content ?? r.data)?.map?.(z => z.name)));
r = await c('GET', '/crm/leads/' + (lAd.lead?.id ?? lAd.id), null, S1.t); ck("S1 reads admin's lead → 404", r.s === 404, r.s + ' ' + r.code);
r = await c('GET', '/crm/reports/leads', null, S1.t); ck('S1 CRM report → 403 (company-wide data)', r.s === 403, r.s + ' ' + r.code);
const ctype = (await c('POST', '/crm/contract-types', { name: 'Service ' + x, active: true }, t)).data;
const con = (await c('POST', '/crm/contracts', { subject: 'Support plan', customerId: Number(cust.id), contractTypeId: Number(ctype?.id), value: 1000, startDate: today, endDate: later }, t)).data;
const con2 = (await c('POST', '/crm/contracts', { subject: 'Other plan', customerId: Number(cust2.id), contractTypeId: Number(ctype?.id), value: 500, startDate: today, endDate: later }, t)).data;
r = await c('GET', '/portal/client/contracts', null, C1.t); ck("C1 sees only my customer's contract", r.s === 200 && r.data.content?.length === 1 && r.data.content[0].id === (con?.contract?.id ?? con?.id), r.s + ' ' + r.code + ' ' + show(r.data));
r = await c('GET', '/portal/client/contracts/' + (con2?.contract?.id ?? con2?.id), null, C1.t); ck("C1 reads another customer's contract → 404", r.s === 404, r.s + ' ' + r.code);
// 4. project own scope + portal
const prj = (await c('POST', '/projects', { name: 'Client Project ' + x, startDate: today, endDate: later, description: 'Fake project', userIds: [adminId] }, t)).data;
const prjS1 = (await c('POST', '/projects', { name: 'S1 Project ' + x, startDate: today, endDate: later, userIds: [adminId] }, t)).data;
r = await c('GET', '/projects/' + prjS1.id, null, S1.t); ck('S1 not on the team → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('PUT', '/projects/' + prjS1.id, { name: 'S1 Project ' + x, startDate: today, endDate: later, userIds: [adminId, S1.id] }, t);
r = await c('GET', '/projects/' + prjS1.id, null, S1.t); ck('S1 added to the team → sees the project', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
await c('PUT', '/projects/' + prj.id + '/clients', { customerIds: [String(cust.id)] }, t);
r = await c('GET', '/portal/client/projects', null, C1.t); ck('C1 sees my project only', r.s === 200 && r.data.content?.length === 1 && r.data.content[0].id === prj.id && !('budget' in r.data.content[0]), r.s + ' ' + r.code + ' ' + show(r.data));
r = await c('GET', '/portal/client/projects/' + prjS1.id, null, C1.t); ck("C1 reads a project that isn't mine → 404", r.s === 404, r.s + ' ' + r.code);
// 5. support: client tickets + staff own scope
const tcat = (await c('POST', '/support/ticket-categories', { name: 'General', color: '#123456' }, t)).data;
r = await c('POST', '/portal/client/tickets', { categoryId: Number(tcat.id), subject: 'Portal help', description: '<p>Help</p>' }, C1.t); const tk = r.data;
ck('C1 raises a ticket (account type CLIENT, my email)', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(r.data));
const tAd = (await c('POST', '/support/tickets', { accountType: 'CUSTOM', name: 'Someone', email: 'someone@example.com', categoryId: Number(tcat.id), subject: 'Not yours', description: 'x' }, t)).data?.ticket?.ticket;
r = await c('GET', '/portal/client/tickets', null, C1.t); ck('C1 sees only my ticket', r.data?.content?.length === 1 && r.data.content[0].subject === 'Portal help', show(r.data?.content?.map(z => z.subject)));
const tkId = r.data?.content?.[0]?.id;
r = await c('GET', '/portal/client/tickets/' + tAd?.id, null, C1.t); ck("C1 reads someone else's ticket → 404", r.s === 404, r.s + ' ' + r.code);
r = await c('POST', '/portal/client/tickets/' + tkId + '/replies', { description: '<p>More info</p>' }, C1.t); ck('C1 replies', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
await c('PUT', '/support/tickets/' + tkId + '/status', { status: 'CLOSED' }, t);
r = await c('POST', '/portal/client/tickets/' + tkId + '/replies', { description: '<p>Again</p>' }, C1.t); ck('reply on a closed ticket → SUP_006', r.code === 'SUP_006', r.s + ' ' + r.code);
// a required custom field: the client reads it from the portal and can still raise a ticket
const tf = (await c('POST', '/support/ticket-fields', { label: 'Severity', type: 'SELECT', options: ['Low', 'High'], required: true, width: 6, order: 1, active: true }, t)).data;
r = await c('GET', '/portal/client/tickets/fields', null, C1.t); ck('C1 lists the active ticket fields', r.s === 200 && r.data?.some(z => z.id === tf?.id && z.required && z.options?.includes('High')), r.s + ' ' + r.code + ' ' + show(r.data));
r = await c('GET', '/portal/client/tickets/fields', null, V1.t); ck('vendor lists client ticket fields → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('POST', '/portal/client/tickets', { categoryId: Number(tcat.id), subject: 'No field', description: '<p>x</p>' }, C1.t); ck('raise without the required field → SUP_037', r.code === 'SUP_037', r.s + ' ' + r.code);
r = await c('POST', '/portal/client/tickets', { categoryId: Number(tcat.id), subject: 'With field', description: '<p>x</p>', customFields: [{ fieldId: Number(tf?.id), value: 'High' }] }, C1.t); ck('raise with the required field', r.s === 200 && r.data?.customFields?.some(z => z.value === 'High'), r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(r.data?.customFields));
await c('PUT', '/support/ticket-fields/' + tf?.id, { label: 'Severity', type: 'SELECT', options: ['Low', 'High'], required: true, width: 6, order: 1, active: false }, t);
r = await c('GET', '/support/tickets/' + tAd?.id, null, S1.t); ck("S1 reads a ticket that isn't mine → 404", r.s === 404, r.s + ' ' + r.code);
// isolation
r = await c('GET', '/portal/client/quotations', null, B.t); ck('company B admin on the client portal → 403 or 404', r.s === 403 || r.s === 404, r.s + ' ' + r.code);
r = await c('GET', '/portal/client/quotations'); ck('no token → 401', r.s === 401, r.s);
console.log(`\n${n - f}/${n} passed`);

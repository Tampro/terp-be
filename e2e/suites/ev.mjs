// notification events from 7 services (no egress)

const num = v => Number(v); const show = d => JSON.stringify(d)?.slice(0, 260);
const today = new Date().toISOString().slice(0, 10); const later = new Date(Date.now() + 30 * 864e5).toISOString().slice(0, 10);
const sleep = ms => new Promise(res => setTimeout(res, ms));
const deliveries = async (q = '') => (await c('GET', '/notifications/deliveries?size=200' + q, null, t)).data?.content || [];
const waitType = async (type, ok, ms = 45000) => { const t0 = Date.now(); let v = []; while (Date.now() - t0 < ms) { v = await deliveries('&eventType=' + type); if (ok(v)) return v; await sleep(2000); } return v; };
const me = (await c('GET', '/me', null, t)).data; const myId = num(me?.id ?? me?.userId ?? me?.user?.id ?? A.admin.id);
// Slack: switch every event OFF first, then enable Slack — so nothing can reach slack.com
let r = await c('PUT', '/settings/integrations/slack/events', { enabledEventTypes: [] }, t); ck('Slack events all off', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
r = await c('GET', '/settings/integrations/slack/events', null, t); const allOff = (r.data?.modules || []).flatMap(m => m.events).every(e => e.enabled === false);
ck('Slack event list grouped by module, all disabled', r.s === 200 && allOff && (r.data?.modules || []).length > 5, r.s + ' ' + show(r.data?.modules?.map(m => m.module)));
r = await c('PUT', '/settings/integrations/slack', { enabled: true, webhookUrl: 'https://hooks.slack.com/services/T000/B000/fakefakefake' }, t); ck('Slack enabled (events still off)', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
r = await c('PUT', '/settings/integrations/slack/events', { enabledEventTypes: ['no.such.event'] }, t); ck('unknown event type → SETTINGS_021', r.code === 'SETTINGS_021', r.s + ' ' + r.code);
r = await c('GET', '/settings/integrations/slack/events', null, B.t); ck("company B's Slack list is its own (all on by default)", (r.data?.modules || []).flatMap(m => m.events).every(e => e.enabled === true), show(r.data?.configured));
// webhooks on new event codes, unresolvable host
const codes = ['NEW_CUSTOMER', 'NEW_VENDOR', 'NEW_WAREHOUSE', 'NEW_HOLIDAY', 'NEW_PROJECT', 'NEW_TASK', 'NEW_MILESTONE', 'NEW_LEAD', 'NEW_DEAL', 'NEW_SALES_PROPOSAL', 'SALES_PROPOSAL_STATUS_UPDATED', 'NEW_USER'];
for (const code of codes) await c('POST', '/settings/webhooks', { eventCode: code, url: 'https://hooks.no-such-host.invalid/' + code.toLowerCase(), active: true }, t);
r = await c('GET', '/settings/webhooks?size=50', null, t); ck('12 webhooks registered', r.data?.content?.length === 12, show(r.data?.content?.length));
// trigger events in 7 services
const cust = (await c('POST', '/accounting/customers', { companyName: 'Event Buyer ' + x, contactPerson: 'Test', email: 'buyer@example.com' }, t)).data;
await c('POST', '/accounting/vendors', { companyName: 'Event Supplier ' + x, contactPerson: 'Test', email: 'supplier@example.com' }, t);
const wh = (await c('POST', '/inventory/warehouses', { name: 'Event WH', address: '1 Test Street', city: 'Test City', zipCode: '70000', active: true }, t)).data;
const hcat = (await c('POST', '/org/holiday-categories', { name: 'Ev Days ' + x, color: '#22C55E' }, t)).data;
await c('POST', '/org/holidays', { name: 'Test Day', categoryId: num(hcat?.id), startDate: later, endDate: later, isPaid: true }, t);
const prj = (await c('POST', '/projects', { name: 'Event Project ' + x, startDate: today, endDate: later, userIds: [myId] }, t)).data;
const ms = (await c('POST', '/projects/' + prj.id + '/milestones', { title: 'Phase 1', startDate: today, endDate: later, status: 'ONGOING', progress: 0 }, t)).data;
await c('POST', '/projects/' + prj.id + '/tasks', { milestoneId: Number(ms?.id), title: 'Event task', priority: 'HIGH', assigneeIds: [myId], startDate: today, dueDate: later }, t);
const pipe = (await c('POST', '/crm/pipelines', { name: 'Ev ' + x }, t)).data;
await c('POST', '/crm/pipelines/' + pipe.id + '/stages', { kind: 'LEAD', name: 'New' }, t); await c('POST', '/crm/pipelines/' + pipe.id + '/stages', { kind: 'DEAL', name: 'Open' }, t);
await c('POST', '/crm/leads', { pipelineId: num(pipe.id), name: 'Event Lead', email: 'lead@example.com', subject: 'x', ownerUserId: myId }, t);
await c('POST', '/crm/deals', { pipelineId: num(pipe.id), name: 'Event Deal', price: 1000 }, t);
const tax = (await c('POST', '/inventory/taxes', { name: 'VAT 10', rate: 10 }, t)).data;
const unit = (await c('POST', '/inventory/units', { name: 'Piece' }, t)).data;
const icat = (await c('POST', '/inventory/categories', { name: 'Goods', color: '#3B82F6' }, t)).data;
const svc = (await c('POST', '/inventory/items', { type: 'SERVICE', name: 'Consulting', generateSku: true, categoryId: Number(icat.id), unitId: Number(unit.id), salePrice: 100, purchasePrice: 0 }, t)).data;
const sp = (await c('POST', '/sales/proposals', { customerId: Number(cust.id), docDate: today, dueDate: later, paymentTerms: 'Net 30', lines: [{ itemId: Number(svc.id) }] }, t)).data?.document;
await c('POST', '/sales/proposals/' + sp?.id + '/send', null, t);
const roles = (await c('GET', '/iam/roles', null, t)).data; const empRole = roles.find(z => z.code === 'EMPLOYEE');
await c('POST', '/iam/users', { username: 'ev.user.' + x.toLowerCase(), email: 'ev.user.' + x.toLowerCase() + '@example.com', fullName: 'Event User', roleIds: [Number(empRole.id)], password: pw() }, t);
// every new event reaches notification-service and fans out to its webhook
const expect = [['accounting.customer.created', 'NEW_CUSTOMER'], ['accounting.vendor.created', 'NEW_VENDOR'], ['inventory.warehouse.created', 'NEW_WAREHOUSE'], ['employee.holiday.created', 'NEW_HOLIDAY'], ['project.project.created', 'NEW_PROJECT'], ['project.milestone.created', 'NEW_MILESTONE'], ['project.task.created', 'NEW_TASK'], ['crm.lead.created', 'NEW_LEAD'], ['crm.deal.created', 'NEW_DEAL'], ['sales.proposal.created', 'NEW_SALES_PROPOSAL'], ['sales.proposal.status_changed', 'SALES_PROPOSAL_STATUS_UPDATED'], ['iam.user.created', 'NEW_USER']];
for (const [type, code] of expect) {
  const v = await waitType(type, x => x.some(z => z.channel === 'WEBHOOK' && z.attempts > 0) && x.some(z => z.channel === 'SLACK'));
  const w = v.find(z => z.channel === 'WEBHOOK'); const s = v.find(z => z.channel === 'SLACK');
  ck(type + ' → webhook ' + code + ' refused (no egress) + Slack SKIPPED per event', w && w.status === 'FAILED' && w.lastErrorCode === 'URL_REFUSED' && w.target === 'hooks.no-such-host.invalid' && s && s.status === 'SKIPPED' && s.lastErrorCode === 'CHANNEL_EVENT_DISABLED', show(v.map(z => [z.channel, z.status, z.lastErrorCode, z.target])));
}
const all = await deliveries(); ck('no delivery was SENT anywhere (nothing left the machine)', all.every(z => z.status !== 'SENT'), show(all.filter(z => z.status === 'SENT')));
ck('no SMS rows (Twilio off)', !all.some(z => z.channel === 'SMS'), all.filter(z => z.channel === 'SMS').length + ' sms rows');
// recruitment: offer letter send rule
r = await c('POST', '/recruitment/offers/999999999/letter/send?templateId=1', null, t); ck('letter send on unknown offer → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('POST', '/recruitment/offers/1/letter/send?templateId=1', null, B.t); ck('letter send without the offer in my company → 404', r.s === 404, r.s + ' ' + r.code);
// permissions
r = await c('PUT', '/settings/integrations/slack/events', { enabledEventTypes: [] }); ck('channel events without token → 401', r.s === 401, r.s);
console.log(`\n${n - f}/${n} passed`);

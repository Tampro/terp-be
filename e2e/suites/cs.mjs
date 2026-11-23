// company settings: profile, notifications, integrations, webhooks, SSRF, ticket fields

const show = d => JSON.stringify(d)?.slice(0, 240); const ST = '/settings';
// profile
let r = await c('PUT', ST + '/company/profile', { name: 'Test Co Ltd ' + x, registrationNumber: 'REG-0001', address: '1 Test Street', city: 'Test City', state: 'TS', country: 'VN', zipCode: '700000', phone: '+84900000003', email: 'billing@example.com', senderName: 'Test Co', taxNumberEnabled: true, taxType: 'VAT', taxNumber: '0000000000' }, t);
ck('profile PUT', r.s === 200 && r.data.registrationNumber === 'REG-0001' && r.data.accountName, r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(r.data));
r = await c('GET', ST + '/company/effective', null, t); ck('effective includes profile with resolved name', r.data?.profile?.name === 'Test Co Ltd ' + x, show(r.data?.profile));
r = await c('GET', ST + '/company/profile', null, B.t); ck('company B profile is its own (no leak)', r.s === 200 && r.data.registrationNumber == null, show(r.data));
// notifications
r = await c('GET', ST + '/email-notifications', null, t); const groups = r.data || [];
ck('notification list grouped (GENERAL, CRM, SUPPORT, RECRUITMENT)', ['GENERAL', 'CRM', 'SUPPORT', 'RECRUITMENT'].every(m => groups.some(g => g.module === m)), r.s + ' ' + r.code + ' ' + show(groups.map(g => g.module)));
r = await c('PUT', ST + '/email-notifications/INVOICE_SENT', { enabled: false }, t); ck('toggle INVOICE_SENT off (dispatched false)', r.s === 200 && r.data.enabled === false && r.data.dispatched === false, r.s + ' ' + r.code + ' ' + show(r.data));
r = await c('GET', ST + '/email-notifications/INVOICE_SENT/enabled', null, t); ck('enabled lookup → false', r.data?.enabled === false, show(r.data));
r = await c('GET', ST + '/email-notifications/INVOICE_SENT/enabled', null, B.t); ck('company B unaffected → true', r.data?.enabled === true, show(r.data));
r = await c('PUT', ST + '/email-notifications/NO_SUCH', { enabled: false }, t); ck('unknown code → 404 EMAIL_001', r.s === 404 && r.code === 'EMAIL_001', r.s + ' ' + r.code);
// integrations
const fakeTok = '123456:' + 'A'.repeat(30);
r = await c('PUT', ST + '/integrations/telegram', { enabled: true, botToken: fakeTok, chatId: '-100123' }, t); ck('telegram saved, token not returned', r.s === 200 && r.data.hasBotToken === true && !show(r.data).includes('AAAAAAAA'), r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(r.data));
r = await c('PUT', ST + '/integrations/telegram', { enabled: true, botToken: '', chatId: '-100999' }, t); ck('blank token keeps stored one', r.s === 200 && r.data.hasBotToken === true && r.data.chatId === '-100999', r.s + ' ' + show(r.data));
r = await c('PUT', ST + '/integrations/twilio', { enabled: true, accountSid: 'AC' + '0'.repeat(32), authToken: 'fake-twilio-token', fromNumber: '+15005550006' }, t); ck('twilio saved', r.s === 200 && r.data.hasAuthToken, r.s + ' ' + r.code + ' ' + r.msg);
r = await c('PUT', ST + '/integrations/ai-assistant', { enabled: true, provider: 'ANTHROPIC', apiKey: 'fake-key-123', model: 'claude-test' }, t); ck('AI saved', r.s === 200 && r.data.hasApiKey, r.s + ' ' + r.code + ' ' + r.msg);
r = await c('PUT', ST + '/integrations/slack', { enabled: true }, t); ck('slack enabled without URL → SETTINGS_012', r.code === 'SETTINGS_012', r.s + ' ' + r.code);
r = await c('PUT', ST + '/integrations/slack', { enabled: true, webhookUrl: 'https://evil.example.com/hook' }, t); ck('slack non-Slack host → SETTINGS_013', r.code === 'SETTINGS_013', r.s + ' ' + r.code + ' ' + r.msg);
r = await c('POST', ST + '/integrations/slack/test', null, t); ck('slack test not configured → SETTINGS_017', r.code === 'SETTINGS_017', r.s + ' ' + r.code);
r = await c('GET', ST + '/integrations', null, t); ck('integrations overview has no secret', r.s === 200 && r.data.telegram.configured && !show(r.data).includes('fake-') && !JSON.stringify(r.data).includes('AAAAAAAA'), show(r.data));
r = await c('GET', ST + '/integrations', null, B.t); ck('company B sees nothing configured', r.data?.telegram?.configured === false, show(r.data?.telegram));
r = await c('DELETE', ST + '/integrations/twilio', null, t); r = await c('GET', ST + '/integrations', null, t); ck('delete twilio', r.data?.twilio?.configured === false, show(r.data?.twilio));
// webhooks + SSRF
r = await c('GET', ST + '/webhooks/events', null, t); ck('webhook event catalog by module', (r.data || []).some(g => g.module === 'SALES') && (r.data || []).some(g => g.module === 'HRM'), show((r.data || []).map(g => g.module)));
const ev = r.data?.find(g => g.module === 'SALES')?.events?.[0]?.code;
for (const [u, label] of [['http://hooks.example.com/x', 'http scheme'], ['https://localhost/x', 'localhost'], ['https://127.0.0.1/x', 'loopback'], ['https://169.254.169.254/latest/meta-data', 'metadata'], ['https://10.0.0.5/x', 'private 10/8'], ['https://192.168.1.10/x', 'private 192.168'], ['https://[::1]/x', 'IPv6 loopback'], ['https://keycloak:8080/x', 'docker service name'], ['https://iam-service/x', 'no-dot host']]) {
  r = await c('POST', ST + '/webhooks', { eventCode: ev, url: u, active: true }, t); ck('SSRF refused: ' + label, r.s === 400 && r.code === 'SETTINGS_013', r.s + ' ' + r.code + ' ' + r.msg);
}
r = await c('POST', ST + '/webhooks', { eventCode: 'NO_SUCH_EVENT', url: 'https://hooks.example.com/erp', active: true }, t); ck('unknown event → SETTINGS_014', r.code === 'SETTINGS_014', r.s + ' ' + r.code);
r = await c('POST', ST + '/webhooks', { eventCode: ev, url: 'https://hooks.example.com/erp', active: true, secret: 'short' }, t); ck('short secret → SETTINGS_019', r.code === 'SETTINGS_019', r.s + ' ' + r.code);
r = await c('POST', ST + '/webhooks', { eventCode: ev, url: 'https://hooks.example.com/erp', active: true, secret: 'fake-signing-secret-0001' }, t);
const wh = r.data; ck('webhook created, secret hidden', r.s === 200 && wh.hasSecret === true && !show(wh).includes('fake-signing'), r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(wh));
r = await c('PUT', ST + '/webhooks/' + wh.id, { eventCode: ev, url: 'https://hooks.example.com/erp2', active: false, secret: '' }, t); ck('PUT keeps secret, deactivates', r.s === 200 && r.data.hasSecret && r.data.active === false, r.s + ' ' + show(r.data));
r = await c('GET', ST + '/webhooks', null, t); ck('webhook list', r.data?.content?.length === 1, show(r.data));
r = await c('GET', ST + '/webhooks/' + wh.id, null, B.t); ck('company B reads A webhook → 404 SETTINGS_015', r.s === 404 && r.code === 'SETTINGS_015', r.s + ' ' + r.code);
r = await c('POST', ST + '/webhooks/' + wh.id + '/test', null, B.t); ck('company B tests A webhook → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('DELETE', ST + '/webhooks/' + wh.id, null, B.t); ck('company B deletes A webhook → 404', r.s === 404, r.s + ' ' + r.code);
// ticket custom fields (support)
r = await c('POST', '/support/ticket-fields', { label: 'Priority', type: 'SELECT', required: true, width: 6, order: 1, active: true }, t); ck('SELECT without options → SUP_040', r.code === 'SUP_040', r.s + ' ' + r.code);
r = await c('POST', '/support/ticket-fields', { label: 'Wide', type: 'TEXT', required: false, width: 5, order: 9, active: true }, t); ck('width 5 → SUP_041', r.code === 'SUP_041', r.s + ' ' + r.code);
const f1 = (await c('POST', '/support/ticket-fields', { label: 'Priority', type: 'SELECT', options: ['Low', 'High'], required: true, width: 6, order: 1, active: true }, t)).data;
const f2 = (await c('POST', '/support/ticket-fields', { label: 'Order date', type: 'DATE', required: false, width: 6, order: 2, active: true }, t)).data;
const fB = (await c('POST', '/support/ticket-fields', { label: 'B field', type: 'TEXT', required: false, width: 12, order: 1, active: true }, B.t)).data;
ck('ticket fields created', f1?.id && f2?.id && fB?.id, show([f1, f2]));
const slug = 'cs-' + x.toLowerCase();
await c('PUT', '/support/portal/settings', { slug, enabled: true, brandName: 'Test Support ' + x }, t);
const cat = (await c('POST', '/support/ticket-categories', { name: 'General', color: '#123456' }, t)).data;
r = await c('GET', '/public/support/' + slug); ck('portal info lists fields in order', r.data?.ticketFields?.map(z => z.label).join() === 'Priority,Order date', show(r.data?.ticketFields));
const pub = cf => c('POST', '/public/support/' + slug + '/tickets', { name: 'Test Customer', email: 'cf.' + x.toLowerCase() + '@example.com', categoryId: Number(cat.id), subject: 'Custom fields', description: 'Hello', customFields: cf });
r = await pub([{ fieldId: Number(f2.id), value: '2026-12-01' }]); ck('required field missing → SUP_037', r.code === 'SUP_037', r.s + ' ' + r.code);
r = await pub([{ fieldId: Number(f1.id), value: 'Urgent' }]); ck('value not an option → SUP_039', r.code === 'SUP_039', r.s + ' ' + r.code);
r = await pub([{ fieldId: Number(f1.id), value: 'High' }, { fieldId: Number(fB.id), value: 'x' }]); ck("company B's field → SUP_035", r.code === 'SUP_035', r.s + ' ' + r.code);
await new Promise(res => setTimeout(res, 14000));
r = await pub([{ fieldId: Number(f1.id), value: 'High' }, { fieldId: Number(f2.id), value: 'not-a-date' }]); ck('bad date → SUP_038', r.code === 'SUP_038', r.s + ' ' + r.code);
r = await pub([{ fieldId: Number(f1.id), value: 'High' }, { fieldId: Number(f2.id), value: '2026-12-01' }]); const pt = r.data; ck('public ticket with custom fields', r.s === 200 && pt?.trackingToken, r.s + ' ' + r.code + ' ' + r.msg);
await new Promise(res => setTimeout(res, 7000));
r = await c('POST', '/public/support/' + slug + '/tickets/track', { trackingToken: pt?.trackingToken }); ck('track shows label + value', show(r.data?.customFields).includes('Priority') && show(r.data?.customFields).includes('High'), r.s + ' ' + r.code + ' ' + show(r.data?.customFields));
const tid = (await c('GET', '/support/tickets?keyword=Custom', null, t)).data?.content?.[0]?.id;
r = await c('GET', '/support/tickets/' + tid, null, t); ck('admin detail shows custom fields', JSON.stringify(r.data).includes('Order date'), r.s + ' ' + show(r.data?.customFields));
r = await c('PUT', '/support/ticket-fields/' + f1.id, { label: 'Priority', type: 'SELECT', options: ['Low', 'High'], required: true, width: 6, order: 1, active: true }, B.t); ck('company B edits A field → 404 SUP_042', r.s === 404 && r.code === 'SUP_042', r.s + ' ' + r.code);
// permissions
const roles = (await c('GET', '/iam/roles', null, t)).data; const empRole = roles.find(z => z.code === 'EMPLOYEE'); const ep = pw(); const eun = 'cs.emp.' + x.toLowerCase();
const eu = await c('POST', '/iam/users', { username: eun, email: eun + '@example.com', fullName: 'CS Employee', roleIds: [Number(empRole.id)], password: ep }, t); const ep2 = pw();
await c('POST', '/auth/initial-password', { username: eun, currentPassword: eu.data?.temporaryPassword || ep, newPassword: ep2 });
const et = (await c('POST', '/auth/login', { username: eun, password: ep2 })).data?.accessToken;
r = await c('GET', ST + '/integrations', null, et); ck('employee integrations → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('POST', ST + '/webhooks', { eventCode: ev, url: 'https://hooks.example.com/erp', active: true }, et); ck('employee creates webhook → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('PUT', ST + '/company/profile', { name: 'Hack' }, et); ck('employee edits profile → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('POST', '/support/ticket-fields', { label: 'x', type: 'TEXT', required: false, width: 12, order: 1, active: true }, et); ck('employee creates ticket field → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', ST + '/webhooks'); ck('no token → 401', r.s === 401, r.s);
console.log(`\n${n - f}/${n} passed`);

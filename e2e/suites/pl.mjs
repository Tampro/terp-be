// platform settings: email toggles, platform webhooks, cache

const show = d => JSON.stringify(d)?.slice(0, 280); const sleep = ms => new Promise(res => setTimeout(res, ms));
const S = '/settings/admin';
// 1. platform email toggles
let r = await c('GET', S + '/email-notifications', null, sa); const before = r.data;
ck('platform email toggles readable', r.s === 200 && typeof before?.newUser === 'boolean', r.s + ' ' + r.code + ' ' + show(r.data));
r = await c('PUT', S + '/email-notifications', { newUser: true, helpdeskTicketCreated: true, helpdeskTicketReply: true, helpdeskNotifyEmails: ['Support@Example.INVALID', 'support@example.invalid'] }, sa);
ck('recipients lower-cased and de-duplicated', r.s === 200 && r.data.helpdeskNotifyEmails.length === 1 && r.data.helpdeskNotifyEmails[0] === 'support@example.invalid', r.s + ' ' + r.code + ' ' + show(r.data));
r = await c('PUT', S + '/email-notifications', { newUser: true, helpdeskTicketCreated: true, helpdeskTicketReply: true, helpdeskNotifyEmails: Array.from({ length: 11 }, (_, i) => 'a' + i + '@example.invalid') }, sa); ck('11 recipients → COMMON_001', r.code === 'COMMON_001', r.s + ' ' + r.code);
r = await c('PUT', S + '/email-notifications', { newUser: true, helpdeskTicketCreated: true, helpdeskTicketReply: true, helpdeskNotifyEmails: ['x'] }, sa); ck('bad address → COMMON_001', r.code === 'COMMON_001', r.s + ' ' + r.code);
r = await c('GET', S + '/email-notifications', null, t); ck('company admin on platform toggles → 403', r.s === 403, r.s + ' ' + r.code);
// 2. platform webhooks (unresolvable host → no egress)
r = await c('GET', S + '/webhooks/events', null, sa); ck('platform event catalog has NEW_COMPANY', r.s === 200 && show(r.data).includes('NEW_COMPANY'), r.s + ' ' + show(r.data));
r = await c('POST', S + '/webhooks', { eventCode: 'NEW_COMPANY', url: 'http://nowhere.invalid/hook', active: true }, sa); ck('http URL → SETTINGS_013', r.code === 'SETTINGS_013', r.s + ' ' + r.code);
r = await c('POST', S + '/webhooks', { eventCode: 'NEW_COMPANY', url: 'https://10.0.0.1/hook', active: true }, sa); ck('private IP → SETTINGS_013', r.code === 'SETTINGS_013', r.s + ' ' + r.code);
r = await c('POST', S + '/webhooks', { eventCode: 'NEW_COMPANY', url: 'https://nowhere.invalid/hook', active: true, secret: 'fake-platform-secret-0001' }, sa); const wh = r.data;
ck('platform webhook saved, secret hidden', r.s === 200 && wh.hasSecret === true && !show(wh).includes('fake-platform'), r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(wh));
r = await c('POST', S + '/webhooks/' + wh.id + '/test', null, sa); ck('test send to an unresolvable host → SETTINGS_013', r.code === 'SETTINGS_013', r.s + ' ' + r.code + ' ' + r.msg);
r = await c('GET', S + '/webhooks', null, t); ck('company admin on platform webhooks → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', S + '/webhooks/999999999', null, sa); ck('unknown platform webhook → 404 SETTINGS_026', r.s === 404 && r.code === 'SETTINGS_026', r.s + ' ' + r.code);
// a new company → platform.company.created → platform webhook delivery (null company)
const plist = async (q) => (await c('GET', '/notifications/deliveries?platform=true&size=100&' + q, null, sa)).data?.content || [];
const seen = new Set((await plist('eventType=platform.company.created')).map(z => z.id));
const Cc = await mk('C');
let ds = [], fresh = []; for (let i = 0; i < 30; i++) { ds = await plist('eventType=platform.company.created'); fresh = ds.filter(z => !seen.has(z.id) && z.channel === 'WEBHOOK'); if (fresh.length && fresh.every(z => z.attempts > 0)) break; await sleep(2000); }
const pw1 = fresh[0];
ck('new company → platform WEBHOOK delivery, company null, URL_REFUSED', pw1 && pw1.companyId == null && pw1.target === 'nowhere.invalid' && pw1.status === 'FAILED' && pw1.lastErrorCode === 'URL_REFUSED', show(ds.map(z => [z.channel, z.companyId, z.target, z.status, z.lastErrorCode])));
r = await c('GET', '/notifications/deliveries?platform=true', null, t); ck('company admin on platform deliveries → 403 NTF_005', r.s === 403 && r.code === 'NTF_005', r.s + ' ' + r.code);
r = await c('GET', '/notifications/deliveries/' + pw1?.id, null, t); ck('company admin reads a platform delivery → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('GET', '/notifications/deliveries?size=200', null, Cc.t); ck("new company's own log doesn't show the platform row", (r.data?.content || []).every(z => z.eventType !== 'platform.company.created'), show(r.data?.content?.map(z => z.eventType)));
// helpdesk ticket → platform EMAIL to the listed address; switch off → SKIPPED EMAIL_DISABLED
const seenH = new Set((await plist('eventType=platform.helpdesk.ticket_created&channel=EMAIL')).map(z => z.id));
const hcat = (await c('POST', '/helpdesk/categories', { name: 'Platform ' + x, color: '#3b82f6', active: true }, sa)).data;
r = await c('POST', '/helpdesk/tickets', { title: 'Platform help ' + x, description: '<p>Help</p>', categoryId: Number(hcat?.id) }, t);
ck('company opens a helpdesk ticket', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
for (let i = 0; i < 30; i++) { ds = await plist('eventType=platform.helpdesk.ticket_created&channel=EMAIL'); fresh = ds.filter(z => !seenH.has(z.id)); if (fresh.length && fresh.every(z => z.attempts > 0 || z.status !== 'PENDING')) break; await sleep(2000); }
const he = fresh[0]; ck('helpdesk → platform EMAIL to *@example.invalid (attempted or SKIPPED, never SENT)', he && he.target === '*@example.invalid' && he.status !== 'SENT', show(ds.map(z => [z.target, z.status, z.lastErrorCode])));
// 3. cache
r = await c('GET', S + '/cache', null, sa); ck('cache list: authorities, translations, impersonation', r.s === 200 && ['authorities', 'translations', 'impersonation'].every(n => r.data.caches.some(z => z.name === n)), r.s + ' ' + r.code + ' ' + show(r.data));
r = await c('POST', S + '/cache/clear', null, sa); const auth = r.data?.caches?.find(z => z.name === 'authorities');
ck('cache clear → authorities near empty', r.s === 200 && auth && auth.estimatedSize <= 2, r.s + ' ' + show(r.data));
r = await c('GET', '/iam/roles', null, t); ck('permissions still resolve after clear', r.s === 200, r.s + ' ' + r.code);
r = await c('POST', S + '/cache/clear', null, t); ck('company admin clears cache → 403', r.s === 403, r.s + ' ' + r.code);
// restore toggles
await c('PUT', S + '/email-notifications', { newUser: true, helpdeskTicketCreated: true, helpdeskTicketReply: true, helpdeskNotifyEmails: before?.helpdeskNotifyEmails ?? [] }, sa);
await c('DELETE', S + '/webhooks/' + wh.id, null, sa);
console.log(`\n${n - f}/${n} passed`);

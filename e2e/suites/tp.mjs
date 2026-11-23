// editable notification templates

const show = d => JSON.stringify(d)?.slice(0, 280); const sleep = ms => new Promise(res => setTimeout(res, ms));
const E = '/settings/email-notification-templates'; const C = '/settings/notification-templates';
// email templates
let r = await c('GET', E, null, t); const groups = r.data || [];
ck('email template list grouped by module, nothing customised', r.s === 200 && groups.length >= 3 && groups.flatMap(g => g.templates).every(z => z.customised === false), r.s + ' ' + r.code + ' ' + show(groups.map(g => g.module)));
r = await c('GET', E + '/SUPPORT_TICKET_CREATED', null, t); ck('template detail lists variables and 3 languages', r.s === 200 && ['name', 'ticketNumber', 'subject'].every(v => r.data.variables.includes(v)) && r.data.languages.length >= 2, r.s + ' ' + show({ v: r.data?.variables, l: r.data?.languages?.map(z => z.lang) }));
r = await c('PUT', E + '/SUPPORT_TICKET_CREATED/vi', { subject: 'Ticket {ticketNumber}', body: '<p>Xin chào {name}</p><script>alert(1)</script><img src=x onerror=alert(2)>' }, t);
const vi = r.data?.languages?.find(z => z.lang === 'vi');
ck('save override: script and onerror stripped', r.s === 200 && vi?.customised === true && !/script|onerror/i.test(vi.body) && vi.body.includes('{name}'), r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(vi));
r = await c('PUT', E + '/SUPPORT_TICKET_CREATED/vi', { subject: 'Hi {password}', body: '<p>x</p>' }, t); ck('unknown placeholder → SETTINGS_025', r.code === 'SETTINGS_025', r.s + ' ' + r.code + ' ' + r.msg);
r = await c('PUT', E + '/SUPPORT_TICKET_CREATED/xx', { subject: 's', body: 'b' }, t); ck('unsupported language → EMAIL_002', r.code === 'EMAIL_002', r.s + ' ' + r.code);
r = await c('PUT', E + '/NO_SUCH_CODE/vi', { subject: 's', body: 'b' }, t); ck('unknown code → 404 EMAIL_001', r.s === 404 && r.code === 'EMAIL_001', r.s + ' ' + r.code);
r = await c('POST', E + '/SUPPORT_TICKET_CREATED/vi/preview', null, t); ck('preview renders sample values', r.s === 200 && r.data.subject === 'Ticket [ticketNumber]' && r.data.body.includes('[name]'), r.s + ' ' + r.code + ' ' + show(r.data));
r = await c('POST', E + '/SUPPORT_TICKET_CREATED/en/preview', { subject: 'S {name}', body: '<b>{name}</b>{{7*7}}' }, t); ck('preview escapes and has no expression evaluation', r.s === 200 && !r.data.body.includes('49'), r.s + ' ' + show(r.data));
r = await c('GET', E, null, t); ck('list shows SUPPORT_TICKET_CREATED customised (vi)', r.data.flatMap(g => g.templates).find(z => z.code === 'SUPPORT_TICKET_CREATED')?.customisedLanguages?.includes('vi'), show(r.data.flatMap(g => g.templates).find(z => z.code === 'SUPPORT_TICKET_CREATED')));
r = await c('GET', E + '/SUPPORT_TICKET_CREATED', null, B.t); ck("company B doesn't see A's override", r.s === 200 && r.data.languages.every(z => z.customised === false), show(r.data?.languages));
// chat templates: Slack not configured → read-only
r = await c('GET', C + '/slack', null, t); ck('Slack templates visible with configured:false', r.s === 200 && r.data.configured === false && r.data.modules.length > 5, r.s + ' ' + r.code + ' ' + show({ c: r.data?.configured, m: r.data?.modules?.length }));
r = await c('PUT', C + '/slack/support.ticket.created/vi', { text: 'Ticket {ticketNumber}' }, t); ck('PUT before Slack is configured → SETTINGS_022', r.code === 'SETTINGS_022', r.s + ' ' + r.code + ' ' + r.msg);
r = await c('GET', C + '/slack/crm.lead.emailed', null, t); ck('private email body never offered to chat', r.s === 200 && !r.data.variables.includes('body') && !r.data.variables.includes('subject'), show(r.data?.variables));
r = await c('GET', C + '/telegram/recruitment.offer.letter_sent', null, t); ck('letterText never offered to chat', r.s === 200 && !r.data.variables.includes('letterText'), show(r.data?.variables));
// configure Telegram with all its events OFF (no egress), then customise its text
await c('PUT', '/settings/integrations/telegram/events', { enabledEventTypes: [] }, t);
r = await c('PUT', '/settings/integrations/telegram', { enabled: true, botToken: '123456:' + 'A'.repeat(30), chatId: '-100123' }, t); ck('Telegram configured (events off)', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
r = await c('PUT', C + '/telegram/support.ticket.created/vi', { text: 'Ticket mới {ticketNumber}: {subject}' }, t); ck('Telegram template saved once configured', r.s === 200 && r.data.languages.find(z => z.lang === 'vi')?.customised === true, r.s + ' ' + r.code + ' ' + r.msg);
r = await c('PUT', C + '/telegram/support.ticket.created/vi', { text: 'x'.repeat(1001) }, t); ck('text over 1000 chars → SETTINGS_024', r.code === 'SETTINGS_024', r.s + ' ' + r.code);
r = await c('POST', C + '/telegram/support.ticket.created/vi/preview', null, t); ck('chat preview', r.s === 200 && r.data.text === 'Ticket mới [ticketNumber]: [subject]', r.s + ' ' + show(r.data));
// delivery still works with an override (unresolvable SMTP → a real attempt, SMTP_ERROR)
await c('PUT', '/settings/company/email', { enabled: true, host: 'smtp.no-such-host.invalid', port: 587, username: 'test', password: 'fake-smtp-pass', encryption: 'TLS', fromAddress: 'noreply@example.com', fromName: 'Test Co' }, t);
const cat = (await c('POST', '/support/ticket-categories', { name: 'General', color: '#123456' }, t)).data;
await c('POST', '/support/tickets', { accountType: 'CUSTOM', name: 'Test Customer', email: 'cust.' + x.toLowerCase() + '@example.com', categoryId: Number(cat.id), subject: 'Need help', description: 'x' }, t);
let ds = []; for (let i = 0; i < 25; i++) { ds = (await c('GET', '/notifications/deliveries?eventType=support.ticket.created', null, t)).data?.content || []; if (ds.some(z => z.channel === 'EMAIL' && z.attempts > 0)) break; await sleep(2000); }
const em = ds.find(z => z.channel === 'EMAIL'), tg = ds.find(z => z.channel === 'TELEGRAM');
ck('override email path: real attempt SMTP_ERROR (no external send)', em && /SMTP|CONNECTION|TIMEOUT/.test(em.lastErrorCode || ''), show(em));
ck('Telegram row SKIPPED by the per-event switch (no egress)', tg && tg.status === 'SKIPPED' && tg.lastErrorCode === 'CHANNEL_EVENT_DISABLED', show(tg));
ck('nothing SENT', ds.every(z => z.status !== 'SENT'), show(ds.map(z => z.status)));
// reset + permissions
r = await c('DELETE', E + '/SUPPORT_TICKET_CREATED/vi', null, t); ck('reset → customised false', r.s === 200 && r.data.languages.find(z => z.lang === 'vi')?.customised === false, r.s + ' ' + show(r.data?.languages));
const roles = (await c('GET', '/iam/roles', null, t)).data; const empRole = roles.find(z => z.code === 'EMPLOYEE'); const un = 'tp.emp.' + x.toLowerCase(); const p0 = pw();
const eu = await c('POST', '/iam/users', { username: un, email: un + '@example.com', fullName: 'TP Employee', roleIds: [Number(empRole.id)], password: p0 }, t); const p1 = pw();
await c('POST', '/auth/initial-password', { username: un, currentPassword: eu.data?.temporaryPassword || p0, newPassword: p1 });
const et = (await c('POST', '/auth/login', { username: un, password: p1 })).data?.accessToken;
r = await c('PUT', E + '/SUPPORT_TICKET_CREATED/vi', { subject: 's', body: 'b' }, et); ck('employee edits a template → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', C + '/slack', null, et); ck('employee chat templates → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', E); ck('no token → 401', r.s === 401, r.s);
console.log(`\n${n - f}/${n} passed`);

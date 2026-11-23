// Google Calendar sync paths (no egress)

const num = v => Number(v); const show = d => JSON.stringify(d)?.slice(0, 280); const sleep = ms => new Promise(res => setTimeout(res, ms));
const today = new Date().toISOString().slice(0, 10); const later = new Date(Date.now() + 40 * 864e5).toISOString().slice(0, 10);
const cal = async () => (await c('GET', '/notifications/deliveries?channel=GOOGLE_CALENDAR&size=100', null, t)).data?.content || [];
const hcat = (await c('POST', '/org/holiday-categories', { name: 'Cal ' + x, color: '#22C55E' }, t)).data;
const mkHoliday = async (name, sync) => (await c('POST', '/org/holidays', { name, categoryId: num(hcat?.id), startDate: later, endDate: later, isPaid: true, calendarSync: sync }, t));
// 1. holiday flag round-trip, integration not configured → no calendar row
let r = await mkHoliday('Sync day', true); const h1 = r.data;
ck('holiday saved with calendarSync=true', r.s === 200 && h1.calendarSync === true, r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(h1));
await sleep(12000);
ck('integration off → no GOOGLE_CALENDAR row', (await cal()).length === 0, show(await cal()));
// 2. integration on with a service account whose key is NOT valid PKCS8 → INVALID_CONFIG, no network call
const badSa = JSON.stringify({ type: 'service_account', client_email: 'erp-test@fake-project.iam.gserviceaccount.com', private_key: '-----BEGIN PRIVATE KEY-----\nnot*valid*base64*at*all\n-----END PRIVATE KEY-----\n', token_uri: 'https://oauth2.googleapis.com/token' });
r = await c('PUT', '/settings/integrations/google-calendar', { enabled: true, calendarId: 'team.calendar@example.invalid', serviceAccountJson: badSa }, t);
ck('Google Calendar integration saved (key file hidden)', r.s === 200 && r.data.hasServiceAccountJson === true && !show(r.data).includes('PRIVATE KEY'), r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(r.data));
// flag off → still no row
await mkHoliday('No sync day', false);
await sleep(12000);
ck('calendarSync=false → no GOOGLE_CALENDAR row', (await cal()).length === 0, show(await cal()));
// flag on → a row that fails INVALID_CONFIG without any call (bad key is rejected before UrlGuard/HTTP)
const h3 = (await mkHoliday('Bad key day', true)).data;
let rows = []; for (let i = 0; i < 25; i++) { rows = await cal(); if (rows.some(z => z.attempts > 0)) break; await sleep(2000); }
const g = rows.find(z => z.eventType === 'employee.holiday.created');
ck('calendarSync=true → GOOGLE_CALENDAR row FAILED INVALID_CONFIG, 1 attempt, target masked', g && g.status === 'FAILED' && g.lastErrorCode === 'INVALID_CONFIG' && g.attempts === 1 && !g.target.includes('team.calendar@example.invalid') && g.target.startsWith('team'), show(rows.map(z => [z.eventType, z.status, z.lastErrorCode, z.attempts, z.target])));
// update + delete events also routed to the calendar only
await c('PUT', '/org/holidays/' + h3.id, { name: 'Bad key day 2', categoryId: num(hcat?.id), startDate: later, endDate: later, isPaid: true, calendarSync: true }, t);
await c('DELETE', '/org/holidays/' + h3.id, null, t);
for (let i = 0; i < 25; i++) { rows = await cal(); if (['employee.holiday.updated', 'employee.holiday.deleted'].every(tp => rows.some(z => z.eventType === tp && z.attempts > 0))) break; await sleep(2000); }
ck('holiday updated + deleted → calendar rows (INVALID_CONFIG)', ['employee.holiday.updated', 'employee.holiday.deleted'].every(tp => rows.some(z => z.eventType === tp && z.lastErrorCode === 'INVALID_CONFIG')), show(rows.map(z => [z.eventType, z.lastErrorCode])));
r = await c('GET', '/notifications/deliveries?eventType=employee.holiday.updated&size=50', null, t);
ck('holiday.updated is calendar-only (no email/webhook/chat rows)', (r.data?.content || []).every(z => z.channel === 'GOOGLE_CALENDAR'), show(r.data?.content?.map(z => z.channel)));
// 3. invoice flag round-trip
const cust = (await c('POST', '/accounting/customers', { companyName: 'Cal Buyer ' + x, contactPerson: 'Test', email: 'buyer@example.com' }, t)).data;
const unit = (await c('POST', '/inventory/units', { name: 'Piece' }, t)).data;
const icat = (await c('POST', '/inventory/categories', { name: 'Svc', color: '#3B82F6' }, t)).data;
const svc = (await c('POST', '/inventory/items', { type: 'SERVICE', name: 'Consulting', generateSku: true, categoryId: Number(icat.id), unitId: Number(unit.id), salePrice: 100, purchasePrice: 0 }, t)).data;
r = await c('POST', '/sales/invoices', { customerId: Number(cust.id), invoiceType: 'SERVICE', docDate: today, dueDate: later, paymentTerms: 'Net 30', syncToGoogleCalendar: true, lines: [{ itemId: Number(svc.id) }] }, t); const inv = r.data?.document;
ck('invoice saved with syncToGoogleCalendar=true', r.s === 200 && r.data?.syncToGoogleCalendar === true, r.s + ' ' + r.code + ' ' + r.msg + ' ' + show({ s: r.data?.syncToGoogleCalendar }));
await c('POST', '/sales/invoices/' + inv.id + '/post', null, t);
for (let i = 0; i < 25; i++) { rows = await cal(); if (rows.some(z => z.eventType === 'sales.invoice.posted' && z.attempts > 0)) break; await sleep(2000); }
ck('posted invoice → GOOGLE_CALENDAR row (INVALID_CONFIG, no call)', rows.some(z => z.eventType === 'sales.invoice.posted' && z.lastErrorCode === 'INVALID_CONFIG'), show(rows.filter(z => z.eventType === 'sales.invoice.posted')));
// isolation + no SENT
r = await c('GET', '/notifications/deliveries?channel=GOOGLE_CALENDAR', null, B.t); ck("company B sees none of A's calendar rows", (r.data?.content || []).length === 0, show(r.data?.content?.length));
ck('no calendar delivery SENT (nothing left the machine)', (await cal()).every(z => z.status !== 'SENT'), '');
r = await c('GET', '/settings/integrations', null, t); ck('integration overview never returns the key file', !show(r.data).includes('PRIVATE KEY') && !JSON.stringify(r.data).includes('not*valid'), show(r.data?.googleCalendar));
await c('DELETE', '/settings/integrations/google-calendar', null, t);
console.log(`\n${n - f}/${n} passed`);

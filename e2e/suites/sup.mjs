// support tickets, public portal, KB/FAQ, messenger

const show = d => JSON.stringify(d)?.slice(0, 220); const S = '/support'; const P = '/public/support/';
const csv = async (pa, text, tk) => { const fd = new FormData(); fd.append('file', new Blob([text], { type: 'text/csv' }), 'import.csv'); const r = await fetch(G + pa, { method: 'POST', headers: { 'Accept-Language': 'en', Authorization: 'Bearer ' + tk }, body: fd }); const j = await r.json().catch(() => ({})); return { s: r.status, code: j.code, msg: j.message, data: j.data }; };
const mkUser = async (role, tag) => { const roles = (await c('GET', '/iam/roles', null, t)).data; const ro = roles.find(z => z.code === role); const un = 'sp.' + tag + '.' + x.toLowerCase(); const p0 = pw(); const u = await c('POST', '/iam/users', { username: un, email: un + '@example.com', fullName: 'Support ' + tag, roleIds: [Number(ro.id)], password: p0 }, t); const p1 = pw(); await c('POST', '/auth/initial-password', { username: un, currentPassword: u.data?.temporaryPassword || p0, newPassword: p1 }); return { id: u.data?.user?.id ?? u.data?.id, t: (await c('POST', '/auth/login', { username: un, password: p1 })).data?.accessToken }; };
// portal + categories
const slug = 'sp-' + x.toLowerCase();
let r = await c('PUT', S + '/portal/settings', { slug, enabled: true, brandName: 'Test Support ' + x, content: { supportInformation: { responseTime: '24h', businessHours: [{ day: 'MON', open: true }] }, contactInformation: { email: 'help@example.com' }, quickLinks: [{ title: 'Docs', icon: 'book', link: 'https://example.com', order: 1 }] } }, t);
ck('portal settings', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
const cat = (await c('POST', S + '/ticket-categories', { name: 'Billing', color: '#336699' }, t)).data; ck('ticket category', !!cat?.id, show(cat));
r = await c('POST', S + '/portal/pages', { title: 'Terms', slug: 'terms', contents: '<p>Fake terms</p>', description: 'x', showInFooter: true }, t); ck('custom page', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
r = await c('GET', P + slug); ck('public portal info with category + page', r.s === 200 && r.data.brandName.startsWith('Test Support') && r.data.ticketCategories.length === 1 && r.data.pages.length === 1, r.s + ' ' + r.code + ' ' + show(r.data));
r = await c('GET', P + 'no-such-portal-' + x.toLowerCase()); ck('unknown portal → 404 SUP_009', r.s === 404 && r.code === 'SUP_009', r.s + ' ' + r.code);
// tickets
r = await c('POST', S + '/tickets', { accountType: 'CLIENT', name: 'Test Client', email: 'client.' + x.toLowerCase() + '@example.com', categoryId: Number(cat.id), subject: 'Invoice question', description: '<p>Hi</p><script>alert(1)</script>', note: 'internal only' }, t);
const tk = r.data?.ticket?.ticket; ck('admin ticket TKT-000001, script stripped', r.s === 200 && tk?.ticketNumber === 'TKT-000001' && !show(r.data).includes('script'), r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(r.data));
const pubEmail = 'cust.' + x.toLowerCase() + '@example.com';
r = await c('POST', P + slug + '/tickets', { name: 'Test Customer', email: pubEmail, categoryId: Number(cat.id), subject: 'Cannot log in', description: 'Help please' });
const pt = r.data; ck('public ticket → TKT-000002 + token', r.s === 200 && pt.ticketNumber === 'TKT-000002' && pt.trackingToken?.length >= 32, r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(pt));
r = await c('POST', P + slug + '/tickets/track', { ticketNumber: 'TKT-000001', email: 'client.' + x.toLowerCase() + '@example.com' }); ck('track admin ticket hides note', r.s === 200 && !show(r.data).includes('internal only'), r.s + ' ' + show(r.data));
r = await c('POST', P + slug + '/tickets/track', { ticketNumber: pt.ticketNumber, email: 'wrong@example.com' }); ck('track wrong email → 404 SUP_010', r.s === 404 && r.code === 'SUP_010', r.s + ' ' + r.code);
const list = (await c('GET', S + '/tickets?keyword=Cannot', null, t)).data; const ptId = list?.content?.[0]?.id;
r = await c('POST', S + '/tickets/' + ptId + '/replies', { description: 'Try resetting' }, t); ck('staff reply', r.s === 200 && r.data.senderType === 'STAFF', r.s + ' ' + r.code + ' ' + r.msg);
r = await c('POST', P + slug + '/tickets/reply', { trackingToken: pt.trackingToken, description: 'Thanks, works' }); ck('customer reply via token, conversation of 2', r.s === 200 && r.data.replies.length === 2, r.s + ' ' + r.code + ' ' + show(r.data?.replies));
r = await c('PUT', S + '/tickets/' + ptId + '/status', { status: 'ON_HOLD' }, t); ck('status ON_HOLD', r.s === 200 && r.data.status === 'ON_HOLD', r.s + ' ' + r.code + ' ' + show(r.data));
r = await c('GET', S + '/tickets/status-counts', null, t); ck('status counts all 2 / onHold 1', r.data?.all === 2 && r.data?.onHold === 1, show(r.data));
r = await c('GET', S + '/tickets/status-counts?keyword=Cannot', null, t); ck('status counts follow the keyword filter: 1 / onHold 1', r.data?.all === 1 && r.data?.onHold === 1 && r.data?.inProgress === 0, show(r.data));
r = await c('GET', S + '/tickets/status-counts?accountType=CLIENT', null, t); ck('status counts follow accountType: CLIENT → 1 in progress', r.data?.all === 1 && r.data?.inProgress === 1, show(r.data));
await c('PUT', S + '/tickets/' + ptId + '/status', { status: 'CLOSED' }, t);
r = await c('POST', S + '/tickets/' + ptId + '/replies', { description: 'late' }, t); ck('reply to closed → SUP_006', r.code === 'SUP_006', r.s + ' ' + r.code);
r = await c('POST', P + slug + '/tickets/reply', { ticketNumber: pt.ticketNumber, email: pubEmail, description: 'again' }); ck('public reply to closed → SUP_006', r.code === 'SUP_006', r.s + ' ' + r.code);
r = await c('GET', S + '/tickets?status=CLOSED', null, t); ck('Closed tab', r.data?.content?.length === 1, show(r.data?.content?.map(z => z.status)));
// KB + FAQ + CSV
const kc = (await c('POST', S + '/kb-categories', { title: 'Getting started' }, t)).data;
r = await c('POST', S + '/kb', { categoryId: Number(kc?.id), title: 'Reset password', description: 'How to reset', content: '<p>Click reset</p>', published: true }, t); ck('KB article', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
r = await csv(S + '/faqs/import', 'question,answer\nA,B\n', t); ck('FAQ CSV bad header → 400', r.s === 400, r.s + ' ' + r.code + ' ' + r.msg);
r = await csv(S + '/faqs/import', 'title,description\n"Refund, how?",Within 7 days\n,missing title\n', t); ck('FAQ CSV row error → nothing saved', r.s === 200 && r.data.created === 0 && r.data.errors.length === 1, r.s + ' ' + show(r.data));
r = await csv(S + '/faqs/import', 'title,description\n"Refund, how?",Within 7 days\nOpen hours,9 to 5\n', t); ck('FAQ CSV imports 2', r.s === 200 && r.data.created === 2, r.s + ' ' + show(r.data));
r = await csv(S + '/kb/import', 'title,description,category,content\nInstall,Setup steps,Getting started,<p>Run it</p>\n', t); ck('KB CSV import', r.s === 200 && r.data.created === 1, r.s + ' ' + r.code + ' ' + show(r.data));
const fq = (await c('POST', S + '/faqs', { title: 'Published FAQ', description: '<p>Yes</p>', published: true }, t)).data;
r = await c('GET', P + slug + '/faqs'); ck('public FAQ shows published only', r.s === 200 && show(r.data).includes('Published FAQ') && !show(r.data).includes('Refund'), r.s + ' ' + show(r.data));
r = await c('GET', P + slug + '/kb'); ck('public KB shows published only', r.s === 200 && show(r.data).includes('Reset password') && !show(r.data).includes('Install'), r.s + ' ' + show(r.data));
// contact (public writes: 10/min, burst 5 — let one token refill)
await new Promise(res => setTimeout(res, 13000));
r = await c('POST', P + slug + '/contact', { name: 'Test Visitor', email: 'visitor@example.com', subject: 'Hello', message: 'Fake message' }); ck('public contact', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
r = await c('GET', S + '/contacts', null, t); const ct = r.data?.content?.[0]; ck('admin sees contact', ct?.subject === 'Hello', show(r.data));
r = await c('DELETE', S + '/contacts/' + ct?.id, null, t); ck('delete contact', r.s === 200, r.s + ' ' + r.code);
// messenger: two employees (no IAM_USER_VIEW)
const e1 = await mkUser('EMPLOYEE', 'e1'), e2 = await mkUser('EMPLOYEE', 'e2');
r = await c('GET', '/iam/colleagues?keyword=Support', null, e1.t); ck('employee lists colleagues (no IAM_USER_VIEW)', r.s === 200 && r.data.content.some(z => z.id === String(e2.id)) && !r.data.content.some(z => z.id === String(e1.id)) && !('email' in (r.data.content[0] || {})), r.s + ' ' + r.code + ' ' + show(r.data));
r = await c('POST', '/messenger/conversations/' + e2.id + '/messages', { body: 'Hi colleague' }, e1.t); ck('employee sends a message', r.s === 200 && r.data.mine === true, r.s + ' ' + r.code + ' ' + r.msg);
await c('POST', '/messenger/conversations/' + e2.id + '/messages', { body: 'Second one' }, e1.t);
r = await c('GET', '/messenger/unread-count', null, e2.t); ck('receiver unread 2', r.data?.total === 2, show(r.data));
r = await c('GET', '/messenger/conversations', null, e2.t); ck('conversation with unread 2', r.data?.content?.[0]?.unreadCount === 2 && r.data.content[0].userId === String(e1.id), show(r.data));
r = await c('POST', '/messenger/conversations/' + e1.id + '/read', null, e2.t); ck('mark read', r.data?.marked === 2, show(r.data));
r = await c('GET', '/messenger/unread-count', null, e2.t); ck('unread 0', r.data?.total === 0, show(r.data));
r = await c('PUT', '/messenger/favorites/' + e1.id, null, e2.t); ck('favorite', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
r = await c('GET', '/messenger/favorites', null, e2.t); ck('favorites list', r.data?.length === 1 || r.data?.content?.length === 1, show(r.data));
r = await c('POST', '/messenger/conversations/' + e1.id + '/messages', { body: 'self' }, e1.t); ck('message myself → SUP_030', r.code === 'SUP_030', r.s + ' ' + r.code);
const bAdminId = (await c('GET', '/iam/users', null, B.t)).data?.content?.[0]?.id;
r = await c('POST', '/messenger/conversations/' + bAdminId + '/messages', { body: 'cross' }, e1.t); ck('message a user of company B → SUP_029', r.code === 'SUP_029', r.s + ' ' + r.code);
r = await c('GET', '/iam/colleagues/' + bAdminId, null, e1.t); ck('colleague of company B → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('GET', '/messenger/conversations/' + e1.id + '/messages', null, B.t); ck('company B reads A conversation → empty or refused', r.s !== 200 || (r.data?.content?.length ?? 0) === 0, r.s + ' ' + show(r.data));
// isolation & permissions
r = await c('GET', S + '/tickets/' + ptId, null, B.t); ck('company B reads A ticket → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('PUT', S + '/tickets/' + ptId + '/status', { status: 'IN_PROGRESS' }, B.t); ck('company B changes A ticket → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('DELETE', S + '/faqs/' + fq?.id, null, B.t); ck('company B deletes A FAQ → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('GET', S + '/tickets', null, e1.t); ck('employee (VIEW_OWN) lists tickets: none of the admin ones', r.s === 200 && r.data.content.length === 0, r.s + ' ' + r.code + ' ' + r.data?.content?.length); r = await c('GET', S + '/tickets/' + ptId, null, e1.t); ck("employee reads the admin's ticket → 404", r.s === 404, r.s + ' ' + r.code);
r = await c('GET', '/messenger/unread-count'); ck('no token → 401', r.s === 401, r.s);
console.log(`\n${n - f}/${n} passed`);

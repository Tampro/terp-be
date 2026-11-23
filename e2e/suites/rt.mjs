// realtime SSE stream and in-app feed

const show = d => JSON.stringify(d)?.slice(0, 260); const sleep = ms => new Promise(res => setTimeout(res, ms));
const tomorrow = new Date(Date.now() + 864e5).toISOString().slice(0, 10);
const roles = (await c('GET', '/iam/roles', null, t)).data; const empRole = roles.find(z => z.code === 'EMPLOYEE');
const mkUser = async tag => { const un = 'rt.' + tag + '.' + x.toLowerCase(); const p0 = pw(); const u = await c('POST', '/iam/users', { username: un, email: un + '@example.com', fullName: 'RT ' + tag, roleIds: [Number(empRole.id)], password: p0 }, t); const p1 = pw(); await c('POST', '/auth/initial-password', { username: un, currentPassword: u.data?.temporaryPassword || p0, newPassword: p1 }); return { id: Number(u.data?.user?.id ?? u.data?.id), t: (await c('POST', '/auth/login', { username: un, password: p1 })).data?.accessToken }; };
const E1 = await mkUser('e1'), E2 = await mkUser('e2');
// open a stream and collect parsed events
const openStream = (tk) => { const st = { events: [], raw: '', status: 0, open: true, hb: 0 }; const ac = new AbortController(); st.abort = () => ac.abort();
  (async () => { try { const res = await fetch(G + '/notifications/me/stream', { headers: { Authorization: 'Bearer ' + tk, Accept: 'text/event-stream' }, signal: ac.signal }); st.status = res.status; st.type = res.headers.get('content-type'); const rd = res.body.getReader(); const dec = new TextDecoder(); let buf = '';
    for (;;) { const { value, done } = await rd.read(); if (done) break; buf += dec.decode(value, { stream: true }); st.raw += dec.decode(value); let i; while ((i = buf.indexOf('\n\n')) >= 0) { const blk = buf.slice(0, i); buf = buf.slice(i + 2); if (blk.startsWith(':')) { st.hb++; continue; } const ev = (blk.match(/^event:(.*)$/m) || [])[1]?.trim(); const data = blk.split('\n').filter(l => l.startsWith('data:')).map(l => l.slice(5)).join('\n'); let d; try { d = JSON.parse(data); } catch { d = data; } st.events.push({ ev, d, at: Date.now() }); } } } catch (e) { if (e.name !== 'AbortError') st.err = String(e); } st.open = false; })(); return st; };
const s1 = openStream(E1.t), s2 = openStream(E2.t), s0 = openStream(B.t);
await sleep(3000);
ck('stream opens through the gateway as text/event-stream', s1.status === 200 && (s1.type || '').includes('text/event-stream'), s1.status + ' ' + s1.type);
ck('initial unread event on connect', s1.events.some(e => e.ev === 'unread' && typeof e.d?.count === 'number'), show(s1.events));
// chat: E2 → E1
let r = await c('POST', '/messenger/conversations/' + E1.id + '/messages', { body: 'Top secret lunch plans' }, E2.t); ck('E2 sends E1 a chat message', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
// CRM task assigned to E1 (lead owner)
const pipe = (await c('POST', '/crm/pipelines', { name: 'RT ' + x }, t)).data;
await c('POST', '/crm/pipelines/' + pipe.id + '/stages', { kind: 'LEAD', name: 'New' }, t); await c('POST', '/crm/pipelines/' + pipe.id + '/stages', { kind: 'DEAL', name: 'Open' }, t);
const lead = (await c('POST', '/crm/leads', { pipelineId: Number(pipe.id), name: 'RT Lead', email: 'rt@example.com', subject: 'x', ownerUserId: E1.id }, t)).data; const leadId = lead?.lead?.id ?? lead?.id;
r = await c('POST', '/crm/leads/' + leadId + '/tasks', { name: 'Call the client', date: tomorrow, priority: 'HIGH', status: 'OPEN' }, t); ck('CRM task on a lead owned by E1', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
for (let i = 0; i < 20 && !(s1.events.some(e => e.ev === 'chat') && s1.events.some(e => e.ev === 'notification' && e.d?.eventType === 'crm.task.created')); i++) await sleep(1500);
const chat = s1.events.find(e => e.ev === 'chat'); const notes = s1.events.filter(e => e.ev === 'notification');
ck('E1 gets a chat event {fromUserId, messageId}', chat && String(chat.d.fromUserId) === String(E2.id) && chat.d.messageId, show(chat));
ck('chat message body never appears on the stream', !s1.raw.includes('Top secret'), '');
ck('E1 gets notification for the chat ("New message from …")', notes.some(n => n.d.eventType === 'support.chat.message_sent' && /message|Tin nhắn/i.test(n.d.title)), show(notes.map(n => [n.d.eventType, n.d.title])));
ck('E1 gets notification for the CRM task', notes.some(n => n.d.eventType === 'crm.task.created'), show(notes.map(n => n.d.eventType)));
ck('unread pushed after new rows', s1.events.filter(e => e.ev === 'unread').length >= 2, show(s1.events.filter(e => e.ev === 'unread').map(e => e.d)));
ck("E2's stream got no event for E1", !s2.events.some(e => e.ev === 'notification' || e.ev === 'chat'), show(s2.events.map(e => e.ev)));
ck("company B's stream got nothing", !s0.events.some(e => e.ev === 'notification' || e.ev === 'chat'), show(s0.events.map(e => e.ev)));
// REST side
r = await c('GET', '/notifications/me/unread-count', null, E1.t); const cnt = r.data?.count; ck('unread count matches (≥ 2)', r.s === 200 && cnt >= 2, r.s + ' ' + show(r.data));
r = await c('GET', '/notifications/me?unreadOnly=true', null, E1.t); const mine = r.data?.content || []; ck('feed lists my unread items', mine.length === cnt && mine.every(z => z.read === false), show(mine.map(z => z.title)));
const one = mine[0];
r = await c('POST', '/notifications/me/' + one.id + '/read', null, E2.t); ck("E2 marks E1's notification → 404 NTF_006", r.s === 404 && r.code === 'NTF_006', r.s + ' ' + r.code);
r = await c('POST', '/notifications/me/' + one.id + '/read', null, E1.t); ck('mark one read', r.s === 200 && r.data.read === true, r.s + ' ' + show(r.data));
r = await c('POST', '/notifications/me/read-all', null, E1.t); ck('read-all', r.s === 200 && r.data.marked === cnt - 1, r.s + ' ' + show(r.data));
r = await c('GET', '/notifications/me/unread-count', null, E1.t); ck('unread 0', r.data?.count === 0, show(r.data));
r = await c('GET', '/notifications/me', null, sa); ck('super admin (no company) → 403 NTF_007', r.s === 403 && r.code === 'NTF_007', r.s + ' ' + r.code);
r = await c('GET', '/notifications/me/unread-count'); ck('no token → 401', r.s === 401, r.s);
// stream stays open past the gateway's 15 s timeout and gets a heartbeat
const t0 = s1.events[0]?.at ?? Date.now(); await sleep(Math.max(0, 25000 - (Date.now() - t0)));
ck('stream still open after > 20 s with a heartbeat (gateway timeout lifted)', s1.open && s1.hb >= 1, 'open=' + s1.open + ' hb=' + s1.hb + ' err=' + s1.err);
r = await fetch(G + '/notifications/me/stream?access_token=' + E1.t); ck('token in the query string is not accepted → 401', r.status === 401, String(r.status));
s1.abort(); s2.abort(); s0.abort();
console.log(`\n${n - f}/${n} passed`);
process.exit(f ? 1 : 0);

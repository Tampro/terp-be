// contract signatures incl. concurrent signing

const show = d => JSON.stringify(d)?.slice(0, 260);
const today = new Date().toISOString().slice(0, 10); const later = new Date(Date.now() + 365 * 864e5).toISOString().slice(0, 10);
// a real 1x1 PNG as the hand-drawn signature
const PNG = Buffer.concat([Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]), Buffer.from('0000000D49484452000000010000000108060000001F15C4890000000A49444154789C6300010000050001', 'hex'), Buffer.from('0D0A2DB40000000049454E44AE426082', 'hex')]);
const dataUrl = 'data:image/png;base64,' + PNG.toString('base64');
const raw = async (pa, tk) => { const res = await fetch(G + pa, { headers: { Authorization: 'Bearer ' + tk } }); return { s: res.status, type: res.headers.get('content-type'), buf: Buffer.from(await res.arrayBuffer()) }; };
const roles = (await c('GET', '/iam/roles', null, t)).data; const R = code => Number(roles.find(z => z.code === code)?.id);
const mkUser = async (tag, roleIds) => { const un = 'sg.' + tag + '.' + x.toLowerCase(); const p0 = pw(); const u = await c('POST', '/iam/users', { username: un, email: un + '@example.com', fullName: 'Sig ' + tag, roleIds, password: p0 }, t); const p1 = pw(); await c('POST', '/auth/initial-password', { username: un, currentPassword: u.data?.temporaryPassword || p0, newPassword: p1 }); return { id: Number(u.data?.user?.id ?? u.data?.id), t: (await c('POST', '/auth/login', { username: un, password: p1 })).data?.accessToken }; };
const C1 = await mkUser('c1', [R('CLIENT')]); const C2 = await mkUser('c2', [R('CLIENT')]);
const custA = (await c('POST', '/accounting/customers', { companyName: 'Sign Client ' + x, contactPerson: 'Test', email: 'c1@example.com', userId: C1.id }, t)).data;
const custB = (await c('POST', '/accounting/customers', { companyName: 'Other Client ' + x, contactPerson: 'Test', email: 'c2@example.com', userId: C2.id }, t)).data;
const ctype = (await c('POST', '/crm/contract-types', { name: 'Service ' + x, active: true }, t)).data;
const form = { subject: 'Support plan', customerId: Number(custA.id), contractTypeId: Number(ctype.id), value: 12000, startDate: today, endDate: later, description: '<p>Fake agreement text.</p>' };
let r = await c('POST', '/crm/contracts', form, t); const con = r.data?.contract ?? r.data; const id = con?.id;
ck('contract created PENDING', r.s === 200 && con.status === 'PENDING', r.s + ' ' + r.code + ' ' + r.msg + ' ' + con?.status);
// validation
r = await c('POST', '/crm/contracts/' + id + '/signatures', { signatureDataUrl: 'data:image/jpeg;base64,' + PNG.toString('base64'), signerName: 'Test Signer' }, t); ck('not a PNG data URL → CTR_013', r.code === 'CTR_013', r.s + ' ' + r.code);
r = await c('POST', '/crm/contracts/' + id + '/signatures', { signatureDataUrl: 'data:image/png;base64,' + Buffer.from('GIF89a not a png at all').toString('base64'), signerName: 'Test Signer' }, t); ck('wrong magic bytes → CTR_013', r.code === 'CTR_013', r.s + ' ' + r.code);
// company signs
r = await c('POST', '/crm/contracts/' + id + '/signatures', { signatureDataUrl: dataUrl, signerName: 'Test Signer' }, t);
const cs = (r.data || []).find(z => z.signerType === 'COMPANY');
ck('company signs: hash stored, content matches, IP recorded', r.s === 200 && cs?.contentMatches === true && /^[0-9a-f]{64}$/.test(cs.contentSha256 || '') && !!cs.ip, r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(cs));
r = await c('GET', '/crm/contracts/' + id, null, t); ck('still PENDING after one signature', (r.data?.contract ?? r.data)?.status === 'PENDING', show((r.data?.contract ?? r.data)?.status));
r = await c('POST', '/crm/contracts/' + id + '/signatures', { signatureDataUrl: dataUrl, signerName: 'Again' }, t); ck('company signs again → CTR_016', r.code === 'CTR_016', r.s + ' ' + r.code);
r = await c('PUT', '/crm/contracts/' + id, { ...form, value: 99999 }, t); ck('edit a signed contract → CTR_018', r.code === 'CTR_018', r.s + ' ' + r.code);
r = await c('DELETE', '/crm/contracts/' + id, null, t); ck('delete a signed contract → CTR_018', r.code === 'CTR_018', r.s + ' ' + r.code);
// client of another customer can't see or sign
r = await c('POST', '/portal/client/contracts/' + id + '/sign', { signatureDataUrl: dataUrl }, C2.t); ck("other customer's client signs → 404", r.s === 404, r.s + ' ' + r.code);
r = await c('GET', '/portal/client/contracts/' + id + '/signatures', null, C2.t); ck("other customer's client reads signatures → 404", r.s === 404, r.s + ' ' + r.code);
// client signs on the portal
r = await c('POST', '/portal/client/contracts/' + id + '/sign', { signatureDataUrl: dataUrl, signerName: 'Fake Override' }, C1.t);
ck('client signs on the portal', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
r = await c('GET', '/portal/client/contracts/' + id + '/signatures', null, C1.t); const ps = r.data || [];
ck('portal lists both signatures without IP / user agent / email', ps.length === 2 && ps.every(z => !('ip' in z) && !('userAgent' in z) && !('signerEmail' in z)) && ps.every(z => z.contentMatches === true), show(ps));
ck('client name taken from the token, not the body', ps.find(z => z.signerType === 'CLIENT')?.signerName !== 'Fake Override', show(ps.find(z => z.signerType === 'CLIENT')));
r = await c('GET', '/crm/contracts/' + id, null, t); ck('both signed → ACTIVE', (r.data?.contract ?? r.data)?.status === 'ACTIVE', show((r.data?.contract ?? r.data)?.status));
r = await c('POST', '/portal/client/contracts/' + id + '/sign', { signatureDataUrl: dataUrl }, C1.t); ck('signing an ACTIVE contract → refused', r.s === 400, r.s + ' ' + r.code);
// images + PDFs
for (const side of ['COMPANY', 'CLIENT']) { const im = await raw('/crm/contracts/' + id + '/signatures/' + side + '/image', t); ck(side + ' signature image is the uploaded PNG', im.s === 200 && im.type?.includes('image/png') && im.buf.equals(PNG), im.s + ' ' + im.type + ' ' + im.buf.length); }
const pdf = await raw('/crm/contracts/' + id + '/pdf', t); ck('company PDF', pdf.s === 200 && pdf.buf.subarray(0, 5).toString() === '%PDF-', pdf.s + ' ' + pdf.type);
const ppdf = await raw('/portal/client/contracts/' + id + '/pdf', C1.t); ck('portal PDF', ppdf.s === 200 && ppdf.buf.subarray(0, 5).toString() === '%PDF-', ppdf.s + ' ' + ppdf.type);
const opdf = await raw('/portal/client/contracts/' + id + '/pdf', C2.t); ck("other customer's PDF → 404", opdf.s === 404, String(opdf.s));
// concurrent signatures (row lock): both sides at once, 5 contracts → all ACTIVE
let allActive = true;
for (let k = 0; k < 5; k++) {
  const cc = (await c('POST', '/crm/contracts', { ...form, subject: 'Race ' + k }, t)).data; const cid = (cc?.contract ?? cc)?.id;
  await Promise.all([c('POST', '/crm/contracts/' + cid + '/signatures', { signatureDataUrl: dataUrl, signerName: 'Test Signer' }, t), c('POST', '/portal/client/contracts/' + cid + '/sign', { signatureDataUrl: dataUrl }, C1.t)]);
  const st = (await c('GET', '/crm/contracts/' + cid, null, t)).data; if ((st?.contract ?? st)?.status !== 'ACTIVE') allActive = false;
}
ck('simultaneous company + client signatures → ACTIVE every time (5 runs)', allActive, String(allActive));
// isolation
r = await c('GET', '/crm/contracts/' + id + '/signatures', null, B.t); ck('company B reads A signatures → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('GET', '/crm/contracts/' + id + '/signatures'); ck('no token → 401', r.s === 401, r.s);
console.log(`\n${n - f}/${n} passed`);

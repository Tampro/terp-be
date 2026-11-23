// S3 storage driver against local SeaweedFS (flips the platform driver, restores LOCAL)
import { spawnSync } from 'child_process';

const show = d => JSON.stringify(d)?.slice(0, 260); const sleep = ms => new Promise(res => setTimeout(res, ms));
// read-only listing inside the MinIO container; credentials come from the container's own env, never printed
const mcLs = () => { const r = spawnSync('docker', ['exec', 'erp-seaweedfs-1', 'sh', '-c', 'wget -q -O- --header "Accept: application/json" "http://localhost:8888/buckets/erp-media/c' + A.id + '/?limit=1000" 2>&1'], { encoding: 'utf8' }); try { return (JSON.parse(r.stdout).Entries || []).map(e => e.FullPath).join(String.fromCharCode(10)); } catch { return ''; } };
const png = Buffer.concat([Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]), Buffer.from('0000000D49484452000000010000000108060000001F15C4890000000A49444154789C6300010000050001', 'hex'), Buffer.from('0D0A2DB40000000049454E44AE426082', 'hex')]);
const up = async (name, tk = t) => { const fd = new FormData(); fd.append('files', new Blob([png], { type: 'image/png' }), name); const res = await fetch(G + '/media/files', { method: 'POST', headers: { Authorization: 'Bearer ' + tk, 'Accept-Language': 'en' }, body: fd }); const j = await res.json().catch(() => ({})); return { s: res.status, code: j.code, msg: j.message, data: j.data }; };
const dl = async (url) => { const res = await fetch(url); return { s: res.status, type: res.headers.get('content-type'), buf: Buffer.from(await res.arrayBuffer()) }; };
const S = '/settings/admin/storage';
let r = await c('GET', S, null, sa); const orig = r.data;
ck('platform storage settings readable, driver LOCAL', r.s === 200 && (orig.driver ?? 'LOCAL') === 'LOCAL', r.s + ' ' + show(orig));
const put = drv => c('PUT', S, { allowedFileTypes: orig.allowedFileTypes, maxUploadSizeKb: orig.maxUploadSizeKb, driver: drv }, sa);
try {
  // storage test against local MinIO (also creates the bucket)
  r = await c('POST', '/media/storage/test?driver=S3', null, sa); ck('storage test S3 (SeaweedFS) ok, no credential in the answer', r.s === 200 && r.data.ok === true && r.data.endpointHost === 'seaweedfs' && !show(r.data).toLowerCase().includes('secret'), r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(r.data));
  r = await c('POST', '/media/storage/test?driver=S3', null, t); ck('company admin storage test → 403', r.s === 403, r.s + ' ' + r.code);
  r = await c('POST', '/media/storage/test?driver=WASABI', null, sa); ck('WASABI with a non-wasabisys endpoint → MEDIA_013', r.code === 'MEDIA_013', r.s + ' ' + r.code + ' ' + r.msg);
  // a LOCAL file before the switch
  r = await up('before.png'); const loc = r.data?.[0]; ck('upload before the switch (LOCAL)', r.s === 200 && loc?.id, r.s + ' ' + r.code + ' ' + r.msg);
  // switch to S3
  r = await put('S3'); ck('driver switched to S3', r.s === 200 && r.data.driver === 'S3', r.s + ' ' + r.code + ' ' + r.msg);
  await sleep(65000); // media caches the policy for 60 s
  const beforeLs = mcLs();
  r = await up('after.png'); const s3f = r.data?.[0]; ck('upload after the switch', r.s === 200 && s3f?.id, r.s + ' ' + r.code + ' ' + r.msg);
  const lsAfter = mcLs(); const newObj = lsAfter.split('\n').filter(l => l.includes('.png') && !beforeLs.includes(l.trim().split(' ').pop()));
  ck('a new .png object appeared in SeaweedFS under c<companyId>/', newObj.length === 1 && newObj[0].includes('c' + A.id + '/'), JSON.stringify(newObj));
  ck('object key is server-generated (not the client name)', newObj.length === 1 && !newObj[0].includes('after.png'), JSON.stringify(newObj));
  r = await c('GET', '/media/files/' + s3f.id, null, t); const d1 = await dl(r.data.downloadUrl);
  console.log('PNGLEN', png.length, 'GOT', d1.buf.length, 'DIFF', [...png].findIndex((b, i) => b !== d1.buf[i]), 'HEADGOT', d1.buf.subarray(0, 16).toString('hex'), 'HEADEXP', png.subarray(0, 16).toString('hex'));
ck('S3 file downloads through the signed link, same bytes, server content type', d1.s === 200 && d1.buf.equals(png) && d1.type?.includes('image/png'), d1.s + ' ' + d1.type + ' ' + d1.buf.length);
  r = await c('GET', '/media/files/' + loc.id, null, t); const d0 = await dl(r.data.downloadUrl);
  ck('old LOCAL file still downloads after the switch', d0.s === 200 && d0.buf.equals(png), d0.s + ' ' + d0.buf.length);
  r = await c('GET', '/media/files/' + s3f.id, null, B.t); ck("company B can't read A's S3 file → 404", r.s === 404, r.s + ' ' + r.code);
  r = await c('DELETE', '/media/files/' + s3f.id, null, t); ck('delete S3 file', r.s === 200, r.s + ' ' + r.code);
  const lsDel = mcLs(); ck('object gone from SeaweedFS after delete', !lsDel.includes(newObj[0]?.trim().split(' ').pop() ?? '###'), JSON.stringify(lsDel.split('\n').filter(l => l.includes('c' + A.id + '/'))));
} finally {
  r = await put(orig.driver ?? 'LOCAL'); ck('driver restored to LOCAL', r.s === 200 && (r.data.driver ?? 'LOCAL') === 'LOCAL', r.s + ' ' + show(r.data));
}
console.log(`\n${n - f}/${n} passed`);

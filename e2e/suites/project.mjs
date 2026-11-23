// projects, tasks, forms
const today = new Date().toISOString().slice(0, 10);
const later = new Date(Date.now() + 30 * 864e5).toISOString().slice(0, 10);
const me = (await c('GET', '/me', null, t)).data; const myId = Number(me?.id ?? me?.userId ?? me?.user?.id ?? A.admin.id);
const pub = async (m, pa, b) => { const res = await fetch(G + pa, { method: m, headers: { 'Content-Type': 'application/json', 'Accept-Language': 'en' }, body: b && JSON.stringify(b) }); const j = await res.json().catch(() => ({})); return { s: res.status, code: j.code, msg: j.message, data: j.data }; };
const show = d => JSON.stringify(d)?.slice(0, 200);

// projects
let r = await c('POST', '/projects', { name: 'Test Portal', startDate: today, endDate: later, budget: 50000000, description: 'Fake project', userIds: [myId] }, t);
const pr = r.data; const pid = pr?.id; ck('project created ONGOING with team', r.s === 200 && pid && pr.status === 'ONGOING', r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(pr));
r = await c('POST', '/projects', { name: 'Bad', startDate: later, endDate: today, userIds: [] }, t); ck('end before start → PRJ_007', r.code === 'PRJ_007', r.s + ' ' + r.code);
r = await c('POST', '/projects', { name: 'Stranger', startDate: today, userIds: [999999] }, t); ck('non-company user → refused', r.s === 400 || r.s === 404, r.s + ' ' + r.code);
r = await c('POST', '/projects/' + pid + '/milestones', { title: 'Phase 1', cost: 1000, startDate: today, endDate: later, summary: 'First', status: 'ONGOING', progress: 20 }, t);
const ms = r.data; ck('milestone', r.s === 200 && ms?.id, r.s + ' ' + r.code + ' ' + r.msg);
r = await c('GET', '/projects/stages?kind=TASK', null, t); const st = r.data; ck('default task stages Todo..Done', r.s === 200 && st.length === 4 && st[3].name === 'Done', r.s + ' ' + show(st?.map?.(z => z.name)));
const bst = (await c('GET', '/projects/stages?kind=BUG', null, t)).data; ck('default bug stages (5)', bst?.length === 5, show(bst?.map?.(z => z.name)));
r = await c('POST', '/projects/' + pid + '/tasks', { milestoneId: Number(ms.id), title: 'Design login', priority: 'HIGH', assigneeIds: [myId], startDate: today, dueDate: today, description: 'x' }, t);
const t1 = r.data; ck('task in Todo', r.s === 200 && t1.stageName === 'Todo', r.s + ' ' + r.code + ' ' + r.msg + ' ' + t1?.stageName);
const t2 = (await c('POST', '/projects/' + pid + '/tasks', { title: 'Build API', priority: 'MEDIUM', assigneeIds: [], dueDate: later }, t)).data;
r = await c('POST', '/projects/' + pid + '/tasks', { title: 'Outsider', assigneeIds: [999999] }, t); ck('assignee not on team → PRJ_017', r.code === 'PRJ_017', r.s + ' ' + r.code);
r = await c('POST', '/projects/tasks/' + t1.id + '/subtasks', { name: 'Wireframe', done: false }, t); ck('subtask', r.s === 200, r.s + ' ' + r.code);
r = await c('POST', '/projects/tasks/' + t1.id + '/comments', { body: 'Looks good' }, t); ck('comment', r.s === 200, r.s + ' ' + r.code);
r = await c('PUT', '/projects/tasks/' + t1.id + '/move', { stageId: Number(st[3].id), position: 0 }, t); ck('move task to Done', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
r = await c('PUT', '/projects/tasks/' + t1.id + '/move', { stageId: Number(bst[0].id) }, t); ck('task into a bug stage → PRJ_024', r.code === 'PRJ_024', r.s + ' ' + r.code);
r = await c('GET', '/projects/' + pid + '/tasks/board', null, t); ck('task board 4 columns', r.s === 200 && r.data.length === 4, r.s + ' ' + show(r.data?.map?.(z => (z.name ?? z.stage?.name) + ':' + (z.tasks?.length))));
r = await c('GET', '/projects/' + pid, null, t); ck('detail progress 50%', r.s === 200 && Number(r.data.progress) === 50, r.s + ' ' + show({ p: r.data?.progress, d: r.data?.daysLeft }));
r = await c('POST', '/projects/' + pid + '/bugs', { title: 'Login 500', priority: 'HIGH', assigneeId: myId, description: 'x' }, t); const bug = r.data; ck('bug Unconfirmed', r.s === 200 && bug.stageName === 'Unconfirmed', r.s + ' ' + r.code + ' ' + r.msg + ' ' + bug?.stageName);
r = await c('PUT', '/projects/bugs/' + bug.id + '/move', { stageId: Number(bst[1].id) }, t); ck('bug → Confirmed', r.s === 200, r.s + ' ' + r.code);
r = await c('GET', '/projects/' + pid + '/report', null, t); ck('project report', r.s === 200, r.s + ' ' + show(r.data));
r = await c('GET', '/projects/' + pid + '/activity', null, t); ck('activity logged', r.s === 200 && (r.data.content ?? r.data).length > 0, r.s + ' ' + (r.data?.content ?? r.data)?.length);
r = await c('POST', '/projects/' + pid + '/duplicate', { name: 'Test Portal copy', tasks: true, bugs: false, activity: false, team: true, clients: false, milestones: true, files: false }, t);
const dup = r.data; ck('duplicate', r.s === 200 && dup?.id && dup.id !== pid, r.s + ' ' + r.code + ' ' + r.msg);
r = await c('GET', '/projects/' + dup?.id + '/bugs/board', null, t); ck('copy has no bugs', r.s === 200 && r.data.every(z => (z.bugs ?? []).length === 0), r.s + ' ' + show(r.data?.map?.(z => z.bugs?.length)));
r = await c('GET', '/projects/' + dup?.id + '/tasks', null, t); ck('copy has 2 tasks', r.s === 200 && (r.data.content ?? r.data).length === 2, r.s + ' ' + (r.data?.content ?? r.data)?.length);
r = await c('GET', '/projects/tasks/calendar?from=' + today + '&to=' + later, null, t); ck('task calendar', r.s === 200 && r.data.length >= 2, r.s + ' ' + r.data?.length);
// unified calendar through report-service
r = await c('GET', '/reports/calendar?from=' + today + '&to=' + later + '&kinds=PROJECT_TASK', null, t);
ck('unified calendar PROJECT_TASK', r.s === 200 && r.data.events.some(e => e.kind === 'PROJECT_TASK') && Object.keys(r.data.unavailable).length === 0, r.s + ' ' + r.code + ' ' + show(r.data?.unavailable) + ' n=' + r.data?.events?.length);
r = await c('GET', '/reports/calendar?from=' + today + '&to=' + later, null, t); ck('unified calendar all kinds', r.s === 200 && Object.keys(r.data.unavailable).length === 0, r.s + ' ' + r.code + ' unavailable=' + show(r.data?.unavailable));
// forms
r = await c('POST', '/forms', { name: 'Contact us', active: true, layout: 'SINGLE_COLUMN', fields: [{ key: 'name', label: 'Name', type: 'TEXT', required: true }, { key: 'email', label: 'Email', type: 'EMAIL', required: true }, { key: 'topic', label: 'Topic', type: 'SELECT', required: false, options: ['ERP', 'POS'] }] }, t);
const form = r.data?.form ?? r.data; const token = form?.publicToken ?? form?.token; ck('form created with public token', r.s === 200 && token, r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(form));
r = await pub('GET', '/public/forms/' + token); ck('public form without login', r.s === 200 && r.data.fields.length === 3 && r.data.companyId === undefined, r.s + ' ' + r.code + ' ' + show(r.data));
r = await pub('POST', '/public/forms/' + token + '/responses', { answers: { name: 'Test Visitor', email: 'not-an-email' } }); ck('bad email → FRM_005', r.code === 'FRM_005', r.s + ' ' + r.code);
r = await pub('POST', '/public/forms/' + token + '/responses', { answers: { name: 'Test Visitor', email: 'visitor@example.com', topic: 'HR' } }); ck('option not allowed → FRM_011', r.code === 'FRM_011', r.s + ' ' + r.code);
r = await pub('POST', '/public/forms/' + token + '/responses', { answers: { name: 'Test Visitor', email: 'visitor@example.com', topic: 'ERP' } }); ck('public response stored', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
await pub('POST', '/public/forms/' + token + '/responses', { answers: { name: 'Second Visitor', email: 'second@example.com' } });
r = await pub('GET', '/public/forms/not-a-real-token'); ck('unknown token → 404 FRM_002', r.s === 404 && r.code === 'FRM_002', r.s + ' ' + r.code);
r = await c('GET', '/forms/' + form.id + '/responses', null, t); const resp = r.data?.content ?? []; ck('responses listed', r.s === 200 && resp.length === 2, r.s + ' ' + resp.length);
r = await c('POST', '/forms/' + form.id + '/responses/' + resp[1].id + '/convert', { target: 'PROJECT', mapping: { name: 'name', description: 'email' }, values: { startDate: today } }, t);
ck('convert response → project', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(r.data));
r = await c('POST', '/forms/' + form.id + '/responses/' + resp[1].id + '/convert', { target: 'PROJECT', mapping: { name: 'name' }, values: {} }, t); ck('second convert → FRM_015', r.code === 'FRM_015', r.s + ' ' + r.code);
const pl = (await c('POST', '/crm/pipelines', { name: 'Web' }, t)).data; await c('POST', '/crm/pipelines/' + pl.id + '/stages', { kind: 'LEAD', name: 'New' }, t);
r = await c('POST', '/forms/' + form.id + '/responses/' + resp[0].id + '/convert', { target: 'LEAD', mapping: { name: 'name', email: 'email', subject: 'topic' }, values: { pipelineId: pl.id, ownerUserId: String(myId) } }, t);
ck('convert response → CRM lead', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(r.data));
r = await c('GET', '/crm/leads?pipelineId=' + pl.id, null, t); ck('lead exists in crm', r.s === 200 && r.data.content.some(l => l.name === 'Second Visitor' || l.name === 'Test Visitor'), r.s + ' ' + show(r.data?.content?.map(l => l.name)));
// timesheet month
const emp = (await c('GET', '/employees?size=1', null, t)).data?.content?.[0];
const ym = today.slice(0, 7).split('-');
r = await c('GET', '/attendance/timesheets/month?year=' + ym[0] + '&month=' + Number(ym[1]), null, t); ck('timesheet month grid', r.s === 200 && r.data.dayTotals.length >= 28, r.s + ' ' + r.code + ' ' + r.msg + ' emp=' + !!emp);
// isolation & permissions
r = await c('GET', '/projects/' + pid, null, B.t); ck('company B reads A project → 404 PRJ_006', r.s === 404 && r.code === 'PRJ_006', r.s + ' ' + r.code);
r = await c('PUT', '/projects/tasks/' + t2.id + '/move', { stageId: Number(st[3].id) }, B.t); ck('company B moves A task → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('DELETE', '/projects/' + pid, null, B.t); ck('company B deletes A project → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('GET', '/forms/' + form.id, null, B.t); ck('company B reads A form → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('POST', '/forms/' + form.id + '/responses/' + resp[0].id + '/convert', { target: 'PROJECT', mapping: { name: 'name' } }, B.t); ck('company B converts A response → 404', r.s === 404, r.s + ' ' + r.code);
const roles = (await c('GET', '/iam/roles', null, t)).data; const empRole = roles.find(z => z.code === 'EMPLOYEE'); const ep = pw(); const eun = 'pj.emp.' + x.toLowerCase();
const eu = await c('POST', '/iam/users', { username: eun, email: eun + '@example.com', fullName: 'Project Employee', roleIds: [Number(empRole.id)], password: ep }, t); const ep2 = pw();
await c('POST', '/auth/initial-password', { username: eun, currentPassword: eu.data?.temporaryPassword || ep, newPassword: ep2 });
const et = (await c('POST', '/auth/login', { username: eun, password: ep2 })).data?.accessToken;
r = await c('POST', '/projects', { name: 'Nope', userIds: [] }, et); ck('employee creates project → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', '/forms', null, et); ck('employee forms → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', '/projects'); ck('no token → 401', r.s === 401, r.s);
r = await pub('GET', '/forms/' + form.id); ck('authenticated form path without token → 401', r.s === 401, r.s);
console.log(`\n${n - f}/${n} passed`);

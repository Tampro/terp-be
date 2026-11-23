// performance and training
const num = v => Number(v); const show = d => JSON.stringify(d)?.slice(0, 220);
const today = new Date().toISOString().slice(0, 10); const ym = today.slice(0, 7);
const ago = d => new Date(Date.now() - d * 864e5).toISOString().slice(0, 10); const ahead = d => new Date(Date.now() + d * 864e5).toISOString().slice(0, 10);
const br = (await c('POST', '/org/branches', { name: 'Test Branch ' + x, city: 'Test City', status: 'ACTIVE' }, t)).data;
const br2 = (await c('POST', '/org/branches', { name: 'Other Branch ' + x, city: 'Test City', status: 'ACTIVE' }, t)).data;
const dep = (await c('POST', '/org/departments', { name: 'Test Dept', branchId: num(br.id), status: 'ACTIVE' }, t)).data;
const des = (await c('POST', '/org/designations', { name: 'Test Clerk', departmentId: num(dep.id), status: 'ACTIVE' }, t)).data;
const mkEmp = async tag => (await c('POST', '/employees', { email: 'pt.' + tag + '.' + x.toLowerCase() + '@example.com', password: pw(), profile: { fullName: 'Test ' + tag + ' ' + x, gender: 'MALE', branchId: num(br.id), departmentId: num(dep.id), designationId: num(des.id), dateOfJoining: ym + '-01', employmentType: 'FULL_TIME', status: 'ACTIVE' } }, t)).data;
const e1 = await mkEmp('staff'), e2 = await mkEmp('boss');
const eid = e => num(e?.id ?? e?.employee?.id);
ck('two employees', eid(e1) && eid(e2), show([e1?.id, e2?.id]));
// reviews
const cat = (await c('POST', '/performance/indicator-categories', { name: 'Job Performance' }, t)).data;
const ind = (await c('POST', '/performance/indicators', { name: 'Quality', categoryId: num(cat?.id), measurementUnit: 'score', targetValue: '5', weight: 1, status: 'ACTIVE' }, t)).data;
const cyc = (await c('POST', '/performance/review-cycles', { name: 'Q4 ' + x, frequency: 'QUARTERLY', status: 'ACTIVE' }, t)).data;
let r = await c('POST', '/performance/reviews', { employeeId: eid(e1), reviewerId: eid(e2), reviewCycleId: num(cyc?.id), reviewDate: today }, t);
const rev = r.data; ck('review created PENDING', r.s === 200 && rev.status === 'PENDING', r.s + ' ' + r.code + ' ' + r.msg + ' ' + rev?.status);
r = await c('PUT', '/performance/reviews/' + rev.id + '/conduct', { ratings: [{ indicatorId: num(ind.id), rating: 6 }], submit: false }, t); ck('rating 6 → COMMON_001', r.s === 400 && r.code === 'COMMON_001', r.s + ' ' + r.code);
r = await c('PUT', '/performance/reviews/' + rev.id + '/conduct', { ratings: [{ indicatorId: num(ind.id), rating: 4, comments: 'Good' }], overallComments: 'Solid quarter', pros: ' Careful work ', cons: 'Slow replies', submit: false }, t);
ck('conduct saves pros/cons, PENDING → IN_PROGRESS', r.s === 200 && r.data.status === 'IN_PROGRESS' && r.data.pros === 'Careful work' && r.data.cons === 'Slow replies', r.s + ' ' + r.code + ' ' + r.msg + ' ' + show({ s: r.data?.status, p: r.data?.pros }));
r = await c('PUT', '/performance/reviews/' + rev.id + '/status', { status: 'PENDING' }, t); ck('moving back → EMP_096', r.code === 'EMP_096', r.s + ' ' + r.code);
r = await c('PUT', '/performance/reviews/' + rev.id + '/status', { status: 'COMPLETED' }, t); ck('complete', r.s === 200 && r.data.status === 'COMPLETED', r.s + ' ' + r.code + ' ' + r.msg);
r = await c('PUT', '/performance/reviews/' + rev.id + '/conduct', { ratings: [{ indicatorId: num(ind.id), rating: 1 }], submit: false }, t); ck('completed review read-only → EMP_077', r.code === 'EMP_077', r.s + ' ' + r.code);
// goals
const gt = (await c('POST', '/performance/goal-types', { name: 'Skill' }, t)).data;
r = await c('POST', '/performance/goals', { employeeId: eid(e1), goalTypeId: num(gt?.id), title: 'Late goal', startDate: ago(30), endDate: ago(2), target: 'Finish course', progress: 20, status: 'IN_PROGRESS' }, t);
const g1 = r.data; ck('goal past its end shows OVERDUE', r.s === 200 && g1.status === 'OVERDUE', r.s + ' ' + r.code + ' ' + r.msg + ' ' + g1?.status);
await c('POST', '/performance/goals', { employeeId: eid(e1), goalTypeId: num(gt?.id), title: 'Fresh goal', startDate: today, endDate: ahead(30), target: 'Ship it', progress: 0, status: 'NOT_STARTED' }, t);
r = await c('GET', '/performance/goals?status=OVERDUE&employeeId=' + eid(e1), null, t); ck('filter OVERDUE → 1', r.s === 200 && r.data.content.length === 1 && r.data.content[0].title === 'Late goal', r.s + ' ' + r.code + ' ' + r.data?.content?.length);
r = await c('POST', '/performance/goals', { employeeId: eid(e1), goalTypeId: num(gt?.id), title: 'X', startDate: today, endDate: ahead(3), target: 'x', progress: 0, status: 'OVERDUE' }, t); ck('setting OVERDUE → EMP_099', r.code === 'EMP_099', r.s + ' ' + r.code);
r = await c('GET', '/performance/goals/status-counts?employeeId=' + eid(e1), null, t); ck('status counts have OVERDUE 1', r.s === 200 && show(r.data).includes('OVERDUE'), r.s + ' ' + show(r.data));
// trainers + sessions
r = await c('POST', '/training/trainers', { name: 'Test Trainer', contactPhone: '0900000001', email: 'trainer@example.com', experienceYears: 7, branchId: num(br.id), departmentId: num(dep.id), expertise: 'Excel', qualification: 'MOS' }, t);
const tr = r.data; ck('trainer created (active)', r.s === 200 && tr.active === true, r.s + ' ' + r.code + ' ' + r.msg);
r = await c('POST', '/training/trainers', { name: 'Bad Trainer', branchId: num(br2.id), departmentId: num(dep.id) }, t); ck('department not of branch → EMP_003', r.code === 'EMP_003', r.s + ' ' + r.code);
const ttype = (await c('POST', '/training/types', { name: 'Tech', branchId: num(br.id) }, t)).data;
const prog = (await c('POST', '/training/programs', { name: 'Excel basics', trainingTypeId: num(ttype?.id), durationHours: 4, cost: 1000000, capacity: 20, status: 'ACTIVE', mandatory: false, selfEnrollment: false }, t)).data;
r = await c('POST', '/training/sessions', { programId: num(prog?.id), name: 'Session 1', startAt: ahead(5) + 'T09:00:00', endAt: ahead(5) + 'T13:00:00', locationType: 'IN_PERSON', location: 'Room 1', status: 'SCHEDULED', trainerId: num(tr.id) }, t);
const ses = r.data?.sessions?.[0] ?? r.data?.session ?? r.data; ck('session with trainer', r.s === 200 && ses?.trainer?.id === tr.id, r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(ses?.trainer));
r = await c('DELETE', '/training/trainers/' + tr.id, null, t); ck('trainer in use cannot be deleted → EMP_010', r.code === 'EMP_010', r.s + ' ' + r.code);
r = await c('GET', '/training/trainers?keyword=Test', null, t); ck('trainer list', r.s === 200 && r.data.content.length === 1, r.s + ' ' + r.data?.content?.length);
// isolation & permissions
const trB = (await c('POST', '/training/trainers', { name: 'B Trainer' }, B.t)).data;
r = await c('POST', '/training/sessions', { programId: num(prog?.id), name: 'Session 2', startAt: ahead(6) + 'T09:00:00', endAt: ahead(6) + 'T10:00:00', locationType: 'VIRTUAL', status: 'SCHEDULED', trainerId: num(trB?.id) }, t); ck("session with company B's trainer → EMP_101", r.code === 'EMP_101', r.s + ' ' + r.code);
r = await c('GET', '/training/trainers/' + tr.id, null, B.t); ck('company B reads A trainer → 404 EMP_100', r.s === 404 && r.code === 'EMP_100', r.s + ' ' + r.code);
r = await c('PUT', '/performance/reviews/' + rev.id + '/status', { status: 'COMPLETED' }, B.t); ck('company B changes A review → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('GET', '/performance/goals/' + g1.id, null, B.t); ck('company B reads A goal → 404', r.s === 404, r.s + ' ' + r.code);
const roles = (await c('GET', '/iam/roles', null, t)).data; const empRole = roles.find(z => z.code === 'EMPLOYEE'); const ep = pw(); const eun = 'pt.emp.' + x.toLowerCase();
const eu = await c('POST', '/iam/users', { username: eun, email: eun + '@example.com', fullName: 'PT Employee', roleIds: [Number(empRole.id)], password: ep }, t); const ep2 = pw();
await c('POST', '/auth/initial-password', { username: eun, currentPassword: eu.data?.temporaryPassword || ep, newPassword: ep2 });
const et = (await c('POST', '/auth/login', { username: eun, password: ep2 })).data?.accessToken;
r = await c('POST', '/training/trainers', { name: 'Nope' }, et); ck('employee creates trainer → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', '/performance/reviews', null, et); ck('employee reviews list → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', '/training/trainers'); ck('no token → 401', r.s === 401, r.s);
console.log(`\n${n - f}/${n} passed`);

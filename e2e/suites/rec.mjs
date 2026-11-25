// recruitment incl. public career page

const num = v => Number(v); const show = d => JSON.stringify(d)?.slice(0, 220);
const today = new Date().toISOString().slice(0, 10); const ahead = d => new Date(Date.now() + d * 864e5).toISOString().slice(0, 10);
const R = '/recruitment';
const raw = async (m, pa, t) => { const r = await fetch(G + pa, { method: m, headers: { 'Accept-Language': 'en', ...(t ? { Authorization: 'Bearer ' + t } : {}) } }); return { s: r.status, type: r.headers.get('content-type'), buf: Buffer.from(await r.arrayBuffer()) }; };
const apply = async (slug, jobId, app) => { const fd = new FormData(); fd.append('application', new Blob([JSON.stringify(app)], { type: 'application/json' })); fd.append('resume', new Blob([Buffer.from('%PDF-1.4\n% fake resume\n%%EOF')], { type: 'application/pdf' }), 'resume.pdf'); const r = await fetch(G + '/public/career/' + slug + '/jobs/' + jobId + '/apply', { method: 'POST', headers: { 'Accept-Language': 'en' }, body: fd }); const j = await r.json().catch(() => ({})); return { s: r.status, code: j.code, msg: j.message, data: j.data }; };
// org + employees (interviewer, approver)
const br = (await c('POST', '/org/branches', { name: 'Rec Branch ' + x, city: 'Test City', status: 'ACTIVE' }, t)).data;
const dep = (await c('POST', '/org/departments', { name: 'Rec Dept', branchId: num(br.id), status: 'ACTIVE' }, t)).data;
const des = (await c('POST', '/org/designations', { name: 'Rec Dev', departmentId: num(dep.id), status: 'ACTIVE' }, t)).data;
const bossEmail = 'rc.boss.' + x.toLowerCase() + '@example.com', bossPw = pw();
const emp = (await c('POST', '/employees', { email: bossEmail, password: bossPw, profile: { fullName: 'Test Boss ' + x, gender: 'MALE', branchId: num(br.id), departmentId: num(dep.id), designationId: num(des.id), dateOfJoining: today, employmentType: 'FULL_TIME', status: 'ACTIVE' } }, t)).data;
const eid = num(emp?.id ?? emp?.employee?.id); ck('interviewer employee', eid > 0, show(emp));
// config
const slug = 'rc-' + x.toLowerCase();
let r = await c('PUT', R + '/career-settings', { slug, enabled: true, displayName: 'Test Careers ' + x }, t); ck('career page', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
const jt = (await c('POST', R + '/job-types', { name: 'Full time' }, t)).data;
const loc = (await c('POST', R + '/job-locations', { name: 'HQ', city: 'Test City', status: 'ACTIVE' }, t)).data;
const itype = (await c('POST', R + '/interview-types', { name: 'Video' }, t)).data;
r = await c('POST', R + '/custom-questions', { question: 'Pick one', type: 'SELECT', required: true }, t); ck('SELECT without options → REC_062', r.code === 'REC_062', r.s + ' ' + r.code);
r = await c('POST', R + '/custom-questions', { question: 'Free text', type: 'TEXT', options: ['A'], required: false }, t); ck('TEXT with options → REC_063', r.code === 'REC_063', r.s + ' ' + r.code);
const q1 = (await c('POST', R + '/custom-questions', { question: 'Preferred stack', type: 'SELECT', options: ['Java', 'Go'], required: true }, t)).data;
const q2 = (await c('POST', R + '/custom-questions', { question: 'Years of Java', type: 'NUMBER', required: true }, t)).data;
ck('typed questions', q1?.type === 'SELECT' && q1.options?.length === 2 && q2?.type === 'NUMBER', show([q1, q2]));
r = await c('POST', R + '/job-postings', { title: 'Backend Dev ' + x, jobTypeId: num(jt?.id), locationId: num(loc?.id), branchId: num(br.id), departmentId: num(dep.id), priority: 'HIGH', skills: ['Java'], startDate: today, applicationDeadline: ahead(30), applicationType: 'EXISTING', positions: 1, minSalary: 10000000, maxSalary: 20000000, description: 'Test job', customQuestionIds: [num(q1?.id), num(q2?.id)] }, t);
const job = r.data; ck('posting DRAFT', r.s === 200 && job.status === 'DRAFT', r.s + ' ' + r.code + ' ' + r.msg);
r = await c('PUT', R + '/job-postings/' + job.id + '/close', null, t); ck('close a draft → REC_056', r.code === 'REC_056', r.s + ' ' + r.code);
r = await c('PUT', R + '/job-postings/' + job.id + '/publish', null, t); ck('publish', r.s === 200 && r.data.status === 'PUBLISHED', r.s + ' ' + r.code + ' ' + r.msg);
r = await c('GET', '/public/career/' + slug + '/jobs/' + job.id); ck('public detail shows typed questions', r.s === 200 && JSON.stringify(r.data).includes('"SELECT"'), r.s + ' ' + r.code);
// apply
const email = 'rc.cand.' + x.toLowerCase() + '@example.com';
const base = { firstName: 'Test', lastName: 'Candidate', email, phone: '0900000002', termsAccepted: true };
r = await apply(slug, job.id, { ...base, answers: [{ questionId: num(q1.id), answer: 'Rust' }, { questionId: num(q2.id), answer: '3' }] }); ck('answer not an option → REC_066', r.code === 'REC_066', r.s + ' ' + r.code + ' ' + r.msg);
r = await apply(slug, job.id, { ...base, answers: [{ questionId: num(q1.id), answer: 'java' }, { questionId: num(q2.id), answer: 'three' }] }); ck('answer not a number → REC_064', r.code === 'REC_064', r.s + ' ' + r.code);
r = await apply(slug, job.id, { ...base, answers: [{ questionId: num(q1.id), answer: 'java' }, { questionId: num(q2.id), answer: '3' }] });
const trk = r.data?.trackingId; ck('apply returns a 12-char tracking ID', r.s === 200 && /^[A-Z2-9]{12}$/.test(trk || ''), r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(r.data));
r = await c('GET', '/public/career/' + slug + '/track?trackingId=' + trk.toLowerCase() + '&email=' + email.toUpperCase()); ck('track: NEW, case-insensitive', r.s === 200 && r.data.status === 'NEW' && !('notes' in r.data), r.s + ' ' + show(r.data));
r = await c('GET', '/public/career/' + slug + '/track?trackingId=' + trk + '&email=other@example.com'); ck('track wrong email → 404 REC_059', r.s === 404 && r.code === 'REC_059', r.s + ' ' + r.code);
const cand = (await c('GET', R + '/candidates?keyword=Candidate&jobPostingId=' + job.id, null, t)).data?.content?.find(z => z.email === email);
ck('candidate in admin list', !!cand, show(cand));
// interview → OFFER, assessment gate
await c('PUT', R + '/candidates/' + cand.id + '/status', { status: 'SCREENING' }, t);
const round = (await c('POST', R + '/interview-rounds', { jobPostingId: num(job.id), name: 'Tech round', sequenceNumber: 1 }, t)).data;
r = await c('POST', R + '/interviews', { candidateId: num(cand.id), roundId: num(round?.id), interviewTypeId: num(itype?.id), date: ahead(1), startTime: '09:00', durationMinutes: 60, location: 'Room A', interviewerIds: [eid] }, t);
const iv = r.data; ck('interview scheduled (candidate → INTERVIEW)', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(r.data) + ' eid=' + eid + ' round=' + show(round));
r = await c('PUT', R + '/interviews/' + iv.id + '/status', { status: 'COMPLETED' }, t);
r = await c('GET', R + '/candidates/' + cand.id, null, t); ck('completing the interview → candidate OFFER', r.data?.status === 'OFFER', r.s + ' ' + r.data?.status);
r = await c('POST', R + '/assessments', { candidateId: num(cand.id), name: 'Coding test', score: 8, maxScore: 10, status: 'PASS', conductedByEmployeeId: eid, assessmentDate: today }, t); ck('assessment without Hire feedback → REC_057', r.code === 'REC_057', r.s + ' ' + r.code);
r = await c('POST', R + '/interviews/' + iv.id + '/feedback', { employeeId: eid, overallScore: 5, technicalScore: 5, communicationScore: 4, culturalFitScore: 4, recommendation: 'STRONG_HIRE', strengths: 'Clear code' }, t); ck('feedback STRONG_HIRE', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
r = await c('POST', R + '/assessments', { candidateId: num(cand.id), name: 'Coding test', score: 8, maxScore: 10, status: 'PASS', conductedByEmployeeId: eid, assessmentDate: today }, t); ck('assessment after Hire feedback', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
// offer approval, letter, convert
r = await c('POST', R + '/offers', { candidateId: num(cand.id), jobPostingId: num(job.id), departmentId: num(dep.id), salary: 15000000, startDate: ahead(14), expirationDate: ahead(7), approvedByEmployeeId: eid, benefits: 'Laptop' }, t);
const off = r.data; ck('offer DRAFT, approval PENDING', r.s === 200 && off.approvalStatus === 'PENDING', r.s + ' ' + r.code + ' ' + r.msg + ' ' + off?.approvalStatus);
r = await c('PUT', R + '/offers/' + off.id + '/status', { status: 'SENT' }, t); ck('send before approval → REC_058', r.code === 'REC_058', r.s + ' ' + r.code);
r = await c('PUT', R + '/offers/' + off.id + '/approval', { approvalStatus: 'APPROVED' }, t); ck('approve records who and when', r.s === 200 && r.data.approvalStatus === 'APPROVED' && r.data.approvalDecidedBy && r.data.approvalDecidedAt, r.s + ' ' + r.code + ' ' + show(r.data));
r = await c('PUT', R + '/offers/' + off.id + '/status', { status: 'SENT' }, t); ck('send after approval', r.s === 200 && r.data.status === 'SENT', r.s + ' ' + r.code + ' ' + r.msg);
const tpl = (await c('POST', R + '/offer-templates', { name: 'Standard', content: '<p>Dear {{candidate_name}},</p><p>Salary {{salary}} from {{start_date}}.</p>', variables: 'candidate_name,salary,start_date', status: 'ACTIVE' }, t)).data;
let L = await raw('GET', R + '/offers/' + off.id + '/letter?templateId=' + tpl?.id, t); ck('offer letter PDF', L.s === 200 && L.type?.includes('pdf') && L.buf.subarray(0, 5).toString() === '%PDF-', L.s + ' ' + L.type + ' ' + L.buf.subarray(0, 80).toString());
r = await c('POST', R + '/offers/' + off.id + '/convert-to-employee', { branchId: num(br.id), departmentId: num(dep.id), designationId: num(des.id), employmentType: 'FULL_TIME' }, t); ck('convert a SENT offer → REC_060', r.code === 'REC_060', r.s + ' ' + r.code);
await c('PUT', R + '/offers/' + off.id + '/status', { status: 'ACCEPTED' }, t);
r = await c('POST', R + '/offers/' + off.id + '/convert-to-employee', { branchId: num(br.id), departmentId: num(dep.id), designationId: num(des.id), employmentType: 'FULL_TIME' }, t);
const conv = r.data; ck('convert → employee, candidate HIRED', r.s === 200 && num(conv.employeeId) > 0 && conv.candidateStatus === 'HIRED', r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(conv));
r = await c('GET', '/employees/' + conv?.employeeId, null, t); ck('employee exists in employee-service', r.s === 200 && show(r.data).includes('Candidate'), r.s + ' ' + r.code + ' ' + show(r.data));
r = await c('POST', R + '/offers/' + off.id + '/convert-to-employee', { branchId: num(br.id), designationId: num(des.id), employmentType: 'FULL_TIME' }, t); ck('convert twice → REC_061', r.code === 'REC_061', r.s + ' ' + r.code);
r = await c('GET', '/public/career/' + slug + '/track?trackingId=' + trk + '&email=' + email); ck('track shows HIRED', r.data?.status === 'HIRED', show(r.data));
// close posting
r = await c('PUT', R + '/job-postings/' + job.id + '/close', null, t); ck('close published', r.s === 200 && r.data.status === 'CLOSED', r.s + ' ' + r.code + ' ' + r.msg);
r = await c('PUT', R + '/job-postings/' + job.id + '/unpublish', null, t); ck('closed → draft → REC_055', r.code === 'REC_055', r.s + ' ' + r.code);
r = await apply(slug, job.id, { ...base, email: 'rc.late.' + x.toLowerCase() + '@example.com', answers: [] }); ck('apply to closed job refused', r.s === 404 || r.code === 'REC_054', r.s + ' ' + r.code);
// checklist role
const cl = (await c('POST', R + '/onboarding-checklists', { name: 'Default ' + x }, t)).data;
r = await c('POST', R + '/checklist-items', { checklistId: num(cl?.id), taskName: 'Laptop', category: 'IT_SETUP', assignedToRole: 'IT Admin', required: true, dueDay: 1 }, t); ck('checklist item with role', r.s === 200 && r.data.assignedToRole === 'IT Admin', r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(r.data));
// isolation & permissions
r = await c('PUT', R + '/offers/' + off.id + '/approval', { approvalStatus: 'REJECTED' }, B.t); ck('company B approves A offer → 404', r.s === 404, r.s + ' ' + r.code);
L = await raw('GET', R + '/offers/' + off.id + '/letter?templateId=' + tpl?.id, B.t); ck('company B letter of A offer → 404', L.s === 404, L.s);
r = await c('POST', R + '/offers/' + off.id + '/convert-to-employee', { employmentType: 'FULL_TIME' }, B.t); ck('company B converts A offer → 404', r.s === 404, r.s + ' ' + r.code);
r = await c('PUT', R + '/job-postings/' + job.id + '/close', null, B.t); ck('company B closes A posting → 404', r.s === 404, r.s + ' ' + r.code);
const roles = (await c('GET', '/iam/roles', null, t)).data; const empRole = roles.find(z => z.code === 'EMPLOYEE'); const ep = pw(); const eun = 'rc.emp.' + x.toLowerCase();
const eu = await c('POST', '/iam/users', { username: eun, email: eun + '@example.com', fullName: 'Rec Employee', roleIds: [Number(empRole.id)], password: ep }, t); const ep2 = pw();
await c('POST', '/auth/initial-password', { username: eun, currentPassword: eu.data?.temporaryPassword || ep, newPassword: ep2 });
const et = (await c('POST', '/auth/login', { username: eun, password: ep2 })).data?.accessToken;
r = await c('PUT', R + '/offers/' + off.id + '/approval', { approvalStatus: 'APPROVED' }, et); ck('employee approves offer → 403', r.s === 403, r.s + ' ' + r.code);
// "my interviews" for a MANAGER (no EMPLOYEE_SELF): the profile lookup must not need that permission
const mgrRole = roles.find(z => z.code === 'MANAGER'); const bossUser = (await c('GET', '/employees/' + eid, null, t)).data?.userId;
r = await c('PUT', '/iam/users/' + bossUser + '/roles', { roleIds: [Number(mgrRole.id)] }, t); ck('interviewer → MANAGER only', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
let bl = await c('POST', '/auth/login', { username: bossEmail, password: bossPw });
if (!bl.data?.accessToken) { const np = pw(); await c('POST', '/auth/initial-password', { username: bossEmail, currentPassword: bossPw, newPassword: np }); bl = await c('POST', '/auth/login', { username: bossEmail, password: np }); }
const mt = bl.data?.accessToken; ck('MANAGER interviewer logs in', !!mt, bl.s + ' ' + bl.code + ' ' + bl.msg);
r = await c('GET', '/employees/me', null, mt); ck('MANAGER full profile still needs EMPLOYEE_SELF → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('GET', '/employees/me/ref', null, mt); ck('MANAGER /employees/me/ref → own id only', r.s === 200 && r.data?.id === String(eid) && !('phone' in (r.data || {})), r.s + ' ' + r.code + ' ' + show(r.data));
r = await c('GET', R + '/interviews/my?from=' + today, null, mt); ck('MANAGER /interviews/my lists the panel interview', r.s === 200 && r.data?.content?.some(z => z.id === iv.id), r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(r.data));
r = await c('GET', '/employees/me/ref', null, sa); ck('super admin /employees/me/ref → 403 EMP_041', r.s === 403 && r.code === 'EMP_041', r.s + ' ' + r.code);
r = await c('GET', R + '/offers'); ck('no token → 401', r.s === 401, r.s);
console.log(`\n${n - f}/${n} passed`);

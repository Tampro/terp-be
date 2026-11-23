// AI assistant gating (never calls a provider)

// SAFETY: the plan flag and the AI integration are never both ON at the same time, so no request can reach a provider.
const show = d => JSON.stringify(d)?.slice(0, 260);
const P = '/iam/ai/compose'; const body = (o = {}) => ({ purpose: 'TICKET_REPLY', language: 'vi', tone: 'FRIENDLY', context: 'Customer cannot log in after password reset.', ...o });
const roles = (await c('GET', '/iam/roles', null, t)).data; const empRole = roles.find(z => z.code === 'EMPLOYEE');
const un = 'ai.emp.' + x.toLowerCase(); const p0 = pw(); const eu = await c('POST', '/iam/users', { username: un, email: un + '@example.com', fullName: 'AI Employee', roleIds: [Number(empRole.id)], password: p0 }, t); const p1 = pw();
await c('POST', '/auth/initial-password', { username: un, currentPassword: eu.data?.temporaryPassword || p0, newPassword: p1 });
const et = (await c('POST', '/auth/login', { username: un, password: p1 })).data?.accessToken;
// 1. plan without AI (default) + integration ON with a fake key → AI_002 before any call
let r = await c('PUT', '/settings/integrations/ai-assistant', { enabled: true, provider: 'ANTHROPIC', apiKey: 'fake-not-a-real-key', model: 'claude-test' }, t); ck('AI integration saved (key hidden)', r.s === 200 && r.data.hasApiKey === true && !show(r.data).includes('fake-not'), r.s + ' ' + r.code + ' ' + r.msg);
r = await c('POST', P, body(), t); ck('plan without AI → 403 (AI_002 or module filtered out; no provider call)', r.s === 403 && ['AI_002', 'COMMON_004'].includes(r.code), r.s + ' ' + r.code + ' ' + r.msg);
// 2. turn the integration OFF, then give company A a plan with AI → AI_003
await c('DELETE', '/settings/integrations/ai-assistant', null, t);
r = await c('GET', '/settings/integrations', null, t); ck('AI integration removed', r.data?.aiAssistant?.configured === false, show(r.data?.aiAssistant));
const plans = (await c('GET', '/saas/admin/plans?size=50', null, sa)).data; const base = (plans?.content ?? plans ?? []).find(p => (p.modules || []).length > 5) ?? (plans?.content ?? plans)[0];
const mods = Array.from(new Set([...(base.modules || []), 'AI_ASSISTANT']));
r = await c('POST', '/saas/admin/plans', { code: 'AIPLAN_' + x, name: 'AI plan ' + x, monthlyPrice: 0, maxUsers: 50, storageLimitGb: 1, enabled: true, aiIntegration: true, modules: mods }, sa); const aiPlan = r.data;
ck('plan with aiIntegration + AI_ASSISTANT module', r.s === 200 && aiPlan.aiIntegration === true, r.s + ' ' + r.code + ' ' + r.msg);
r = await c('PUT', '/saas/admin/companies/' + A.id + '/subscription', { planId: Number(aiPlan.id) }, sa); ck('company A on the AI plan', r.s === 200, r.s + ' ' + r.code);
r = await c('POST', P, body(), t); ck('integration off → 400 AI_003 (no provider call)', r.s === 400 && r.code === 'AI_003', r.s + ' ' + r.code + ' ' + r.msg);
// 3. size limits, permission, super admin, bad purpose
r = await c('POST', P, body({ context: 'x'.repeat(4001) }), t); ck('context over 4000 chars → 400', r.s === 400, r.s + ' ' + r.code);
r = await c('POST', P, body({ instructions: 'y'.repeat(501) }), t); ck('instructions over 500 chars → 400', r.s === 400, r.s + ' ' + r.code);
r = await c('POST', P, body({ purpose: 'HACK' }), t); ck('unknown purpose → 400', r.s === 400, r.s + ' ' + r.code);
r = await c('POST', P, body(), et); ck('employee without AI_ASSISTANT_USE → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('POST', P, body(), sa); ck('super admin → 403 AI_001', r.s === 403 && r.code === 'AI_001', r.s + ' ' + r.code);
r = await c('POST', P, body(), B.t); ck('company B (plan without AI) → AI_002', r.code === 'AI_002' || r.s === 403, r.s + ' ' + r.code);
// 4. usage log: nothing reached a provider, so nothing recorded; no text stored anywhere
r = await c('GET', '/iam/ai/usage', null, t); ck('usage log readable and empty (every request stopped before the call)', r.s === 200 && (r.data?.content || []).length === 0, r.s + ' ' + show(r.data));
r = await c('GET', '/iam/ai/usage', null, et); ck('employee usage log → 403', r.s === 403, r.s + ' ' + r.code);
r = await c('POST', P, body()); ck('no token → 401', r.s === 401, r.s);
console.log(`\n${n - f}/${n} passed`);

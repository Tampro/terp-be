// coupon types incl. FIXED

const num = v => Number(v); const show = d => JSON.stringify(d)?.slice(0, 260);
// a plan with known prices, then FIXED / FLAT / PERCENTAGE coupons quoted by company A
const plans = (await c('GET', '/saas/admin/plans?size=50', null, sa)).data; const base = (plans?.content ?? plans ?? []).find(p => (p.modules || []).length > 5) ?? (plans?.content ?? plans)[0];
let r = await c('POST', '/saas/admin/plans', { code: 'CP_' + x, name: 'Coupon plan ' + x, monthlyPrice: 490000, yearlyPrice: 4900000, maxUsers: 50, storageLimitGb: 5, enabled: true, modules: base.modules }, sa); const plan = r.data;
ck('plan 490,000 / month', r.s === 200, r.s + ' ' + r.code + ' ' + r.msg);
const mkCoupon = async (code, type, value) => (await c('POST', '/saas/admin/coupons', { name: code, type, discountValue: value, codeGeneration: 'MANUAL', code, enabled: true }, sa));
r = await mkCoupon('FIX' + x, 'FIXED', 199000); ck('FIXED coupon created', r.s === 200 && r.data.type === 'FIXED', r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(r.data));
await mkCoupon('HIGH' + x, 'FIXED', 600000); await mkCoupon('FLAT' + x, 'FLAT', 100000); await mkCoupon('PCT' + x, 'PERCENTAGE', 10);
const quote = (code, cycle = 'MONTHLY') => c('POST', '/saas/orders/quote', { planId: num(plan.id), billingCycle: cycle, couponCode: code }, t);
r = await quote('FIX' + x); ck('FIXED 199,000 → pay 199,000, discount 291,000', r.s === 200 && num(r.data.finalPrice) === 199000 && num(r.data.discountAmount) === 291000, r.s + ' ' + r.code + ' ' + show(r.data));
r = await quote('FIX' + x, 'YEARLY'); ck('FIXED on the yearly price → still pay 199,000', num(r.data?.finalPrice) === 199000 && num(r.data.discountAmount) === 4701000, show(r.data));
r = await quote('HIGH' + x); ck('FIXED price above the plan price → no discount', num(r.data?.finalPrice) === 490000 && num(r.data.discountAmount) === 0, show(r.data));
r = await quote('FLAT' + x); ck('FLAT 100,000 unchanged → pay 390,000', num(r.data?.finalPrice) === 390000, show(r.data));
r = await quote('PCT' + x); ck('PERCENTAGE 10 % unchanged → pay 441,000', num(r.data?.finalPrice) === 441000, show(r.data));
r = await c('GET', '/saas/admin/coupons?type=FIXED&size=50', null, sa); ck('coupon list filter type=FIXED', r.s === 200 && (r.data?.content || []).filter(z => z.code?.endsWith(x)).length === 2 && r.data.content.every(z => z.type === 'FIXED'), r.s + ' ' + show(r.data?.content?.map(z => z.code)));
r = await c('POST', '/saas/admin/coupons', { name: 'Bad', type: 'FIXED', discountValue: -1, codeGeneration: 'MANUAL', code: 'BAD' + x, enabled: true }, sa); ck('negative FIXED price → 400', r.s === 400, r.s + ' ' + r.code);
r = await mkCoupon('NOPE' + x, 'FIXED', 1); r = await c('POST', '/saas/admin/coupons', { name: 'X', type: 'FIXED', discountValue: 1, codeGeneration: 'MANUAL', code: 'X' + x, enabled: true }, t); ck('company admin creates a coupon → 403', r.s === 403, r.s + ' ' + r.code);
console.log(`\n${n - f}/${n} passed`);

// PayPal paths (never calls PayPal)

// SAFETY: PayPal is only ever enabled with fake credentials while the platform currency is one PayPal can't charge,
// so every request stops at the currency check before any HTTP call. PayPal is switched off again in `finally`.
const show = d => JSON.stringify(d)?.slice(0, 260);
const SUPPORTED = ['AUD', 'BRL', 'CAD', 'CNY', 'CZK', 'DKK', 'EUR', 'HKD', 'HUF', 'ILS', 'JPY', 'MYR', 'MXN', 'TWD', 'NZD', 'NOK', 'PHP', 'PLN', 'GBP', 'SGD', 'SEK', 'CHF', 'THB', 'USD'];
const PP = '/settings/admin/payment/paypal';
// Start clean: a secret left by an earlier run would make 'enable without a secret' succeed.
await c('PUT', PP, { enabled: false, mode: 'SANDBOX', clientId: '', clientSecret: '' }, sa);
let r = await c('GET', PP, null, sa); const orig = r.data;
ck('PayPal settings readable, off by default, no secret text', r.s === 200 && orig.enabled === false && !('clientSecret' in (orig || {})), r.s + ' ' + show(orig));
r = await c('GET', '/settings/admin/currency', null, sa); const cur = r.data?.defaultCurrencyCode;
ck('platform default currency known', !!cur, show(r.data));
const plans = (await c('GET', '/saas/plans', null, t)).data; const plan = (plans?.content ?? plans ?? []).find(p => Number(p.monthlyPrice) > 0);
ck('a paid plan to order', !!plan, show((plans?.content ?? plans ?? []).map(p => [p.name, p.monthlyPrice])));
// 1. PayPal off
r = await c('POST', '/saas/orders', { planId: Number(plan.id), billingCycle: 'MONTHLY', paymentMethod: 'PAYPAL' }, t); ck('order with PayPal while off → SAAS_042', r.code === 'SAAS_042', r.s + ' ' + r.code + ' ' + r.msg);
// 2. settings validation and secret handling
r = await c('PUT', PP, { enabled: true, mode: 'SANDBOX', clientId: 'fake-client' }, sa); ck('enable without a secret → SETTINGS_027', r.code === 'SETTINGS_027', r.s + ' ' + r.code);
r = await c('PUT', PP, { enabled: false, mode: 'SANDBOX', clientId: 'fake-client', clientSecret: 'fake-paypal-secret-0001' }, sa);
ck('secret saved (disabled), never returned', r.s === 200 && r.data.hasSecret === true && !show(r.data).includes('fake-paypal'), r.s + ' ' + r.code + ' ' + show(r.data));
r = await c('GET', '/settings/public', null, null); ck('public settings show paypal=false', r.data?.payments?.paypal === false, show(r.data?.payments));
r = await c('PUT', PP, { enabled: false, mode: 'SANDBOX', clientId: 'x', clientSecret: 'y' }, t); ck('company admin on PayPal settings → 403', r.s === 403, r.s + ' ' + r.code);
// 3. unsupported currency — only when it really is unsupported (otherwise skip: it would reach PayPal OAuth)
if (cur && !SUPPORTED.includes(cur.toUpperCase())) {
  try {
    r = await c('PUT', PP, { enabled: true, mode: 'SANDBOX', clientId: 'fake-client' }, sa); ck('PayPal enabled (fake credentials, currency ' + cur + ' unsupported)', r.s === 200 && r.data.enabled === true, r.s + ' ' + r.code);
    r = await c('POST', '/saas/orders', { planId: Number(plan.id), billingCycle: 'MONTHLY', paymentMethod: 'PAYPAL' }, t); const order = r.data?.order ?? r.data;
    ck('PENDING PayPal order created', r.s === 200 && order?.id, r.s + ' ' + r.code + ' ' + r.msg + ' ' + show(r.data));
    r = await c('POST', '/saas/orders/' + order.id + '/paypal/capture', null, t); ck('capture before checkout → SAAS_039 (no HTTP)', r.code === 'SAAS_039', r.s + ' ' + r.code);
    r = await c('POST', '/saas/orders/' + order.id + '/paypal', null, t); ck('checkout in ' + cur + ' → SAAS_043 before any PayPal call', r.code === 'SAAS_043' && (r.msg || '').includes(cur), r.s + ' ' + r.code + ' ' + r.msg);
    r = await c('POST', '/saas/orders/' + order.id + '/paypal', null, B.t); ck("company B checks out A's order → 404 SAAS_008", r.s === 404 && r.code === 'SAAS_008', r.s + ' ' + r.code);
    r = await c('POST', '/saas/orders/' + order.id + '/paypal/capture', null, B.t); ck("company B captures A's order → 404", r.s === 404, r.s + ' ' + r.code);
  } finally {
    r = await c('PUT', PP, { enabled: false, mode: 'SANDBOX', clientId: 'fake-client' }, sa); ck('PayPal switched off again', r.s === 200 && r.data.enabled === false, r.s + ' ' + show(r.data));
  }
} else ck('SKIPPED unsupported-currency step: default currency ' + cur + ' is PayPal-supported (would call PayPal)', true, '');
r = await c('GET', PP, null, sa); ck('PayPal ends disabled', r.data?.enabled === false, show(r.data));
console.log(`\n${n - f}/${n} passed`);

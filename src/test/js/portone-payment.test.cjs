const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('src/main/resources/static/js/reservation/portone-payment.js', 'utf8');

function setup() {
    const nodes = {
        paymentForm: {dataset: {reservationId: '9'}, addEventListener: (_, handler) => {state.submit = handler;}},
        paymentMessage: {textContent: ''}, payButton: {textContent: 'Pay', disabled: false}, buyerPhone: {value: '01012345678'}
    };
    const state = {calls: [], opened: 0};
    const window = {paymentText: {checking: 'Checking', failed: 'Failed', retry: 'Retry', sdk: 'SDK missing'},
        location: {origin: 'https://demo.example', assign: url => {state.redirect = url;}},
        IMP: {init: value => {state.userCode = value;}, request_pay: (params, callback) => {
            state.opened++; state.params = params; state.callback = callback;
        }}};
    const order = {userCode: 'imp_demo', channelKey: 'channel-test', merchantUid: 'KPTEST-order', amount: 17000, currency: 'KRW', name: '[TEST] Venue'};
    const fetch = async (url, request) => {
        state.calls.push({url, request});
        if (url.endsWith('/prepare')) return {ok: true, json: async () => order};
        if (state.failVerification) return {ok: false};
        return {ok: true, json: async () => ({status: 'PAID', nextUrl: '/ko/reservation/complete/9'})};
    };
    vm.runInNewContext(source, {window, document: {getElementById: id => nodes[id] || null,
        querySelector: selector => ({content: selector.includes('_csrf_header') ? 'X-CSRF-TOKEN' : 'csrf-token'})}, fetch, URLSearchParams});
    state.nodes = nodes; state.window = window;
    return state;
}

test('checkout uses server amount and channel; success waits for server verification', async () => {
    const state = setup();
    await state.submit({preventDefault() {}});
    assert.equal(state.params.amount, 17000);
    assert.equal(state.params.channelKey, 'channel-test');
    assert.equal(state.params.m_redirect_url, 'https://demo.example/ko/reservation/payment/9/return');
    assert.equal(state.redirect, undefined);
    await state.callback({success: true, imp_uid: 'imp_test', merchant_uid: 'forged-browser-id'});
    const request = state.calls[1].request;
    assert.equal(JSON.parse(request.body).merchantUid, 'KPTEST-order');
    assert.equal(request.headers['X-CSRF-TOKEN'], 'csrf-token');
    assert.equal(state.redirect, '/ko/reservation/complete/9');
});

test('verification failure offers lookup retry without reopening checkout', async () => {
    const state = setup(); state.failVerification = true;
    await state.submit({preventDefault() {}});
    await state.callback({success: true, imp_uid: 'imp_test'});
    assert.equal(state.redirect, undefined);
    assert.equal(state.nodes.payButton.disabled, false);
    state.failVerification = false;
    await state.submit({preventDefault() {}});
    assert.equal(state.opened, 1);
    assert.equal(state.redirect, '/ko/reservation/complete/9');
});

test('closing checkout never confirms and allows another attempt', async () => {
    const state = setup();
    await state.submit({preventDefault() {}});
    await state.callback({success: false});
    assert.equal(state.calls.length, 1);
    assert.equal(state.redirect, undefined);
    assert.equal(state.nodes.payButton.disabled, false);
});

test('missing SDK cannot prepare or confirm a payment', async () => {
    const state = setup(); state.window.IMP = undefined;
    await state.submit({preventDefault() {}});
    assert.equal(state.calls.length, 0);
    assert.equal(state.nodes.paymentMessage.textContent, 'SDK missing');
});

test('disabled checkout does not issue requests', async () => {
    const state = setup(); state.nodes.payButton.disabled = true;
    await state.submit({preventDefault() {}});
    assert.equal(state.calls.length, 0);
});

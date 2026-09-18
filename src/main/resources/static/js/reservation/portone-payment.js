(() => {
    const form = document.getElementById('paymentForm');
    const result = document.getElementById('paymentReturn');
    if (!form && !result) return;
    const id = (form || result).dataset.reservationId;
    const message = document.getElementById('paymentMessage');
    const text = window.paymentText;
    const headers = {'Content-Type': 'application/json',
        [document.querySelector('meta[name="_csrf_header"]').content]: document.querySelector('meta[name="_csrf"]').content};
    let pending = null;
    async function post(path, body) {
        const response = await fetch(`/api/reservations/${encodeURIComponent(id)}/payment/${path}`, {
            method: 'POST', headers, body: body ? JSON.stringify(body) : undefined});
        if (!response.ok) throw new Error(text.retry);
        return response.json();
    }
    async function verify(impUid, merchantUid) {
        if (!impUid || !merchantUid) throw new Error(text.failed);
        message.textContent = text.checking;
        const data = await post('verify', {impUid, merchantUid, paymentMethod: 'CARD'});
        window.location.assign(data.nextUrl);
    }
    if (form) {
        const button = document.getElementById('payButton');
        const originalLabel = button.textContent;
        form.addEventListener('submit', async event => {
            event.preventDefault();
            if (button.disabled) return;
            button.disabled = true;
            try {
                if (pending) { await verify(pending.imp_uid, pending.merchant_uid); return; }
                if (!window.IMP) throw new Error(text.sdk);
                const order = await post('prepare');
                window.IMP.init(order.userCode);
                window.IMP.request_pay({channelKey: order.channelKey,
                    merchant_uid: order.merchantUid, amount: order.amount, currency: order.currency,
                    name: order.name, pay_method: 'card', buyer_tel: document.getElementById('buyerPhone').value,
                    m_redirect_url: `${window.location.origin}/ko/reservation/payment/${encodeURIComponent(id)}/return`
                }, async response => {
                    try {
                        // A canceled checkout can still return an imp_uid for the failed attempt.
                        // Only an actual/unknown payment result needs server verification retry.
                        if (response.success === false || response.error_code) {
                            pending = null;
                            throw new Error(text.failed);
                        }
                        if (!response.imp_uid) throw new Error(text.failed);
                        pending = {imp_uid: response.imp_uid, merchant_uid: order.merchantUid};
                        await verify(pending.imp_uid, pending.merchant_uid);
                    } catch (error) {
                        message.textContent = error.message;
                        button.textContent = pending ? text.retry : originalLabel;
                        button.disabled = false;
                    }
                });
            } catch (error) {
                message.textContent = error.message;
                button.textContent = pending ? text.retry : originalLabel;
                button.disabled = false;
            }
        });
    }
    if (result) {
        const query = new URLSearchParams(window.location.search);
        const retry = document.getElementById('retryVerification');
        async function check() {
            retry.disabled = true;
            try { await verify(query.get('imp_uid'), query.get('merchant_uid')); }
            catch (error) { message.textContent = error.message; retry.disabled = false; }
        }
        retry.addEventListener('click', check);
        check();
    }
})();

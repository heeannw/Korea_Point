(() => {
    const form = document.getElementById('chargeForm');
    if (!form) return;
    const message = document.getElementById('chargeMessage');
    const button = document.getElementById('chargeButton');
    const originalLabel = button.textContent;
    const text = window.pointsText;
    const headers = {
        'Content-Type': 'application/json',
        [document.querySelector('meta[name="_csrf_header"]').content]: document.querySelector('meta[name="_csrf"]').content
    };

    function selectedAmount() {
        const checked = form.querySelector('input[name="amount"]:checked');
        return checked ? Number(checked.value) : 0;
    }

    document.querySelectorAll('.tier-option input[type="radio"]').forEach(input => {
        input.addEventListener('change', () => {
            document.querySelectorAll('.tier-option').forEach(el => el.classList.toggle('active', el.contains(input) && input.checked));
        });
    });

    async function post(path, body) {
        const response = await fetch(`/api/points/charge/${path}`, {
            method: 'POST', headers, body: body ? JSON.stringify(body) : undefined
        });
        if (!response.ok) throw new Error(text.failed);
        return response.json();
    }

    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (button.disabled) return;
        const amount = selectedAmount();
        if (!amount) {
            message.textContent = text.selectAmount;
            return;
        }
        button.disabled = true;
        message.textContent = '';
        try {
            if (!window.IMP) throw new Error(text.sdk);
            const order = await post('prepare', {amount});
            window.IMP.init(order.userCode);
            window.IMP.request_pay({
                channelKey: order.channelKey,
                merchant_uid: order.merchantUid,
                amount: order.amount,
                currency: order.currency,
                name: order.name,
                pay_method: 'card',
                buyer_tel: document.getElementById('buyerPhone').value
            }, async response => {
                try {
                    if (response.success === false || response.error_code) throw new Error(text.failed);
                    if (!response.imp_uid) throw new Error(text.failed);
                    message.textContent = text.checking;
                    await post('verify', {impUid: response.imp_uid, merchantUid: order.merchantUid});
                    location.href = `/ko/mypage?lang=${encodeURIComponent(document.documentElement.lang || 'ko')}`;
                } catch (error) {
                    message.textContent = error.message;
                    button.disabled = false;
                    button.textContent = originalLabel;
                }
            });
        } catch (error) {
            message.textContent = error.message;
            button.disabled = false;
            button.textContent = originalLabel;
        }
    });
})();

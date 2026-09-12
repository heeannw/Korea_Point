(() => {
    if (document.body.dataset.page !== 'reservationRequests') return;
    const list = document.getElementById('reservationRequestsList');
    const message = document.getElementById('reservationRequestsMessage');
    const previous = document.getElementById('reservationRequestsPrevious');
    const next = document.getElementById('reservationRequestsNext');
    let page = 0, busy = false;
    const labels = {PENDING:'승인 대기',APPROVED:'승인 · 결제 대기',CONFIRMED:'예약 확정',REJECTED:'신청 반려',FREE:'무료 이용 안내',EXPIRED:'결제 기한 만료',CANCELED:'예약 취소'};
    const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
    async function load(target = 0) {
        if (busy) return; busy = true;
        previous.disabled = next.disabled = true;
        message.textContent = '신청을 불러오는 중입니다.'; list.replaceChildren();
        try {
            const params = new URLSearchParams({page:target,pendingOnly:document.getElementById('reservationRequestsPending').value});
            const response = await fetch('/admin/api/reservation-requests?' + params, {headers:{Accept:'application/json'}});
            if (!response.ok) throw Error('신청을 불러오지 못했습니다.');
            const data = await response.json(); page = data.number;
            for (const item of data.content) {
                const form = document.createElement('form'); form.className = 'dashboard-card place-policy-form';
                form.innerHTML = `<h3>${escape(item.placeTitle)}</h3><p>${escape(item.reservationNo)} · 회원 ID ${escape(item.userId)}</p>
                    <p>${escape(item.reservationDate)} ${escape(item.reservationTime)} · ${item.peopleCount}명</p>
                    <p>${escape(item.memo)}</p><strong>${escape(labels[item.state] || item.state)}</strong>
                    <p>${escape(item.reviewNote)}</p>
                    ${item.amount && item.state !== 'PENDING' ? `<p>총 예약금 ${Number(item.amount).toLocaleString('ko-KR')}원</p>` : ''}
                    ${item.paymentDeadline ? `<p>결제 기한: ${escape(item.paymentDeadline.replace('T',' '))}</p>` : ''}
                    ${item.state === 'PENDING' ? `<div class="filters">
                        <label>검토 결과<select name="decision"><option value="APPROVE">승인</option><option value="REJECT">반려</option><option value="FREE">무료 이용 안내로 종료</option></select></label>
                        <label>총 예약금 (원)<input name="amount" type="number" min="1" max="10000000" step="1" required placeholder="총 결제금액 입력"></label>
                        <label>신청자에게 안내할 내용<input name="note" required maxlength="1000" placeholder="확인한 요금과 이용 안내 또는 반려 사유"></label>
                        <button class="primary-button" type="submit">검토 결과 저장</button>
                    </div><p class="review-result" role="status"></p>` : ''}
                    <button class="ghost-button review-place-detail" type="button">장소 정보 확인</button><div class="policy-detail-content" hidden></div>`;
                if (item.state === 'PENDING') {
                    const amount = form.elements.amount;
                    form.elements.decision.addEventListener('change', () => {
                        amount.disabled = form.elements.decision.value !== 'APPROVE'; amount.required = !amount.disabled;
                    });
                    form.addEventListener('submit', async event => {
                        event.preventDefault(); const button = form.querySelector('[type="submit"]'); button.disabled = true;
                        const result = form.querySelector('.review-result'); result.textContent = '저장 중입니다.';
                        try {
                            const header = document.querySelector('meta[name="_csrf_header"]').content;
                            const csrf = document.querySelector('meta[name="_csrf"]').content;
                            const response = await fetch(`/admin/api/reservation-requests/${item.id}/review`, {
                                method:'POST',headers:{'Content-Type':'application/json',[header]:csrf},
                                body:JSON.stringify({decision:form.elements.decision.value,amount:amount.disabled?null:Number(amount.value),note:form.elements.note.value})});
                            if (!response.ok) throw Error(response.status === 409 ? '이미 처리되었거나 방문 시각 또는 장소 설정이 변경된 신청입니다. 조회를 눌러 확인해 주세요.' : '저장하지 못했습니다. 장소 설정과 입력 내용을 확인해 주세요.');
                            const saved = await response.json();
                            result.textContent = labels[saved.state] + ' 처리했습니다.';
                            form.querySelectorAll('input, select, [type="submit"]').forEach(control => {control.disabled = true;});
                        } catch(error) {result.textContent = error.message; button.disabled = false;}
                    });
                }
                form.querySelector('.review-place-detail').addEventListener('click', async event => {
                    const button = event.currentTarget, detail = form.querySelector('.policy-detail-content');
                    button.disabled = true; detail.hidden = false; detail.textContent = '정보를 불러오는 중입니다.';
                    try {
                        const response = await fetch('/api/tourapi/places/' + encodeURIComponent(item.contentId) + '?lang=ko');
                        if (!response.ok) throw Error('장소 정보를 불러오지 못했습니다.');
                        const place = await response.json();
                        detail.textContent = [place.overview,...Object.entries(place.operatingInformation || {}).map(([k,v])=>`${k}: ${v}`)].filter(Boolean).join('\n');
                    } catch(error) {detail.textContent = error.message;} finally {button.disabled = false;}
                });
                list.append(form);
            }
            message.textContent = data.totalElements ? `총 ${data.totalElements}건` : '신청이 없습니다.';
            document.getElementById('reservationRequestsPage').textContent = `${page+1} / ${Math.max(1,data.totalPages)}`;
            previous.disabled = page === 0; next.disabled = data.last;
        } catch(error) {message.textContent = error.message;} finally {busy = false;}
    }
    document.getElementById('reservationRequestsFilter').addEventListener('submit', event => {event.preventDefault(); load();});
    previous.addEventListener('click',()=>load(page-1)); next.addEventListener('click',()=>load(page+1)); load();
})();

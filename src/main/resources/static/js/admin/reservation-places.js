(() => {
    if (document.body.dataset.page !== 'reservationPlaces') return;
    const list = document.getElementById('reservationPlacesList');
    const message = document.getElementById('reservationPlacesMessage');
    const previous = document.getElementById('reservationPlacesPrevious');
    const next = document.getElementById('reservationPlacesNext');
    let currentPage = 0;
    let loading = false;
    const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));

    async function load(page = 0) {
        if (loading) return;
        loading = true;
        previous.disabled = next.disabled = true;
        list.replaceChildren();
        message.textContent = '장소를 불러오는 중입니다.';
        try {
            const params = new URLSearchParams({page,
                keyword: document.getElementById('reservationPlacesKeyword').value,
                contentTypeId: document.getElementById('reservationPlacesType').value});
            const response = await fetch('/admin/api/reservation-places?' + params, {headers: {Accept:'application/json'}});
            if (!response.ok || !response.headers.get('content-type')?.includes('application/json')) throw Error('목록을 불러오지 못했습니다. 로그인 상태와 TourAPI 연결을 확인해 주세요.');
            const data = await response.json();
            currentPage = data.page;
            for (const place of data.places) {
                const policy = data.policies[place.contentId] || {};
                const form = document.createElement('form');
                form.className = 'dashboard-card place-policy-form';
                form.innerHTML = `<h3>${escape(place.title)}</h3><p>${escape(place.address)} · ID ${escape(place.contentId)}</p>
                    <div class="filters">
                        <label>요금 구분<select name="feeStatus">
                            <option value="UNKNOWN">요금 미확인</option><option value="FREE">무료</option><option value="PAID">유료</option>
                        </select></label>
                        <label>예약 허용<select name="reservationEnabled"><option value="false">예약 차단</option><option value="true">예약 허용</option></select></label>
                        <label>확인한 요금 및 근거<input name="feeNote" required maxlength="1000" placeholder="예: 성인 10,000원, 공식 홈페이지 확인" value="${escape(policy.feeNote)}"></label>
                        <button class="primary-button" type="submit">저장</button>
                        <button class="ghost-button policy-detail" type="button">장소 정보 확인</button>
                    </div><p class="policy-save-status" role="status"></p><div class="policy-detail-content" hidden></div>`;
                const fee = form.elements.feeStatus, enabled = form.elements.reservationEnabled;
                fee.value = policy.feeStatus || 'UNKNOWN';
                enabled.value = String(policy.reservationEnabled === true);
                function sync() {
                    enabled.disabled = fee.value !== 'PAID';
                    enabled.options[0].textContent = fee.value === 'UNKNOWN' ? '신청 접수 · 개별 검토' : '예약 차단';
                    if (enabled.disabled) enabled.value = 'false';
                }
                sync(); fee.addEventListener('change', sync);
                let version = policy.version ?? null;
                const status = form.querySelector('.policy-save-status');
                status.textContent = policy.reviewedAt ? '최근 확인: ' + policy.reviewedAt.replace('T',' ') : '아직 확인하지 않은 장소입니다.';
                form.addEventListener('input', () => {status.textContent = '변경한 설정을 적용하려면 저장해 주세요.';});
                form.addEventListener('change', () => {status.textContent = '변경한 설정을 적용하려면 저장해 주세요.';});
                form.addEventListener('submit', async event => {
                    event.preventDefault();
                    const button = form.querySelector('[type="submit"]');
                    button.disabled = true;
                    status.textContent = '저장 중입니다.';
                    try {
                        const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;
                        const csrf = document.querySelector('meta[name="_csrf"]').content;
                        const response = await fetch('/admin/api/reservation-places/' + encodeURIComponent(place.contentId), {
                            method:'PUT', headers:{'Content-Type':'application/json',[csrfHeader]:csrf},
                            body:JSON.stringify({feeStatus:fee.value, reservationEnabled:enabled.value === 'true', feeNote:form.elements.feeNote.value, version})});
                        if (!response.ok) throw Error(response.status === 409 ? '다른 변경이 있습니다. 검색을 눌러 최신 설정을 불러와 주세요.' : '저장하지 못했습니다. 입력 내용과 로그인 상태를 확인해 주세요.');
                        const saved = await response.json(); version = saved.version;
                        status.textContent = '저장했습니다. ' + (saved.bookable ? '예약이 허용됩니다.' : '예약이 차단됩니다.');
                    } catch (error) {status.textContent = error.message;} finally {button.disabled = false;}
                });
                form.querySelector('.policy-detail').addEventListener('click', async event => {
                    const button = event.currentTarget;
                    const details = form.querySelector('.policy-detail-content');
                    details.hidden = false; details.textContent = '정보를 확인하는 중입니다.'; button.disabled = true;
                    try {
                        const response = await fetch('/api/tourapi/places/' + encodeURIComponent(place.contentId) + '?lang=ko');
                        if (!response.ok) throw Error('장소 정보를 불러오지 못했습니다.');
                        const detail = await response.json();
                        details.replaceChildren();
                        window.renderAdminPlaceMap(details, detail);
                        const information = document.createElement('p');
                        information.textContent = [detail.overview, ...Object.entries(detail.operatingInformation || {}).map(([key,value]) => key + ': ' + value)].filter(Boolean).join('\n');
                        details.append(information);
                    } catch(error) {details.textContent = error.message;} finally {button.disabled = false;}
                });
                list.append(form);
            }
            message.textContent = data.total ? `총 ${data.total}곳` : '조회된 장소가 없습니다. 검색 조건 또는 TourAPI 연결을 확인해 주세요.';
            document.getElementById('reservationPlacesPage').textContent = `${currentPage + 1} / ${Math.max(1,Math.ceil(data.total / 20))}`;
            previous.disabled = currentPage === 0; next.disabled = (currentPage + 1) * 20 >= data.total;
        } catch(error) {message.textContent = error.message;} finally {loading = false;}
    }
    document.getElementById('reservationPlacesFilter').addEventListener('submit', event => {event.preventDefault(); load();});
    previous.addEventListener('click', () => load(currentPage - 1));
    next.addEventListener('click', () => load(currentPage + 1));
    load();
})();

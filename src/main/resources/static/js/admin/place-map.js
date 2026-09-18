(() => {
    let sdk;
    function loadMaps() {
        if (sdk) return sdk;
        sdk = new Promise((resolve, reject) => {
            const key = document.getElementById('adminKakaoMap').dataset.key;
            if (!key) { reject(new Error('Map key missing')); return; }
            const script = document.createElement('script');
            const timeout = setTimeout(() => reject(new Error('Map timeout')), 15000);
            script.src = 'https://dapi.kakao.com/v2/maps/sdk.js?autoload=false&appkey=' + encodeURIComponent(key);
            script.onload = () => {
                if (!window.kakao?.maps) { clearTimeout(timeout); reject(new Error('Map unavailable')); return; }
                window.kakao.maps.load(() => { clearTimeout(timeout); resolve(window.kakao.maps); });
            };
            script.onerror = () => { clearTimeout(timeout); reject(new Error('Map unavailable')); };
            document.head.append(script);
        }).catch(error => { sdk = null; throw error; });
        return sdk;
    }
    window.renderAdminPlaceMap = (container, place) => {
        const address = document.createElement('p');
        address.textContent = place.address || '등록된 주소가 없습니다.';
        container.append(address);
        const lat = Number(place.mapY), lng = Number(place.mapX);
        const canvas = document.createElement('div');
        container.append(canvas);
        if (!place.mapY || !place.mapX || !Number.isFinite(lat) || !Number.isFinite(lng)
                || Math.abs(lat) >= 90 || Math.abs(lng) >= 180 || (lat === 0 && lng === 0)) {
            canvas.textContent = '위치 좌표가 없어 지도를 표시할 수 없습니다.';
            return;
        }
        canvas.style.cssText = 'width:100%;height:320px;border-radius:12px;overflow:hidden;white-space:normal';
        canvas.setAttribute('aria-label', `${place.title || '장소'} 위치 지도`);
        canvas.textContent = '지도를 불러오는 중입니다.';
        const link = document.createElement('a');
        link.href = `https://map.kakao.com/link/map/${encodeURIComponent(place.title || '장소')},${lat},${lng}`;
        link.target = '_blank'; link.rel = 'noopener noreferrer'; link.textContent = '카카오맵에서 크게 보기';
        container.append(link);
        loadMaps().then(maps => {
            if (!canvas.isConnected) return;
            canvas.replaceChildren();
            const position = new maps.LatLng(lat, lng);
            const map = new maps.Map(canvas, {center: position, level: 3});
            new maps.Marker({map, position});
        }).catch(() => { canvas.textContent = '지도를 불러오지 못했습니다. 아래 카카오맵 링크에서 확인해 주세요.'; });
    };
})();

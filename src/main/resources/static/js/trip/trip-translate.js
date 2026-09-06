function googleTranslateElementInit() {
    new google.translate.TranslateElement({pageLanguage: 'ko', includedLanguages: 'ko,en,ja,zh-CN,fr', autoDisplay: false}, 'google_translate_element');
    applyGoogleLanguage(localStorage.getItem('tripLanguage') || 'ko');
}

function changeTripLanguage(language) {
    localStorage.setItem('tripLanguage', language);

    // trip에서 언어를 바꾸면 다른 페이지(관광/챗봇/홈)가 같이 보는 세션 로케일도 맞춰준다 —
    // 안 그러면 여기서 바꾼 언어가 다른 페이지로 넘어갈 때 반영이 안 된다. trip은 중국어를
    // "zh-CN"으로 쓰지만 세션 로케일 쪽은 "zh"라서 매핑해서 보낸다.
    const sessionLang = language === 'zh-CN' ? 'zh' : language;
    const withLangParam = (url) => {
        const u = new URL(url, location.origin);
        u.searchParams.set('lang', sessionLang);
        return u.toString();
    };

    const isHomePage = ['/', '/ko', '/kr', '/en'].includes(location.pathname);
    if (language === 'ko') {
        document.cookie = 'googtrans=; Max-Age=0; path=/';
        location.href = withLangParam(isHomePage ? '/ko' : location.href);
        return;
    }
    if (language === 'en' && isHomePage) {
        document.cookie = 'googtrans=; Max-Age=0; path=/';
        location.href = withLangParam('/en');
        return;
    }

    document.cookie = `googtrans=/ko/${language}; path=/`;

    // 일본어와 중국어는 한국어 원문 페이지를 기준으로 자동 번역한다.
    if (isHomePage && location.pathname !== '/ko' && location.pathname !== '/kr') {
        location.href = withLangParam('/ko');
        return;
    }
    // 여기로 오면 페이지 새로고침 없이 구글 번역만 그 자리에서 바로 적용하는 경우라,
    // 세션 로케일 동기화는 백그라운드 요청으로 따로 보내둔다(페이지 이동이 없어서
    // 자연스러운 새로고침에 얹을 방법이 없음).
    fetch(withLangParam(location.href), { credentials: 'same-origin' }).catch(() => {});
    applyGoogleLanguage(language);
}

function applyGoogleLanguage(language, attempt = 0) {
    if (language === 'ko') return;
    const googleSelect = document.querySelector('.goog-te-combo');
    if (googleSelect) {
        googleSelect.value = language;
        googleSelect.dispatchEvent(new Event('change', {bubbles: true}));
        return;
    }
    if (attempt < 40) setTimeout(() => applyGoogleLanguage(language, attempt + 1), 250);
}

document.addEventListener('DOMContentLoaded', () => {
    // 관광/챗봇/홈 등 다른 페이지는 전부 세션 로케일(?lang=) 하나를 공유하는데, trip만 이 언어 설정을
    // localStorage에 따로 저장해서 안 맞았다. 페이지가 세션 로케일을 window.SERVER_LANG으로 심어주면
    // (다른 페이지에서 방금 언어를 바꾸고 넘어온 경우) 그걸 우선해서 localStorage를 맞춰준다.
    if (window.SERVER_LANG && window.SERVER_LANG !== localStorage.getItem('tripLanguage')) {
        localStorage.setItem('tripLanguage', window.SERVER_LANG);
        // localStorage만 맞추고 googtrans 쿠키를 안 건드리면, 예전에 다른 언어로 바꿔봤을 때 남은
        // 쿠키를 구글 번역 스크립트가 그대로 읽어서 "언어 선택창은 한국어인데 본문은 번역돼서 영어로
        // 나오는" 것처럼 보이는 문제가 있었다 — 세션 로케일에 맞춰 쿠키도 같이 정리한다.
        if (window.SERVER_LANG === 'ko') {
            document.cookie = 'googtrans=; Max-Age=0; path=/';
        } else {
            document.cookie = `googtrans=/ko/${window.SERVER_LANG}; path=/`;
        }
    }
    const savedLanguage = localStorage.getItem('tripLanguage') || 'ko';
    const isHomePage = ['/', '/ko', '/kr', '/en'].includes(location.pathname);

    // 저장된 언어와 실제 홈 템플릿이 다르면 올바른 원문 페이지로 이동한다.
    if (isHomePage) {
        if (savedLanguage === 'ko' && location.pathname !== '/ko' && location.pathname !== '/kr') {
            location.replace('/ko');
            return;
        }
        if (savedLanguage === 'en' && location.pathname !== '/en') {
            location.replace('/en');
            return;
        }
        if (['ja', 'zh-CN'].includes(savedLanguage)
            && location.pathname !== '/ko' && location.pathname !== '/kr') {
            location.replace('/ko');
            return;
        }
    }

    let select = document.getElementById('languageSelect');
    if (!select) {
        const headerRight = document.querySelector('header .header-right');
        if (headerRight) {
            const languageButtons = [...headerRight.querySelectorAll(':scope > a.h-btn, :scope > button.h-btn')]
                .filter(element => ['EN', 'KR'].includes(element.textContent.trim().toUpperCase()));
            languageButtons.forEach(element => element.remove());

            select = document.createElement('select');
            select.id = 'languageSelect';
            select.className = 'h-btn';
            select.setAttribute('translate', 'no');
            select.setAttribute('aria-label', '언어 선택');
            select.innerHTML = '<option value="ko">한국어</option><option value="en">English</option><option value="ja">日本語</option><option value="zh-CN">中文</option><option value="fr">Français</option>';
            select.addEventListener('change', event => changeTripLanguage(event.target.value));
            headerRight.prepend(select);
        }
    }

    if (!document.getElementById('google_translate_element')) {
        const translateElement = document.createElement('div');
        translateElement.id = 'google_translate_element';
        translateElement.style.display = 'none';
        document.body.prepend(translateElement);
    }

    const bannerStyle = document.createElement('style');
    bannerStyle.textContent = `
        body { top: 0 !important; position: static !important; }
        #google_translate_element,
        .goog-te-gadget,
        .goog-te-gadget-simple,
        .goog-te-gadget-icon,
        .goog-logo-link,
        .VIpgJd-ZVi9od-l4eHX-hSRGPd,
        .goog-te-banner-frame,
        .goog-te-banner-frame.skiptranslate,
        .VIpgJd-ZVi9od-ORHb,
        .VIpgJd-ZVi9od-ORHb-OEVmcd,
        body > .skiptranslate {
            display: none !important;
            width: 0 !important;
            height: 0 !important;
            overflow: hidden !important;
            visibility: hidden !important;
        }
    `;
    document.head.appendChild(bannerStyle);

    new MutationObserver(() => {
        document.body.style.setProperty('top', '0px', 'important');
        document.body.style.setProperty('position', 'static', 'important');
        document.querySelectorAll('#google_translate_element, .goog-te-gadget, .goog-te-gadget-simple, .goog-te-banner-frame, .VIpgJd-ZVi9od-ORHb, .VIpgJd-ZVi9od-ORHb-OEVmcd').forEach(element => {
            element.style.setProperty('display', 'none', 'important');
        });
    }).observe(document.documentElement, {childList: true, subtree: true});

    if (select) select.value = savedLanguage;
    const script = document.createElement('script');
    script.src = 'https://translate.google.com/translate_a/element.js?cb=googleTranslateElementInit';
    script.async = true;
    document.head.appendChild(script);
});

function googleTranslateElementInit() {
    new google.translate.TranslateElement({pageLanguage: 'ko', includedLanguages: 'ko,en,ja,zh-CN', autoDisplay: false}, 'google_translate_element');
    applyGoogleLanguage(localStorage.getItem('tripLanguage') || 'ko');
}

/**
 * 이동할 주소에 ?lang= 을 실어 준다.
 *
 * 서버의 LocaleChangeInterceptor 가 이 값을 보고 세션 로케일을 바꾼다.
 * 이걸 빼면 홈에서 언어를 바꿔도 세션은 그대로라, 다른 페이지로 넘어가는 순간
 * 이전 언어로 돌아간다.
 */
function withLang(url, language) {
    const target = new URL(url, location.origin);
    target.searchParams.set('lang', language);
    return target.pathname + target.search + target.hash;
}

function changeTripLanguage(language) {
    localStorage.setItem('tripLanguage', language);

    const isHomePage = ['/', '/ko', '/kr', '/en'].includes(location.pathname);
    if (language === 'ko') {
        document.cookie = 'googtrans=; Max-Age=0; path=/';
        location.href = withLang(isHomePage ? '/ko' : location.href, 'ko');
        return;
    }
    if (language === 'en' && isHomePage) {
        document.cookie = 'googtrans=; Max-Age=0; path=/';
        location.href = withLang('/en', 'en');
        return;
    }

    document.cookie = `googtrans=/ko/${language}; path=/`;

    // 일본어와 중국어는 한국어 원문 페이지를 기준으로 자동 번역한다.
    if (isHomePage && location.pathname !== '/ko' && location.pathname !== '/kr') {
        location.href = withLang('/ko', language);
        return;
    }
    // 다른 페이지에서도 세션 로케일을 맞춰야 해서 한 번 이동한다.
    // googtrans 는 쿠키라 이동해도 번역 상태가 유지된다.
    location.href = withLang(location.href, language);
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
    const savedLanguage = localStorage.getItem('tripLanguage') || 'ko';

    // 홈의 언어 판단은 서버 세션 로케일이 맡는다(HomeController).
    // 예전에는 여기서 localStorage 를 보고 홈을 강제로 옮겼는데, 공용 헤더는
    // 세션만 갱신하므로 두 기억이 어긋나 "다른 페이지에서 바꾼 언어가 홈에서 되돌아가는"
    // 문제가 있었다.

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
            select.innerHTML = '<option value="ko">한국어</option><option value="en">English</option><option value="ja">日本語</option><option value="zh-CN">中文</option>';
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

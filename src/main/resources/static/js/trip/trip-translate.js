function googleTranslateElementInit() {
    new google.translate.TranslateElement({pageLanguage: 'ko', includedLanguages: 'ko,en,ja,zh-CN', autoDisplay: false}, 'google_translate_element');
    applyGoogleLanguage(localStorage.getItem('tripLanguage') || 'ko');
}

function changeTripLanguage(language) {
    localStorage.setItem('tripLanguage', language);
    if (language === 'ko') {
        document.cookie = 'googtrans=; Max-Age=0; path=/';
        location.reload();
        return;
    }
    document.cookie = `googtrans=/ko/${language}; path=/`;
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
    const bannerStyle = document.createElement('style');
    bannerStyle.textContent = `
        body { top: 0 !important; position: static !important; }
        .goog-te-banner-frame,
        .goog-te-banner-frame.skiptranslate,
        .VIpgJd-ZVi9od-ORHb,
        .VIpgJd-ZVi9od-ORHb-OEVmcd,
        body > .skiptranslate { display: none !important; height: 0 !important; visibility: hidden !important; }
    `;
    document.head.appendChild(bannerStyle);

    new MutationObserver(() => {
        document.body.style.setProperty('top', '0px', 'important');
        document.body.style.setProperty('position', 'static', 'important');
        document.querySelectorAll('.goog-te-banner-frame, .VIpgJd-ZVi9od-ORHb, .VIpgJd-ZVi9od-ORHb-OEVmcd').forEach(element => {
            element.style.setProperty('display', 'none', 'important');
        });
    }).observe(document.documentElement, {childList: true, subtree: true});

    const select = document.getElementById('languageSelect');
    if (select) select.value = localStorage.getItem('tripLanguage') || 'ko';
    const script = document.createElement('script');
    script.src = 'https://translate.google.com/translate_a/element.js?cb=googleTranslateElementInit';
    script.async = true;
    document.head.appendChild(script);
});

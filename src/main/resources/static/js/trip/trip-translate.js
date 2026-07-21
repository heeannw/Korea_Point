function googleTranslateElementInit() {
    new google.translate.TranslateElement({pageLanguage: 'ko', includedLanguages: 'ko,en,ja,zh-CN', autoDisplay: false}, 'google_translate_element');
}

function changeTripLanguage(language) {
    localStorage.setItem('tripLanguage', language);
    const value = language === 'ko' ? '' : `/ko/${language}`;
    const expires = language === 'ko' ? '; expires=Thu, 01 Jan 1970 00:00:00 GMT' : '';
    document.cookie = `googtrans=${value}; path=/${expires}`;
    document.cookie = `googtrans=${value}; path=/; domain=${location.hostname}${expires}`;
    location.reload();
}

document.addEventListener('DOMContentLoaded', () => {
    const select = document.getElementById('languageSelect');
    if (select) select.value = localStorage.getItem('tripLanguage') || 'ko';
    const script = document.createElement('script');
    script.src = 'https://translate.google.com/translate_a/element.js?cb=googleTranslateElementInit';
    script.async = true;
    document.head.appendChild(script);
});

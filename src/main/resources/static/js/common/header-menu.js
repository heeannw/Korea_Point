/* header-menu.js — 공용 헤더 드롭다운(유저 메뉴 / 언어)
   fragment: templates/fragments/app-header.html · CSS: /css/common/header-menu.css */
(function () {
  window.toggleHeaderMenu = function (e, btn) {
    if (e) e.stopPropagation();
    var menu = btn.closest('.user-menu, .lang-menu');
    var pop = menu.querySelector('[role="menu"]');
    var willOpen = pop.hidden;
    window.closeHeaderMenus();
    pop.hidden = !willOpen;
    menu.classList.toggle('open', willOpen);
    btn.setAttribute('aria-expanded', String(willOpen));
  };

  window.closeHeaderMenus = function () {
    document.querySelectorAll('.user-menu, .lang-menu').forEach(function (m) {
      m.classList.remove('open');
      var p = m.querySelector('[role="menu"]');
      if (p) p.hidden = true;
      var b = m.querySelector('button[aria-haspopup]');
      if (b) b.setAttribute('aria-expanded', 'false');
    });
  };

  window.switchLang = function (newLang) {
    var url = new URL(window.location.href);
    url.searchParams.set('lang', newLang);
    window.location.href = url.toString();
  };

  document.addEventListener('click', function (e) {
    if (!e.target.closest('.user-menu, .lang-menu')) window.closeHeaderMenus();
  });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') window.closeHeaderMenus();
  });
})();

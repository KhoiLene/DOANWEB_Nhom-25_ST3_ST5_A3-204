(() => {
  const storageKey = 'techtra.color-theme';
  const root = document.documentElement;

  function readTheme() {
    try {
      return localStorage.getItem(storageKey) === 'dark' ? 'dark' : 'light';
    } catch (_) {
      return 'light';
    }
  }

  function applyTheme(theme) {
    const isDark = theme === 'dark';
    root.dataset.theme = isDark ? 'dark' : 'light';
    document.querySelectorAll('[data-theme-toggle]').forEach(button => {
      button.setAttribute('aria-label', isDark ? 'Chuyển sang giao diện sáng' : 'Chuyển sang giao diện tối');
      button.setAttribute('aria-pressed', String(isDark));
      const icon = button.querySelector('[data-theme-icon]');
      if (icon) icon.textContent = isDark ? '☀' : '☾';
      const label = button.querySelector('[data-theme-label]');
      if (label) label.textContent = isDark ? 'Giao diện sáng' : 'Giao diện tối';
    });
  }

  applyTheme(readTheme());
  document.addEventListener('click', event => {
    const button = event.target.closest('[data-theme-toggle]');
    if (!button) return;
    const nextTheme = root.dataset.theme === 'dark' ? 'light' : 'dark';
    applyTheme(nextTheme);
    try { localStorage.setItem(storageKey, nextTheme); } catch (_) { /* Theme still applies for this page. */ }
  });
})();

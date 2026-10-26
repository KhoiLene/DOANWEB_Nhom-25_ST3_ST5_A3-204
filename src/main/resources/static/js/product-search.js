(() => {
  const cache = new Map();
  const fallbackImage = 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 64 64%22%3E%3Crect width=%2264%22 height=%2264%22 rx=%2210%22 fill=%22%23f2f5f8%22/%3E%3Cpath d=%22M23 24h18l-2 24H25l-2-24Zm4 0a5 5 0 0 1 10 0%22 fill=%22none%22 stroke=%22%2397a8bc%22 stroke-width=%223%22/%3E%3C/svg%3E';
  const formatPrice = value => Number.isFinite(Number(value))
    ? new Intl.NumberFormat('vi-VN').format(Number(value)) + ' ₫'
    : '';

  function initSearch(input) {
    const wrapper = input.closest('.header-search, .mobile-search-wrap');
    const panel = wrapper?.querySelector('[data-product-suggestions]');
    if (!panel) return;
    let timer;
    let request;
    let matches = [];
    let activeIndex = -1;

    const hide = () => {
      panel.hidden = true;
      panel.replaceChildren();
      input.setAttribute('aria-expanded', 'false');
      matches = [];
      activeIndex = -1;
    };

    const setActive = index => {
      activeIndex = (index + matches.length) % matches.length;
      const options = panel.querySelectorAll('[role="option"]');
      options.forEach((option, itemIndex) => option.setAttribute('aria-selected', String(itemIndex === activeIndex)));
      options[activeIndex]?.scrollIntoView({ block: 'nearest' });
    };

    const render = items => {
      panel.replaceChildren();
      matches = Array.isArray(items) ? items.slice(0, 6) : [];
      activeIndex = -1;
      if (!matches.length) {
        const empty = document.createElement('p');
        empty.className = 'product-search-suggestions__empty';
        empty.textContent = 'Chưa tìm thấy sản phẩm phù hợp.';
        panel.append(empty);
      } else {
        matches.forEach(product => {
          const link = document.createElement('a');
          link.className = 'product-search-suggestion';
          link.href = '/product/' + encodeURIComponent(product.id);
          link.setAttribute('role', 'option');
          link.setAttribute('aria-selected', 'false');

          const image = document.createElement('img');
          image.src = product.imageUrl || fallbackImage;
          image.alt = '';
          image.loading = 'lazy';
          image.onerror = () => { image.onerror = null; image.src = fallbackImage; };

          const info = document.createElement('span');
          info.className = 'product-search-suggestion__info';
          const name = document.createElement('strong');
          name.textContent = product.name || 'Sản phẩm';
          const price = document.createElement('small');
          price.textContent = formatPrice(product.price);
          info.append(name, price);
          link.append(image, info);
          panel.append(link);
        });
      }
      panel.hidden = false;
      input.setAttribute('aria-expanded', 'true');
    };

    input.addEventListener('input', () => {
      window.clearTimeout(timer);
      request?.abort();
      const keyword = input.value.trim();
      if (keyword.length < 2) { hide(); return; }
      const cacheKey = keyword.toLocaleLowerCase('vi');
      if (cache.has(cacheKey)) {
        render(cache.get(cacheKey));
        return;
      }
      timer = window.setTimeout(async () => {
        request = new AbortController();
        try {
          const response = await fetch('/api/products/search/suggestions?keyword=' + encodeURIComponent(keyword), {
            headers: { Accept: 'application/json' },
            signal: request.signal
          });
          if (!response.ok) throw new Error('Không tìm được gợi ý.');
          const products = await response.json();
          if (input.value.trim() !== keyword) return;
          cache.set(cacheKey, products);
          render(products);
        } catch (error) {
          if (error.name !== 'AbortError' && input.value.trim() === keyword) hide();
        }
      }, 220);
    });

    input.addEventListener('keydown', event => {
      if (panel.hidden || matches.length === 0) return;
      if (event.key === 'ArrowDown') { event.preventDefault(); setActive(activeIndex + 1); }
      else if (event.key === 'ArrowUp') { event.preventDefault(); setActive(activeIndex <= 0 ? matches.length - 1 : activeIndex - 1); }
      else if (event.key === 'Enter' && activeIndex >= 0) {
        event.preventDefault();
        window.location.assign('/product/' + encodeURIComponent(matches[activeIndex].id));
      } else if (event.key === 'Escape') hide();
    });

    input.addEventListener('focus', () => {
      if (input.value.trim().length >= 2 && panel.childElementCount) {
        panel.hidden = false;
        input.setAttribute('aria-expanded', 'true');
      }
    });
    input.form?.addEventListener('submit', () => hide());
    document.addEventListener('click', event => {
      if (!wrapper.contains(event.target)) hide();
    });
  }

  document.querySelectorAll('input[type="search"][name="keyword"]').forEach(initSearch);
})();

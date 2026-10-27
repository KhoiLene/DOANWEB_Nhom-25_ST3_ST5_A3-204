(() => {
  const badgeNodes = () => document.querySelectorAll('[data-cart-badge]');

  function setCartCount(count) {
    badgeNodes().forEach((badge) => {
      badge.textContent = String(count || 0);
      badge.hidden = !count;
    });
  }

  function showMessage(message, kind = 'success') {
    let toast = document.getElementById('storefrontToast');
    if (!toast) {
      toast = document.createElement('div');
      toast.id = 'storefrontToast';
      toast.setAttribute('role', 'status');
      document.body.appendChild(toast);
    }
    toast.textContent = message;
    toast.dataset.kind = kind;
    toast.classList.add('is-visible');
    window.clearTimeout(showMessage.timer);
    showMessage.timer = window.setTimeout(() => toast.classList.remove('is-visible'), 2800);
  }

  function hideBrokenNavigationImage(image) {
    if (!(image instanceof HTMLImageElement) || !image.matches('[data-nav-image]')) return;
    image.hidden = true;
    image.parentElement?.classList.add('has-image-error');
  }

  document.addEventListener('error', event => hideBrokenNavigationImage(event.target), true);
  document.querySelectorAll('img[data-nav-image]').forEach(image => {
    if (image.complete && image.naturalWidth === 0) hideBrokenNavigationImage(image);
  });

  async function refreshCartCount() {
    try {
      const response = await fetch('/api/cart/summary', { credentials: 'same-origin' });
      if (!response.ok) return;
      const summary = await response.json();
      setCartCount(summary.count);
    } catch (_) {
      // The storefront remains usable when the customer is offline.
    }
  }

  async function addToCart(productId, quantity = 1) {
    try {
      const response = await fetch('/api/cart/add', {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ productId: Number(productId), quantity: Number(quantity) })
      });
      if (response.status === 401 || response.status === 403) {
        const returnTo = `${window.location.pathname}${window.location.search}${window.location.hash}`;
        window.location.assign(`/login?returnTo=${encodeURIComponent(returnTo)}`);
        return false;
      }
      const result = await response.json();
      if (!response.ok) throw new Error(result.error || 'Không thể thêm sản phẩm vào giỏ.');
      setCartCount(result.count);
      showMessage(result.message || 'Đã thêm sản phẩm vào giỏ.');
      return true;
    } catch (error) {
      showMessage(error.message || 'Không thể kết nối đến cửa hàng.', 'error');
      return false;
    }
  }

  document.addEventListener('DOMContentLoaded', () => {
    refreshCartCount();

    document.addEventListener('click', async (event) => {
      const addButton = event.target.closest('[data-add-to-cart]');
      if (addButton) {
        event.preventDefault();
        const originalLabel = addButton.textContent;
        addButton.disabled = true;
        addButton.textContent = 'Đang thêm…';
        await addToCart(addButton.dataset.productId, addButton.dataset.quantity || 1);
        addButton.disabled = false;
        addButton.textContent = originalLabel;
        return;
      }

      const joinButton = event.target.closest('[data-promotion-id]');
      if (joinButton) {
        event.preventDefault();
        joinButton.disabled = true;
        try {
          const response = await fetch(`/api/customer/promotions/${encodeURIComponent(joinButton.dataset.promotionId)}/join`, {
            method: 'POST', credentials: 'same-origin'
          });
          if (response.status === 401 || response.status === 403) {
            window.location.assign('/login');
            return;
          }
          const result = await response.json();
          if (!response.ok) throw new Error(result.error || 'Chưa thể tham gia chương trình.');
          const joined = document.createElement('span');
          joined.className = 'joined-promotion';
          joined.innerHTML = '<i class="fa-solid fa-circle-check" aria-hidden="true"></i> Đã tham gia';
          joinButton.replaceWith(joined);
          const message = document.getElementById('promotionMessage');
          if (message) message.textContent = `Bạn đã tham gia “${result.title}”.`;
        } catch (error) {
          const message = document.getElementById('promotionMessage');
          if (message) message.textContent = error.message || 'Chưa thể tham gia chương trình.';
          joinButton.disabled = false;
        }
      }
    });
  });

  window.TechtraStorefront = { addToCart, refreshCartCount, showMessage };
})();

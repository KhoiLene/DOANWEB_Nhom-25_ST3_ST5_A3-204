(() => {
  const root = document.getElementById('store-locator');
  if (!root) return;

  const results = document.getElementById('storeResults');
  const message = document.getElementById('locationMessage');
  const mapMessage = document.getElementById('mapMessage');
  let map;
  let storeMarkers;
  let originMarker;

  const preferredStore = () => { try { return localStorage.getItem('preferredStoreId'); } catch (_) { return null; } };

  function clearMarkers() {
    if (storeMarkers) storeMarkers.clearLayers();
    if (originMarker) { originMarker.remove(); originMarker = null; }
  }

  function showStores(stores, origin) {
    clearMarkers();
    results.replaceChildren();
    if (!stores.length) {
      message.textContent = 'Chưa tìm thấy chi nhánh phù hợp. Đơn giao xa có thể được xử lý từ kho.';
      const empty = document.createElement('p');
      empty.className = 'store-locator__empty';
      empty.textContent = 'Bạn có thể thử địa chỉ khác hoặc tiếp tục đặt giao hàng để hệ thống chọn kho phù hợp.';
      results.append(empty);
      return;
    }

    message.textContent = origin
      ? `Đang hiển thị ${stores.length} chi nhánh gần nhất.`
      : `Đang hiển thị ${stores.length} chi nhánh khớp tìm kiếm.`;
    const bounds = [];
    if (origin && map) {
      originMarker = L.circleMarker([origin.lat, origin.lng], {
        radius: 8, color: '#fff', weight: 2, fillColor: '#2563eb', fillOpacity: 1
      }).addTo(map).bindPopup('Vị trí tìm kiếm');
      bounds.push([origin.lat, origin.lng]);
    }

    stores.forEach((store, index) => {
      const card = document.createElement('article');
      card.className = 'store-locator__card';
      const heading = document.createElement('h3');
      heading.textContent = `${index + 1}. ${store.name ?? ''}`;
      const address = document.createElement('p');
      address.textContent = [store.address, store.district, store.city].filter(Boolean).join(', ');
      card.append(heading, address);
      if (store.phone) {
        const phone = document.createElement('a');
        phone.href = `tel:${String(store.phone).replace(/[^+\d]/g, '')}`;
        phone.textContent = `Gọi ${store.phone}`;
        card.append(phone);
      }
      if (store.distanceKm != null) {
        const distance = document.createElement('span');
        distance.className = 'store-locator__distance';
        distance.textContent = `${Number(store.distanceKm).toFixed(1)} km`;
        card.append(distance);
      }
      const select = document.createElement('button');
      select.type = 'button';
      select.className = 'store-locator__select';
      select.dataset.storeId = store.id;
      select.textContent = preferredStore() === String(store.id)
        ? 'Đã chọn để nhận hàng' : 'Chọn chi nhánh nhận hàng';
      select.addEventListener('click', () => {
        try { localStorage.setItem('preferredStoreId', String(store.id)); } catch (_) { }
        document.querySelectorAll('.store-locator__select').forEach(button => {
          button.textContent = button.dataset.storeId === String(store.id)
            ? 'Đã chọn để nhận hàng' : 'Chọn chi nhánh nhận hàng';
        });
        message.textContent = `Đã lưu ${store.name} làm chi nhánh nhận hàng ưu tiên. Bạn có thể tiếp tục mua sắm.`;
      });
      card.append(select);
      results.append(card);

      if (map && Number.isFinite(store.latitude) && Number.isFinite(store.longitude)) {
        const position = [store.latitude, store.longitude];
        const marker = L.marker(position, { title: store.name })
          .bindPopup(`<strong>${escapeHtml(store.name || '')}</strong><br>${escapeHtml(store.address || '')}`)
          .addTo(storeMarkers);
        marker.bindTooltip(String(index + 1), { permanent: true, direction: 'top', offset: [0, -8] });
        bounds.push(position);
      }
    });

    if (map && bounds.length) {
      if (bounds.length === 1) map.setView(bounds[0], 14);
      else map.fitBounds(bounds, { padding: [40, 40], maxZoom: 14 });
    }
  }

  function escapeHtml(value) {
    return value.replace(/[&<>"']/g, character => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[character]);
  }

  async function fetchStores(url, origin) {
    message.textContent = 'Đang tìm cửa hàng…';
    try {
      const response = await fetch(url, { headers: { Accept: 'application/json' } });
      if (!response.ok) throw new Error('Không thể tìm chi nhánh lúc này.');
      const stores = await response.json();
      showStores(stores, origin);
      return stores;
    } catch (error) {
      message.textContent = error.message || 'Không thể tìm chi nhánh lúc này.';
      return null;
    }
  }

  function byCoordinates(latitude, longitude) {
    const params = new URLSearchParams({ latitude, longitude, limit: '5' });
    return fetchStores(`/api/locations/nearest?${params}`, { lat: latitude, lng: longitude });
  }

  async function geocodeAddress(query) {
    return window.geocodeWithNominatim(query);
  }

  document.getElementById('locateMe').addEventListener('click', () => {
    if (!navigator.geolocation) {
      message.textContent = 'Trình duyệt không hỗ trợ định vị. Hãy nhập địa chỉ để tìm.';
      return;
    }
    message.textContent = 'Trình duyệt sẽ hỏi quyền truy cập vị trí.';
    navigator.geolocation.getCurrentPosition(
      position => byCoordinates(position.coords.latitude, position.coords.longitude),
      () => { message.textContent = 'Không lấy được vị trí. Bạn vẫn có thể nhập địa chỉ để tìm.'; },
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 }
    );
  });

  document.getElementById('addressSearch').addEventListener('submit', async event => {
    event.preventDefault();
    const input = document.getElementById('addressQuery');
    const query = input.value.trim();
    if (!query) return;
    message.textContent = 'Đang xác định địa chỉ với OpenStreetMap…';
    try {
      const result = await geocodeAddress(query);
      if (result.displayName) input.value = result.displayName;
      await byCoordinates(result.latitude, result.longitude);
    } catch (error) {
      const localResults = await fetchStores(`/api/locations/search?query=${encodeURIComponent(query)}`);
      if (localResults?.length) {
        message.textContent = 'Không tra được tọa độ trực tuyến; đang hiển thị chi nhánh khớp từ dữ liệu trong cửa hàng.';
      } else {
        message.textContent = error.message || 'Không thể xác định địa chỉ. Hãy thử lại.';
      }
    }
  });

  function initMap() {
    if (!window.L) {
      mapMessage.textContent = 'Không tải được thư viện bản đồ. Danh sách chi nhánh vẫn dùng được.';
      return;
    }
    map = L.map('storeMap', { scrollWheelZoom: false }).setView([16.0, 107.5], 5);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
    }).addTo(map);
    storeMarkers = L.layerGroup().addTo(map);
    mapMessage.textContent = 'Bản đồ OpenStreetMap. Chọn kết quả để xem ghim chi nhánh gần nhất.';
  }

  initMap();
})();

(() => {
  const root = document.getElementById('store-locator');
  if (!root) return;

  const results = document.getElementById('storeResults');
  const message = document.getElementById('locationMessage');
  const mapMessage = document.getElementById('mapMessage');
  let map;
  let maplibregl;
  let storeMarkers = [];
  let originMarker;

  const preferredStore = () => { try { return localStorage.getItem('preferredStoreId'); } catch (_) { return null; } };

  function clearMarkers() {
    storeMarkers.forEach(marker => marker?.remove());
    storeMarkers = [];
    if (originMarker) { originMarker.remove(); originMarker = null; }
  }

  function createMarker(label, className) {
    const marker = document.createElement('span');
    marker.className = className;
    marker.setAttribute('aria-hidden', 'true');
    const text = document.createElement('span');
    text.textContent = label;
    marker.append(text);
    return marker;
  }

  function addStoreMarker(store, index) {
    if (!map || !Number.isFinite(store.latitude) || !Number.isFinite(store.longitude)) return null;
    const popup = document.createElement('div');
    const title = document.createElement('strong');
    title.textContent = store.name || 'Chi nhánh';
    const address = document.createElement('p');
    address.textContent = [store.address, store.district, store.city].filter(Boolean).join(', ');
    popup.append(title, address);
    const marker = new maplibregl.Marker({ element: createMarker(String(index + 1), 'store-map-marker') })
      .setLngLat([store.longitude, store.latitude])
      .setPopup(new maplibregl.Popup({ offset: 22, closeButton: false }).setDOMContent(popup))
      .addTo(map);
    storeMarkers[index] = marker;
    return [store.longitude, store.latitude];
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
      originMarker = new maplibregl.Marker({ element: createMarker('Bạn', 'store-map-marker store-map-marker--origin') })
        .setLngLat([origin.lng, origin.lat])
        .setPopup(new maplibregl.Popup({ offset: 22, closeButton: false }).setText('Vị trí tìm kiếm'))
        .addTo(map);
      bounds.push([origin.lng, origin.lat]);
    }

    stores.slice(0, 5).forEach((store, index) => {
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
      card.addEventListener('click', event => {
        if (event.target === select || event.target.closest('a')) return;
        storeMarkers[index]?.togglePopup();
      });
      card.append(select);
      results.append(card);

      const position = addStoreMarker(store, index);
      if (position) bounds.push(position);
    });

    if (map && bounds.length) {
      if (bounds.length === 1) map.easeTo({ center: bounds[0], zoom: 13 });
      else {
        const extent = new maplibregl.LngLatBounds();
        bounds.forEach(point => extent.extend(point));
        map.fitBounds(extent, { padding: 48, maxZoom: 13, duration: 450 });
      }
    }
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
      const result = await window.geocodeWithNominatim(query);
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

  async function initMap() {
    try {
      maplibregl = await import('https://unpkg.com/maplibre-gl@6.11.2/dist/maplibre-gl.mjs');
      map = new maplibregl.Map({
        container: 'storeMap',
        style: 'https://tiles.openfreemap.org/styles/positron',
        center: [107.5, 16.0],
        zoom: 4,
        attributionControl: true,
        cooperativeGestures: true,
        fadeDuration: 0
      });
      map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-left');
      map.once('load', () => {
        mapMessage.textContent = 'Bản đồ OpenFreeMap · dữ liệu © OpenMapTiles, © OpenStreetMap.';
      });
      map.on('error', () => {
        mapMessage.textContent = 'Bản đồ đang tải chậm. Danh sách chi nhánh và tìm kiếm vẫn sử dụng được.';
      });
    } catch (_) {
      mapMessage.textContent = 'Không tải được bản đồ. Danh sách chi nhánh và tìm kiếm vẫn dùng được.';
    }
  }

  initMap();
})();

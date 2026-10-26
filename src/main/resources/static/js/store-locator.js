(() => {
  const root = document.getElementById('store-locator');
  if (!root) return;

  const results = document.getElementById('storeResults');
  const message = document.getElementById('locationMessage');
  const mapMessage = document.getElementById('mapMessage');
  const addressInput = document.getElementById('addressQuery');
  const suggestionsBox = document.getElementById('addressSuggestions');
  let map;
  let maplibregl;
  let storeMarkers = [];
  let originMarker;
  let selectedAddress = null;
  let suggestionTimer;
  let suggestionRequest;
  let activeSuggestion = -1;
  let currentSuggestions = [];

  const preferredStore = () => { try { return localStorage.getItem('preferredStoreId'); } catch (_) { return null; } };

  function hideSuggestions() {
    suggestionsBox.replaceChildren();
    suggestionsBox.hidden = true;
    addressInput.setAttribute('aria-expanded', 'false');
    activeSuggestion = -1;
    currentSuggestions = [];
  }

  function chooseAddress(suggestion) {
    selectedAddress = suggestion;
    addressInput.value = suggestion.label;
    hideSuggestions();
    byCoordinates(suggestion.latitude, suggestion.longitude);
  }

  function showSuggestions(suggestions) {
    suggestionsBox.replaceChildren();
    currentSuggestions = suggestions;
    activeSuggestion = -1;
    if (!suggestions.length) { hideSuggestions(); return; }
    suggestions.forEach((suggestion, index) => {
      const option = document.createElement('button');
      option.type = 'button';
      option.className = 'store-locator__suggestion';
      option.role = 'option';
      option.setAttribute('aria-selected', 'false');
      option.dataset.index = String(index);
      const label = document.createElement('span');
      label.textContent = suggestion.label;
      const hint = document.createElement('small');
      hint.textContent = 'Địa chỉ tại Việt Nam';
      option.append(label, hint);
      option.addEventListener('click', () => chooseAddress(suggestion));
      suggestionsBox.append(option);
    });
    suggestionsBox.hidden = false;
    addressInput.setAttribute('aria-expanded', 'true');
  }

  addressInput.addEventListener('input', () => {
    selectedAddress = null;
    window.clearTimeout(suggestionTimer);
    suggestionRequest?.abort();
    const query = addressInput.value.trim();
    if (query.length < 3) { hideSuggestions(); return; }
    suggestionTimer = window.setTimeout(async () => {
      suggestionRequest = new AbortController();
      try {
        const response = await fetch(`/api/locations/suggest?query=${encodeURIComponent(query)}`, {
          headers: { Accept: 'application/json' }, signal: suggestionRequest.signal
        });
        const payload = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(payload.error || 'Không tải được gợi ý địa chỉ.');
        if (addressInput.value.trim() === query) showSuggestions(Array.isArray(payload) ? payload : []);
      } catch (error) {
        if (error.name !== 'AbortError' && addressInput.value.trim() === query) {
          hideSuggestions();
          message.textContent = error.message || 'Gợi ý địa chỉ đang tạm gián đoạn. Bạn vẫn có thể bấm Tìm cửa hàng.';
        }
      }
    }, 500);
  });

  addressInput.addEventListener('keydown', event => {
    if (suggestionsBox.hidden || !currentSuggestions.length) return;
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      const direction = event.key === 'ArrowDown' ? 1 : -1;
      activeSuggestion = (activeSuggestion + direction + currentSuggestions.length) % currentSuggestions.length;
      suggestionsBox.querySelectorAll('[role="option"]').forEach((option, index) => {
        option.setAttribute('aria-selected', String(index === activeSuggestion));
      });
    } else if (event.key === 'Enter' && activeSuggestion >= 0) {
      event.preventDefault();
      chooseAddress(currentSuggestions[activeSuggestion]);
    } else if (event.key === 'Escape') hideSuggestions();
  });

  document.addEventListener('click', event => {
    if (!event.target.closest('.store-locator__address-field')) hideSuggestions();
  });

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

  async function showAllStoresOnMap() {
    message.textContent = 'Đang tải danh sách chi nhánh…';
    try {
      const response = await fetch('/api/locations/all', { headers: { Accept: 'application/json' } });
      if (!response.ok) throw new Error('Không tải được danh sách chi nhánh.');
      const stores = await response.json();
      clearMarkers();
      const bounds = [];
      stores.forEach((store, index) => {
        const position = addStoreMarker(store, index);
        if (position) bounds.push(position);
      });
      if (stores.length) {
        message.textContent = `Bản đồ đang hiển thị tất cả ${stores.length} chi nhánh. Tìm kiếm hoặc dùng GPS để lọc tối đa 5 điểm phù hợp.`;
      } else {
        message.textContent = 'Chưa có chi nhánh nào có tọa độ để hiển thị trên bản đồ.';
      }
      if (map && bounds.length === 1) map.easeTo({ center: bounds[0], zoom: 13 });
      else if (map && bounds.length > 1) {
        const extent = new maplibregl.LngLatBounds();
        bounds.forEach(point => extent.extend(point));
        map.fitBounds(extent, { padding: 48, maxZoom: 10, duration: 450 });
      }
    } catch (error) {
      message.textContent = error.message || 'Không tải được danh sách chi nhánh.';
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
    const query = addressInput.value.trim();
    if (!query) return;
    if (selectedAddress) {
      await byCoordinates(selectedAddress.latitude, selectedAddress.longitude);
      return;
    }
    message.textContent = 'Đang xác định địa chỉ…';
    try {
      const result = await window.geocodeWithNominatim(query);
      if (result.displayName) addressInput.value = result.displayName;
      await byCoordinates(result.latitude, result.longitude);
    } catch (error) {
      const localResults = await fetchStores(`/api/locations/search?query=${encodeURIComponent(query)}`);
      if (localResults?.length) {
        message.textContent = 'Chưa xác định được tọa độ địa chỉ; đang hiển thị chi nhánh khớp trong dữ liệu cửa hàng.';
      } else {
        message.textContent = error.message || 'Không thể xác định địa chỉ. Hãy thử lại hoặc nhập quận, tỉnh/thành.';
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
        mapMessage.textContent = 'Bản đồ OpenFreeMap · dữ liệu © OpenMapTiles, © OpenStreetMap. Gợi ý địa chỉ từ Photon.';
        showAllStoresOnMap();
      });
      map.on('error', () => {
        mapMessage.textContent = 'Bản đồ đang tải chậm. Danh sách chi nhánh và tìm kiếm vẫn sử dụng được.';
      });
    } catch (_) {
        mapMessage.textContent = 'Không tải được bản đồ. Gợi ý địa chỉ từ Photon · dữ liệu OpenStreetMap; danh sách chi nhánh vẫn dùng được.';
    }
  }

  initMap();
})();

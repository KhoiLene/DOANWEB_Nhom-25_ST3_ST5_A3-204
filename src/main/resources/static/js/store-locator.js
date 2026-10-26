(() => {
  const root = document.getElementById('store-locator');
  if (!root) return;

  const results = document.getElementById('storeResults');
  const message = document.getElementById('locationMessage');
  const mapMessage = document.getElementById('mapMessage');
  const mapKey = root.dataset.googleMapsKey;
  let map;
  let geocoder;
  let infoWindow;
  let markers = [];
  let originMarker;

  const preferredStore = () => { try { return localStorage.getItem('preferredStoreId'); } catch (_) { return null; } };

  function clearMarkers() {
    markers.forEach(marker => marker.setMap(null));
    markers = [];
    if (originMarker) originMarker.setMap(null);
    originMarker = null;
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

    message.textContent = `Đang hiển thị ${stores.length} chi nhánh gần nhất.`;
    const bounds = map ? new google.maps.LatLngBounds() : null;
    if (map && origin) {
      originMarker = new google.maps.Marker({
        map, position: origin, title: 'Vị trí tìm kiếm',
        icon: { path: google.maps.SymbolPath.CIRCLE, scale: 7, fillColor: '#2563eb', fillOpacity: 1, strokeColor: '#fff', strokeWeight: 2 }
      });
      bounds.extend(origin);
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
        const position = { lat: store.latitude, lng: store.longitude };
        const marker = new google.maps.Marker({
          map,
          position,
          title: store.name,
          label: String(index + 1)
        });
        marker.addListener('click', () => {
          const content = document.createElement('div');
          const title = document.createElement('strong');
          title.textContent = store.name || '';
          const addressLine = document.createElement('div');
          addressLine.textContent = store.address || '';
          content.append(title, addressLine);
          infoWindow.setContent(content);
          infoWindow.open({ map, anchor: marker });
        });
        markers.push(marker);
        bounds.extend(position);
      }
    });
    if (map && bounds && !bounds.isEmpty()) {
      if (origin) bounds.extend(origin);
      map.fitBounds(bounds, 48);
      if (stores.length === 1) map.setZoom(14);
    }
  }

  async function fetchStores(url, origin) {
    message.textContent = 'Đang tìm cửa hàng…';
    try {
      const response = await fetch(url, { headers: { Accept: 'application/json' } });
      if (!response.ok) throw new Error('Không thể tìm chi nhánh lúc này.');
      showStores(await response.json(), origin);
    } catch (error) {
      message.textContent = error.message || 'Không thể tìm chi nhánh lúc này.';
    }
  }

  function byCoordinates(latitude, longitude) {
    const params = new URLSearchParams({ latitude, longitude, limit: '5' });
    const origin = { lat: latitude, lng: longitude };
    return fetchStores(`/api/locations/nearest?${params}`, origin);
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
    const query = document.getElementById('addressQuery').value.trim();
    if (!query) return;
    if (!geocoder) {
      return fetchStores(`/api/locations/search?query=${encodeURIComponent(query)}`);
    }
    message.textContent = 'Đang xác định vị trí địa chỉ…';
    try {
      const { results: geocoded } = await geocoder.geocode({ address: query });
      if (!geocoded?.length) throw new Error('Không tìm thấy địa chỉ. Hãy nhập thêm quận hoặc tỉnh/thành.');
      const location = geocoded[0].geometry.location;
      document.getElementById('addressQuery').value = geocoded[0].formatted_address || query;
      await byCoordinates(location.lat(), location.lng());
    } catch (error) {
      message.textContent = error.message || 'Không thể xác định địa chỉ. Hãy thử lại.';
    }
  });

  window.initStoreLocatorMap = async () => {
    try {
      const [{ Map, InfoWindow }, { Geocoder }] = await Promise.all([
        google.maps.importLibrary('maps'),
        google.maps.importLibrary('geocoding')
      ]);
      map = new Map(document.getElementById('storeMap'), {
        center: { lat: 16.0, lng: 107.5 }, zoom: 5, mapTypeControl: false,
        streetViewControl: false, fullscreenControl: true
      });
      geocoder = new Geocoder();
      infoWindow = new InfoWindow();
      mapMessage.textContent = 'Nhập địa chỉ hoặc dùng GPS để xem và ghim tối đa 5 chi nhánh gần nhất.';
    } catch (_) {
      mapMessage.textContent = 'Không tải được bản đồ. Bạn vẫn có thể tìm chi nhánh bằng địa chỉ.';
    }
  };

  if (mapKey) {
    const script = document.createElement('script');
    script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(mapKey)}&loading=async&callback=initStoreLocatorMap`;
    script.async = true;
    script.onerror = () => {
      mapMessage.textContent = 'Không tải được Google Maps. Kiểm tra khóa, API đã bật và giới hạn referrer.';
    };
    document.head.append(script);
  } else {
    mapMessage.textContent = 'Bản đồ sẽ xuất hiện sau khi cấu hình GOOGLE_MAPS_BROWSER_KEY. Tìm theo khu vực vẫn hoạt động.';
  }
})();

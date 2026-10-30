(() => {
  const tools = document.getElementById('locationMapTools');
  if (!tools) return;

  const message = document.getElementById('locationMapMessage');
  const latitude = document.getElementById('latitude');
  const longitude = document.getElementById('longitude');
  const address = document.getElementById('address');
  const district = document.getElementById('district');
  const city = document.getElementById('city');
  const suggestions = document.getElementById('locationAddressSuggestions');
  const addressField = address?.closest('.branch-address-field');
  const form = tools.closest('form');
  let map;
  let maplibregl;
  let selectedMarker;
  let suggestionTimer;
  let requestController;
  let requestSequence = 0;
  let confirmedAddress = address?.value.trim() || '';

  const currentPosition = () => {
    const lat = Number(latitude?.value);
    const lng = Number(longitude?.value);
    return latitude?.value && longitude?.value && Number.isFinite(lat) && Number.isFinite(lng)
      && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180 ? [lng, lat] : null;
  };

  function writePosition(position) {
    if (!Array.isArray(position) || position.length < 2) return;
    longitude.value = Number(position[0]).toFixed(7);
    latitude.value = Number(position[1]).toFixed(7);
  }

  function setMarker(position) {
    if (!map || !maplibregl) return;
    if (!selectedMarker) {
      selectedMarker = new maplibregl.Marker({ color: '#2563eb', draggable: true, anchor: 'bottom' })
        .setLngLat(position).addTo(map);
      selectedMarker.on('dragend', () => {
        const point = selectedMarker.getLngLat();
        writePosition([point.lng, point.lat]);
        message.textContent = 'Đã chỉnh ghim. Vị trí mới sẽ được lưu cùng chi nhánh/kho.';
      });
    } else {
      selectedMarker.setLngLat(position);
    }
  }

  function setPosition(position, place = null, moveMap = true) {
    writePosition(position);
    if (place) {
      address.value = place.label || place.displayName || address.value;
      confirmedAddress = address.value.trim();
      if (district) district.value = place.district || '';
      if (city) city.value = place.city || '';
    } else if (!confirmedAddress && address.value.trim()) {
      confirmedAddress = address.value.trim();
    }
    setMarker(position);
    if (map && moveMap) map.easeTo({ center: position, zoom: Math.max(map.getZoom(), 15), duration: 400 });
    message.textContent = 'Đã định vị. Kéo ghim nếu cần chỉnh vị trí chính xác hơn.';
  }

  function closeSuggestions() {
    suggestions.hidden = true;
    suggestions.replaceChildren();
    address.setAttribute('aria-expanded', 'false');
  }

  function displaySuggestions(items) {
    suggestions.replaceChildren();
    if (!items.length) {
      const empty = document.createElement('p');
      empty.className = 'branch-address-suggestions__empty';
      empty.textContent = 'Chưa tìm thấy địa chỉ. Hãy nhập thêm tên đường hoặc khu vực.';
      suggestions.append(empty);
      suggestions.hidden = false;
      address.setAttribute('aria-expanded', 'true');
      return;
    }
    items.forEach(item => {
      const option = document.createElement('button');
      option.type = 'button';
      option.className = 'branch-address-suggestions__option';
      option.setAttribute('role', 'option');
      option.textContent = item.label;
      option.addEventListener('click', () => {
        closeSuggestions();
        setPosition([item.longitude, item.latitude], item);
      });
      suggestions.append(option);
    });
    suggestions.hidden = false;
    address.setAttribute('aria-expanded', 'true');
  }

  async function loadSuggestions(query) {
    requestController?.abort();
    requestController = new AbortController();
    const sequence = ++requestSequence;
    try {
      const results = await window.suggestAddresses(query, requestController.signal);
      if (sequence === requestSequence && address.value.trim() === query) displaySuggestions(results);
    } catch (error) {
      if (error.name === 'AbortError' || sequence !== requestSequence) return;
      closeSuggestions();
      message.textContent = error.message || 'Không tải được gợi ý. Bạn vẫn có thể đặt ghim trực tiếp trên bản đồ.';
    }
  }

  address?.addEventListener('input', () => {
    window.clearTimeout(suggestionTimer);
    requestController?.abort();
    const query = address.value.trim();
    if (query !== confirmedAddress) {
      latitude.value = '';
      longitude.value = '';
      if (district) district.value = '';
      if (city) city.value = '';
      confirmedAddress = '';
      selectedMarker?.remove();
      selectedMarker = null;
    }
    if (query.length < 3) {
      closeSuggestions();
      return;
    }
    message.textContent = 'Đang tìm địa chỉ…';
    suggestionTimer = window.setTimeout(() => loadSuggestions(query), 800);
  });

  address?.addEventListener('keydown', event => {
    if (event.key === 'Escape') closeSuggestions();
    if (event.key === 'ArrowDown' && !suggestions.hidden) {
      event.preventDefault();
      suggestions.querySelector('button')?.focus();
    }
    if (event.key === 'Enter' && !suggestions.hidden) {
      const first = suggestions.querySelector('button');
      if (first) { event.preventDefault(); first.click(); }
    }
  });

  suggestions?.addEventListener('keydown', event => {
    const options = [...suggestions.querySelectorAll('button')];
    const index = options.indexOf(document.activeElement);
    if (event.key === 'ArrowDown' && index < options.length - 1) {
      event.preventDefault(); options[index + 1]?.focus();
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      if (index <= 0) address.focus(); else options[index - 1]?.focus();
    } else if (event.key === 'Escape') {
      closeSuggestions(); address.focus();
    }
  });

  document.addEventListener('click', event => {
    if (addressField && !addressField.contains(event.target)) closeSuggestions();
  });

  document.getElementById('findLocationAddress')?.addEventListener('click', async () => {
    const query = address.value.trim();
    if (!query) { address.focus(); message.textContent = 'Nhập địa chỉ để tìm trên bản đồ.'; return; }
    message.textContent = 'Đang định vị địa chỉ…';
    try {
      const result = await window.geocodeWithNominatim(query, 'vn');
      setPosition([result.longitude, result.latitude], result);
    } catch (error) {
      message.textContent = error.message || 'Không tìm được vị trí. Hãy chọn gợi ý hoặc bấm trực tiếp lên bản đồ.';
    }
  });

  form?.addEventListener('submit', event => {
    if (!currentPosition()) {
      event.preventDefault();
      message.textContent = 'Hãy chọn gợi ý địa chỉ hoặc bấm lên bản đồ để đặt ghim.';
      tools.scrollIntoView({ behavior: 'smooth', block: 'center' });
      address.focus();
    }
  });

  (async () => {
    try {
      maplibregl = await import('https://unpkg.com/maplibre-gl@6.11.2/dist/maplibre-gl.mjs');
      const savedPosition = currentPosition();
      map = new maplibregl.Map({
        container: 'locationMap',
        style: 'https://tiles.openfreemap.org/styles/positron',
        center: savedPosition || [107.5, 16],
        zoom: savedPosition ? 15 : 5,
        attributionControl: true,
        cooperativeGestures: true,
        fadeDuration: 0
      });
      map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right');
      const locations = [...document.querySelectorAll('[data-map-location]')]
        .filter(node => node.dataset.lat && node.dataset.lng
          && Number.isFinite(Number(node.dataset.lat)) && Number.isFinite(Number(node.dataset.lng)));
      const editId = document.getElementById('id')?.value;
      const bounds = new maplibregl.LngLatBounds();

      map.on('load', () => {
        locations.forEach(location => {
          const position = [Number(location.dataset.lng), Number(location.dataset.lat)];
          bounds.extend(position);
          if (editId && location.dataset.id === editId) return;
          const popup = document.createElement('div');
          popup.className = 'branch-map-popup';
          const name = document.createElement('strong');
          name.textContent = location.dataset.name || 'Địa điểm';
          const detail = document.createElement('span');
          detail.textContent = location.dataset.address || '';
          popup.append(name, detail);
          new maplibregl.Marker({ color: location.dataset.active === 'false' ? '#8b98aa' : '#22a06b' })
            .setLngLat(position).setPopup(new maplibregl.Popup({ offset: 24 }).setDOMContent(popup)).addTo(map);
        });
        if (savedPosition) {
          setMarker(savedPosition);
          bounds.extend(savedPosition);
        } else if (!bounds.isEmpty()) {
          map.fitBounds(bounds, { padding: 48, maxZoom: 12 });
        }
        message.textContent = savedPosition
          ? 'Kéo ghim để chỉnh đúng vị trí, hoặc nhập địa chỉ mới để tìm lại.'
          : 'Nhập địa chỉ ở trên; chọn gợi ý để bản đồ tự ghim vị trí.';
        window.requestAnimationFrame(() => map.resize());
      });
      map.on('click', event => {
        closeSuggestions();
        setPosition([event.lngLat.lng, event.lngLat.lat]);
      });
      map.on('error', () => { message.textContent = 'Bản đồ tải chậm. Thử lại sau hoặc chọn vị trí trên bản đồ khi tải xong.'; });
    } catch (_) {
      message.textContent = 'Không tải được bản đồ. Hãy thử lại sau; địa chỉ vẫn có thể được tìm bằng nút Định vị.';
    }
  })();
})();

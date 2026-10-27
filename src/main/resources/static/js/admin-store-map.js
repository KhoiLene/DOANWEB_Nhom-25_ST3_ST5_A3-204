(() => {
  const tools = document.getElementById('branchMapTools');
  if (!tools) return;

  const message = document.getElementById('branchMapMessage');
  const latitude = document.getElementById('latitude');
  const longitude = document.getElementById('longitude');
  const address = document.getElementById('address');
  const district = document.getElementById('district');
  const city = document.getElementById('city');
  const suggestions = document.getElementById('branchAddressSuggestions');
  const addressField = address?.closest('.branch-address-field');
  const form = tools.closest('form');
  let map;
  let maplibregl;
  let selectedMarker;
  let suggestionTimer;
  let requestController;
  let requestSequence = 0;

  const currentPosition = () => {
    const lat = Number(latitude?.value);
    const lng = Number(longitude?.value);
    return latitude?.value && longitude?.value && Number.isFinite(lat) && Number.isFinite(lng)
      ? [lng, lat] : null;
  };

  function setPosition(position, formattedAddress) {
    if (!Array.isArray(position) || position.length < 2) return;
    longitude.value = Number(position[0]).toFixed(7);
    latitude.value = Number(position[1]).toFixed(7);
    if (formattedAddress) address.value = formattedAddress;
    if (!map) {
      message.textContent = 'Vị trí đã được chọn và sẽ xuất hiện trên bản đồ trong giây lát.';
      return;
    }
    if (!selectedMarker) {
      selectedMarker = new maplibregl.Marker({ color: '#2563eb', draggable: true, anchor: 'bottom' })
        .setLngLat(position).addTo(map);
      selectedMarker.on('dragend', () => {
        const point = selectedMarker.getLngLat();
        setPosition([point.lng, point.lat]);
      });
    } else {
      selectedMarker.setLngLat(position);
    }
    map.easeTo({ center: position, zoom: Math.max(map.getZoom(), 14), duration: 350 });
    message.textContent = 'Đã chọn vị trí. Kiểm tra địa chỉ và ghim trước khi lưu.';
  }

  function closeSuggestions() {
    suggestions.hidden = true;
    suggestions.replaceChildren();
    address.setAttribute('aria-expanded', 'false');
  }

  function chooseSuggestion(item) {
    address.value = item.label;
    setPosition([item.longitude, item.latitude], item.label);
    closeSuggestions();
  }

  function displaySuggestions(items) {
    suggestions.replaceChildren();
    if (!items.length) {
      const empty = document.createElement('p');
      empty.className = 'branch-address-suggestions__empty';
      empty.textContent = 'Chưa có địa chỉ phù hợp. Hãy thử thêm tên đường hoặc quận.';
      suggestions.append(empty);
      suggestions.hidden = false;
      address.setAttribute('aria-expanded', 'true');
      return;
    }
    items.forEach((item, index) => {
      const option = document.createElement('button');
      option.type = 'button';
      option.className = 'branch-address-suggestions__option';
      option.setAttribute('role', 'option');
      option.setAttribute('aria-selected', 'false');
      option.dataset.index = String(index);
      option.textContent = item.label;
      option.addEventListener('click', () => chooseSuggestion(item));
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
      const items = await window.suggestAddresses(query, requestController.signal);
      if (sequence === requestSequence && address.value.trim() === query) displaySuggestions(items);
    } catch (error) {
      if (error.name === 'AbortError' || sequence !== requestSequence) return;
      closeSuggestions();
      message.textContent = error.message || 'Không thể tải gợi ý địa chỉ. Bạn vẫn có thể chọn vị trí trên bản đồ.';
    }
  }

  address?.addEventListener('input', () => {
    window.clearTimeout(suggestionTimer);
    requestController?.abort();
    const query = address.value.trim();
    if (query.length < 3) {
      closeSuggestions();
      return;
    }
    suggestionTimer = window.setTimeout(() => loadSuggestions(query), 650);
  });

  address?.addEventListener('keydown', event => {
    if (event.key === 'Escape') closeSuggestions();
    if (event.key === 'ArrowDown' && !suggestions.hidden) {
      event.preventDefault();
      suggestions.querySelector('button')?.focus();
    }
    if (event.key === 'Enter' && !suggestions.hidden) {
      const first = suggestions.querySelector('button');
      if (first) {
        event.preventDefault();
        first.click();
      }
    }
  });

  suggestions?.addEventListener('keydown', event => {
    const options = [...suggestions.querySelectorAll('button')];
    const currentIndex = options.indexOf(document.activeElement);
    if (event.key === 'ArrowDown' && currentIndex < options.length - 1) {
      event.preventDefault();
      options[currentIndex + 1]?.focus();
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      if (currentIndex <= 0) address.focus();
      else options[currentIndex - 1]?.focus();
    } else if (event.key === 'Escape') {
      closeSuggestions();
      address.focus();
    }
  });

  document.addEventListener('click', event => {
    if (addressField && !addressField.contains(event.target)) closeSuggestions();
  });

  document.getElementById('geocodeBranchAddress')?.addEventListener('click', async () => {
    if (!address.value.trim()) {
      message.textContent = 'Nhập địa chỉ trước để tìm vị trí.';
      address.focus();
      return;
    }
    message.textContent = 'Đang tìm vị trí…';
    try {
      const query = [address.value, district?.value, city?.value].filter(Boolean).join(', ');
      const result = await window.geocodeWithNominatim(query, 'vn');
      setPosition([result.longitude, result.latitude], result.displayName);
    } catch (error) {
      message.textContent = error.message || 'Không tìm thấy vị trí. Chọn trực tiếp trên bản đồ.';
    }
  });

  form?.addEventListener('submit', event => {
    if (!currentPosition()) {
      event.preventDefault();
      message.textContent = 'Hãy chọn một gợi ý địa chỉ hoặc bấm lên bản đồ để đặt ghim trước khi lưu.';
      tools.scrollIntoView({ behavior: 'smooth', block: 'center' });
      address.focus();
    }
  });

  (async () => {
    try {
      maplibregl = await import('https://unpkg.com/maplibre-gl@6.11.2/dist/maplibre-gl.mjs');
      map = new maplibregl.Map({
        container: 'branchMap',
        style: 'https://tiles.openfreemap.org/styles/positron',
        center: currentPosition() || [107.5, 16],
        zoom: currentPosition() ? 14 : 5,
        attributionControl: true,
        cooperativeGestures: true,
        fadeDuration: 0
      });
      window.branchAdminMap = map;
      map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right');
      const branches = [...document.querySelectorAll('.branch-directory__item[data-lat][data-lng]')]
        .filter(item => item.dataset.lat && item.dataset.lng && item.dataset.branchMarker !== 'false')
        .filter(item => Number.isFinite(Number(item.dataset.lat)) && Number.isFinite(Number(item.dataset.lng)));
      const editId = document.getElementById('id')?.value;
      const bounds = new maplibregl.LngLatBounds();

      map.on('load', () => {
        branches.forEach(branch => {
          const branchId = branch.dataset.id;
          const position = [Number(branch.dataset.lng), Number(branch.dataset.lat)];
          bounds.extend(position);
          if (editId && branchId === editId) return;
          const popup = document.createElement('div');
          popup.className = 'branch-map-popup';
          const title = document.createElement('strong');
          title.textContent = branch.dataset.name || 'Chi nhánh';
          const detail = document.createElement('span');
          detail.textContent = branch.dataset.address || '';
          popup.append(title, detail);
          new maplibregl.Marker({ color: branch.dataset.active === 'true' ? '#22a06b' : '#8b98aa' })
            .setLngLat(position)
            .setPopup(new maplibregl.Popup({ offset: 24 }).setDOMContent(popup))
            .addTo(map);
        });
        const savedPosition = currentPosition();
        if (savedPosition) {
          selectedMarker = new maplibregl.Marker({ color: '#2563eb', draggable: true, anchor: 'bottom' })
            .setLngLat(savedPosition).addTo(map);
          selectedMarker.on('dragend', () => {
            const point = selectedMarker.getLngLat();
            setPosition([point.lng, point.lat]);
          });
          bounds.extend(savedPosition);
        }
        if (!bounds.isEmpty() && !savedPosition) map.fitBounds(bounds, { padding: 52, maxZoom: 13 });
        message.textContent = 'Bản đồ OpenFreeMap · gợi ý địa chỉ từ Photon. Kéo ghim hoặc bấm vị trí muốn chọn.';
        window.requestAnimationFrame(() => map.resize());
      });
      map.on('click', event => setPosition([event.lngLat.lng, event.lngLat.lat]));
      map.on('error', () => {
        message.textContent = 'Bản đồ đang tải chậm. Gợi ý địa chỉ và danh sách chi nhánh vẫn hoạt động.';
      });
    } catch (_) {
      message.textContent = 'Không tải được bản đồ. Hãy thử lại hoặc nhập tọa độ đã xác nhận.';
    }
  })();
})();

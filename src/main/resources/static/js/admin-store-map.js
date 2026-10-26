(() => {
  const tools = document.getElementById('branchMapTools');
  if (!tools) return;
  const message = document.getElementById('branchMapMessage');
  const latitude = document.getElementById('latitude');
  const longitude = document.getElementById('longitude');
  const address = document.getElementById('address');
  const district = document.getElementById('district');
  const city = document.getElementById('city');

  if (!window.L) {
    message.textContent = 'Không tải được thư viện bản đồ. Bạn vẫn có thể nhập tọa độ trực tiếp.';
    return;
  }

  const currentPosition = () => {
    const lat = Number(latitude.value);
    const lng = Number(longitude.value);
    return latitude.value && longitude.value && Number.isFinite(lat) && Number.isFinite(lng)
      ? [lat, lng] : null;
  };

  const initial = currentPosition() || [16.0, 107.5];
  const map = L.map('branchMap').setView(initial, currentPosition() ? 15 : 5);
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
  }).addTo(map);
  const marker = L.marker(initial, { draggable: true, title: 'Vị trí chi nhánh' }).addTo(map);

  function setPosition(position, formattedAddress) {
    latitude.value = position[0].toFixed(7);
    longitude.value = position[1].toFixed(7);
    marker.setLatLng(position);
    map.panTo(position);
    map.setZoom(Math.max(map.getZoom() || 5, 15));
    if (formattedAddress) address.value = formattedAddress;
  }

  marker.on('dragend', () => setPosition(marker.getLatLng().toArray()));
  map.on('click', event => setPosition([event.latlng.lat, event.latlng.lng]));

  document.getElementById('geocodeBranchAddress').addEventListener('click', async () => {
    if (!address.value.trim()) {
      message.textContent = 'Nhập địa chỉ chi nhánh trước khi định vị.';
      address.focus();
      return;
    }
    message.textContent = 'Đang tìm địa chỉ trong Việt Nam…';
    try {
      const query = [address.value, district.value, city.value].filter(Boolean).join(', ');
      const result = await window.geocodeWithNominatim(query, 'vn');
      setPosition([result.latitude, result.longitude], result.displayName);
      message.textContent = 'Đã điền tọa độ. Kiểm tra ghim trên bản đồ trước khi lưu.';
    } catch (error) {
      message.textContent = error.message || 'Không thể định vị địa chỉ này.';
    }
  });

  message.textContent = 'Bản đồ OpenStreetMap miễn phí. Nhập địa chỉ rồi định vị, hoặc bấm/di chuyển ghim để chọn tọa độ.';
})();

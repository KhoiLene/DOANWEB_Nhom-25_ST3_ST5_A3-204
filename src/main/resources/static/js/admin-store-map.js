(() => {
  const tools = document.getElementById('branchMapTools');
  if (!tools) return;
  const key = tools.dataset.googleMapsKey;
  const message = document.getElementById('branchMapMessage');
  const latitude = document.getElementById('latitude');
  const longitude = document.getElementById('longitude');
  const address = document.getElementById('address');
  const district = document.getElementById('district');
  const city = document.getElementById('city');
  let map;
  let geocoder;
  let marker;

  const currentPosition = () => {
    const lat = Number(latitude.value);
    const lng = Number(longitude.value);
    return latitude.value && longitude.value && Number.isFinite(lat) && Number.isFinite(lng)
      ? { lat, lng } : null;
  };

  function setPosition(position, formattedAddress) {
    latitude.value = position.lat.toFixed(7);
    longitude.value = position.lng.toFixed(7);
    marker.setPosition(position);
    map.panTo(position);
    map.setZoom(Math.max(map.getZoom() || 5, 15));
    if (formattedAddress) address.value = formattedAddress;
  }

  window.initBranchMap = async () => {
    try {
      const [{ Map }, { Geocoder }] = await Promise.all([
        google.maps.importLibrary('maps'), google.maps.importLibrary('geocoding')
      ]);
      const existing = currentPosition();
      map = new Map(document.getElementById('branchMap'), {
        center: existing || { lat: 16.0, lng: 107.5 }, zoom: existing ? 15 : 5,
        mapTypeControl: false, streetViewControl: false
      });
      geocoder = new Geocoder();
      marker = new google.maps.Marker({ map, position: existing || map.getCenter(), draggable: true, title: 'Vị trí chi nhánh' });
      marker.addListener('dragend', event => setPosition({ lat: event.latLng.lat(), lng: event.latLng.lng() }));
      map.addListener('click', async event => {
        const position = { lat: event.latLng.lat(), lng: event.latLng.lng() };
        try {
          const { results } = await geocoder.geocode({ location: position });
          setPosition(position, results?.[0]?.formatted_address);
        } catch (_) {
          setPosition(position);
        }
      });
      message.textContent = 'Nhập địa chỉ rồi bấm định vị, hoặc bấm/di chuyển ghim để chọn vị trí.';
    } catch (_) {
      message.textContent = 'Không tải được bản đồ. Bạn vẫn có thể nhập vĩ độ và kinh độ trực tiếp.';
    }
  };

  document.getElementById('geocodeBranchAddress').addEventListener('click', async () => {
    if (!geocoder) {
      message.textContent = 'Bản đồ chưa sẵn sàng. Kiểm tra khóa API và trạng thái kết nối.';
      return;
    }
    const parts = [address.value, district.value, city.value, 'Vietnam'].filter(Boolean);
    if (!address.value.trim()) {
      message.textContent = 'Nhập địa chỉ chi nhánh trước khi định vị.';
      address.focus();
      return;
    }
    message.textContent = 'Đang xác định tọa độ…';
    try {
      const { results } = await geocoder.geocode({ address: parts.join(', '), componentRestrictions: { country: 'VN' } });
      if (!results?.length) throw new Error('Không tìm thấy địa chỉ. Hãy nhập thêm quận hoặc tỉnh/thành.');
      const location = results[0].geometry.location;
      setPosition({ lat: location.lat(), lng: location.lng() }, results[0].formatted_address);
      message.textContent = 'Đã điền tọa độ. Kiểm tra lại ghim trước khi lưu chi nhánh.';
    } catch (error) {
      message.textContent = error.message || 'Không thể định vị địa chỉ này.';
    }
  });

  if (!key) {
    message.textContent = 'Cấu hình GOOGLE_MAPS_BROWSER_KEY để chọn vị trí trên bản đồ; vẫn có thể nhập tọa độ thủ công.';
    return;
  }
  const script = document.createElement('script');
  script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(key)}&loading=async&callback=initBranchMap`;
  script.async = true;
  script.onerror = () => { message.textContent = 'Không tải được Google Maps. Kiểm tra khóa và API đã bật.'; };
  document.head.append(script);
})();

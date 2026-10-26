(() => {
  let lastDirectRequestAt = 0;

  async function directLookup(query, countryCode) {
    const wait = 1000 - (Date.now() - lastDirectRequestAt);
    if (wait > 0) await new Promise(resolve => window.setTimeout(resolve, wait));
    lastDirectRequestAt = Date.now();

    const params = new URLSearchParams({ format: 'jsonv2', limit: '1', 'accept-language': 'vi', q: query });
    if (countryCode) params.set('countrycodes', countryCode);
    const response = await fetch(`https://nominatim.openstreetmap.org/search?${params}`, {
      headers: { Accept: 'application/json' }
    });
    if (!response.ok) throw new Error('OpenStreetMap chưa phản hồi. Hãy thử lại sau hoặc đặt ghim thủ công.');
    const results = await response.json();
    if (!results.length) throw new Error('Không tìm thấy địa chỉ. Hãy nhập chi tiết hơn.');
    return {
      latitude: Number(results[0].lat),
      longitude: Number(results[0].lon),
      displayName: results[0].display_name || query
    };
  }

  window.geocodeWithNominatim = async (query, countryCode = '') => {
    const params = new URLSearchParams({ query });
    if (countryCode) params.set('countryCode', countryCode);
    try {
      const response = await fetch(`/api/locations/geocode?${params}`, { headers: { Accept: 'application/json' } });
      const result = await response.json();
      if (response.ok) return result;
      if (response.status !== 502 && response.status !== 504) throw new Error(result.error || 'Không thể xác định địa chỉ.');
    } catch (error) {
      if (error instanceof TypeError) return directLookup(query, countryCode);
      if (error?.message && !error.message.includes('Không thể tra địa chỉ lúc này')) throw error;
    }
    return directLookup(query, countryCode);
  };
})();

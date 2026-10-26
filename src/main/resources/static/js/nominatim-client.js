(() => {
  window.geocodeWithNominatim = async (query, countryCode = '') => {
    const params = new URLSearchParams({ query });
    if (countryCode) params.set('countryCode', countryCode);
    const response = await fetch(`/api/locations/geocode?${params}`, { headers: { Accept: 'application/json' } });
    const result = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(result.error || 'Không thể xác định địa chỉ.');
    return result;
  };
})();

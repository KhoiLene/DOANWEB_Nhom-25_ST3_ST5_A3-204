package com.shopchain.controller.admin;

import com.shopchain.entity.Store;
import com.shopchain.entity.Warehouse;
import com.shopchain.repository.StoreRepository;
import com.shopchain.repository.WarehouseRepository;
import com.shopchain.service.NominatimGeocodingService;
import com.shopchain.service.NominatimGeocodingService.GeocodingUnavailableException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/locations")
public class AdminLocationController {
    private final StoreRepository storeRepository;
    private final WarehouseRepository warehouseRepository;
    private final NominatimGeocodingService geocodingService;

    public AdminLocationController(StoreRepository storeRepository, WarehouseRepository warehouseRepository,
                                   NominatimGeocodingService geocodingService) {
        this.storeRepository = storeRepository;
        this.warehouseRepository = warehouseRepository;
        this.geocodingService = geocodingService;
    }

    @GetMapping("/stores")
    public String stores(@RequestParam(required = false) Long editId, Model model) {
        prepareStoresPage(model, editId == null ? Store.builder().active(true).pickupEnabled(true).build()
                : storeRepository.findById(editId).orElseThrow());
        return "admin/layout";
    }

    @PostMapping("/stores/save")
    public String saveStore(@ModelAttribute Store store, Model model) {
        if (store.getName() == null || store.getName().isBlank()) {
            prepareStoresPage(model, store);
            model.addAttribute("locationError", "Vui lòng nhập tên chi nhánh.");
            return "admin/layout";
        }
        if (store.getAddress() == null || store.getAddress().isBlank() || !ensureCoordinates(store.getAddress(), store)) {
            prepareStoresPage(model, store);
            model.addAttribute("locationError", "Chưa xác định được vị trí. Hãy chọn một gợi ý địa chỉ hoặc ghim vị trí trên bản đồ.");
            return "admin/layout";
        }
        store.setName(store.getName().trim());
        store.setAddress(store.getAddress().trim());
        storeRepository.save(store);
        return "redirect:/admin/locations/stores";
    }

    @PostMapping("/stores/{id}/toggle")
    public String toggleStore(@PathVariable Long id) {
        Store store = storeRepository.findById(id).orElseThrow();
        store.setActive(!store.isActive());
        storeRepository.save(store);
        return "redirect:/admin/locations/stores";
    }

    @GetMapping("/warehouses")
    public String warehouses(@RequestParam(required = false) Long editId, Model model) {
        prepareWarehousesPage(model, editId == null ? Warehouse.builder().active(true).build()
                : warehouseRepository.findById(editId).orElseThrow());
        return "admin/layout";
    }

    @PostMapping("/warehouses/save")
    public String saveWarehouse(@ModelAttribute Warehouse warehouse, Model model) {
        if (warehouse.getName() == null || warehouse.getName().isBlank()) {
            prepareWarehousesPage(model, warehouse);
            model.addAttribute("locationError", "Vui lòng nhập tên kho.");
            return "admin/layout";
        }
        if (warehouse.getAddress() == null || warehouse.getAddress().isBlank()
                || !ensureCoordinates(warehouse.getAddress(), warehouse)) {
            prepareWarehousesPage(model, warehouse);
            model.addAttribute("locationError", "Chưa xác định được vị trí. Hãy chọn một gợi ý địa chỉ hoặc ghim vị trí trên bản đồ.");
            return "admin/layout";
        }
        warehouse.setName(warehouse.getName().trim());
        warehouse.setAddress(warehouse.getAddress().trim());
        warehouseRepository.save(warehouse);
        return "redirect:/admin/locations/warehouses";
    }

    @PostMapping("/warehouses/{id}/toggle")
    public String toggleWarehouse(@PathVariable Long id) {
        Warehouse warehouse = warehouseRepository.findById(id).orElseThrow();
        warehouse.setActive(!warehouse.isActive());
        warehouseRepository.save(warehouse);
        return "redirect:/admin/locations/warehouses";
    }

    private void prepareStoresPage(Model model, Store store) {
        model.addAttribute("stores", storeRepository.findAll());
        model.addAttribute("store", store);
        model.addAttribute("title", "Quản lý cửa hàng");
        model.addAttribute("activePage", "stores");
        model.addAttribute("content", "admin/locations/stores");
    }

    private void prepareWarehousesPage(Model model, Warehouse warehouse) {
        model.addAttribute("warehouses", warehouseRepository.findAll());
        model.addAttribute("warehouse", warehouse);
        model.addAttribute("title", "Quản lý kho");
        model.addAttribute("activePage", "warehouses");
        model.addAttribute("content", "admin/locations/warehouses");
    }

    private boolean ensureCoordinates(String address, Store store) {
        if (validCoordinates(store.getLatitude(), store.getLongitude())) return true;
        try {
            var place = geocodingService.geocode(address, "vn");
            store.setLatitude(place.latitude());
            store.setLongitude(place.longitude());
            if (store.getDistrict() == null || store.getDistrict().isBlank()) store.setDistrict(place.district());
            if (store.getCity() == null || store.getCity().isBlank()) store.setCity(place.city());
            return true;
        } catch (IllegalArgumentException | GeocodingUnavailableException exception) {
            return false;
        }
    }

    private boolean ensureCoordinates(String address, Warehouse warehouse) {
        if (validCoordinates(warehouse.getLatitude(), warehouse.getLongitude())) return true;
        try {
            var place = geocodingService.geocode(address, "vn");
            warehouse.setLatitude(place.latitude());
            warehouse.setLongitude(place.longitude());
            return true;
        } catch (IllegalArgumentException | GeocodingUnavailableException exception) {
            return false;
        }
    }

    private boolean validCoordinates(Double latitude, Double longitude) {
        return latitude != null && longitude != null && Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }
}

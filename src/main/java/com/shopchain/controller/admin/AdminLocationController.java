package com.shopchain.controller.admin;

import com.shopchain.entity.Store;
import com.shopchain.entity.Warehouse;
import com.shopchain.repository.StoreRepository;
import com.shopchain.repository.WarehouseRepository;
import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/locations")
public class AdminLocationController {
    private final StoreRepository storeRepository;
    private final WarehouseRepository warehouseRepository;
    @Value("${maps.google.browser-key:}")
    private String googleMapsBrowserKey;

    public AdminLocationController(StoreRepository storeRepository, WarehouseRepository warehouseRepository) {
        this.storeRepository = storeRepository;
        this.warehouseRepository = warehouseRepository;
    }

    @GetMapping("/stores")
    public String stores(@RequestParam(required = false) Long editId, Model model) {
        model.addAttribute("stores", storeRepository.findAll());
        model.addAttribute("store", editId == null ? Store.builder().active(true).pickupEnabled(true).build()
                : storeRepository.findById(editId).orElseThrow());
        model.addAttribute("title", "Quản lý cửa hàng");
        model.addAttribute("googleMapsBrowserKey", googleMapsBrowserKey);
        model.addAttribute("activePage", "stores");
        model.addAttribute("content", "admin/locations/stores");
        return "admin/layout";
    }

    @PostMapping("/stores/save")
    public String saveStore(@ModelAttribute Store store) {
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
        model.addAttribute("warehouses", warehouseRepository.findAll());
        model.addAttribute("warehouse", editId == null ? Warehouse.builder().active(true).build()
                : warehouseRepository.findById(editId).orElseThrow());
        model.addAttribute("title", "Quản lý kho");
        model.addAttribute("activePage", "warehouses");
        model.addAttribute("content", "admin/locations/warehouses");
        return "admin/layout";
    }

    @PostMapping("/warehouses/save")
    public String saveWarehouse(@ModelAttribute Warehouse warehouse) {
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
}

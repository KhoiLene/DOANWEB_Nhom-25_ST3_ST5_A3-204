package com.shopchain.controller.admin;

import com.shopchain.entity.Role;
import com.shopchain.entity.User;
import com.shopchain.repository.RoleRepository;
import com.shopchain.repository.UserRepository;
import com.shopchain.repository.StoreRepository;
import com.shopchain.repository.WarehouseRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final StoreRepository storeRepository;
    private final WarehouseRepository warehouseRepository;

    public AdminUserController(UserRepository userRepository, RoleRepository roleRepository,
                                StoreRepository storeRepository, WarehouseRepository warehouseRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.storeRepository = storeRepository;
        this.warehouseRepository = warehouseRepository;
    }

    @GetMapping
    public String list(Model model) {
        List<User> users = userRepository.findAll();
        model.addAttribute("users", users);
        model.addAttribute("title", "Quản lý người dùng");
        model.addAttribute("activePage", "users");
        model.addAttribute("content", "admin/users/list");
        return "admin/layout";
    }

    @GetMapping("/edit/{id}")
    public String editUser(@PathVariable Long id, Model model) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid user Id:" + id));

        model.addAttribute("user", user);
        model.addAttribute("roles", roleRepository.findAll());
        model.addAttribute("stores", storeRepository.findAll());
        model.addAttribute("warehouses", warehouseRepository.findAll());
        model.addAttribute("title", "Chỉnh sửa người dùng");
        model.addAttribute("activePage", "users");
        model.addAttribute("content", "admin/users/form");
        return "admin/layout";
    }

    @PostMapping("/save")
    public String saveUser(@ModelAttribute User user, @RequestParam Long roleId,
                           @RequestParam(required = false) Long storeId,
                           @RequestParam(required = false) Long warehouseId) {
        User existingUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        existingUser.setRole(role);
        existingUser.setEnabled(user.isEnabled());
        existingUser.setFullName(user.getFullName());
        existingUser.setPhone(user.getPhone());
        existingUser.setStore(null);
        existingUser.setWarehouse(null);

        switch (role.getName()) {
            case "ROLE_STORE_STAFF" -> existingUser.setStore(storeRepository.findById(storeId)
                    .orElseThrow(() -> new IllegalArgumentException("Hãy gán nhân viên cho một cửa hàng.")));
            case "ROLE_WAREHOUSE" -> existingUser.setWarehouse(warehouseRepository.findById(warehouseId)
                    .orElseThrow(() -> new IllegalArgumentException("Hãy gán nhân viên cho một kho.")));
        }

        userRepository.save(existingUser);
        return "redirect:/admin/users";
    }

    @PostMapping("/toggle-status/{id}")
    public String toggleStatus(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
        return "redirect:/admin/users";
    }
}

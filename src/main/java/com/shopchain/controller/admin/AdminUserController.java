package com.shopchain.controller.admin;

import com.shopchain.entity.Role;
import com.shopchain.entity.Store;
import com.shopchain.entity.User;
import com.shopchain.entity.Warehouse;
import com.shopchain.repository.RoleRepository;
import com.shopchain.repository.StoreRepository;
import com.shopchain.repository.UserRepository;
import com.shopchain.repository.WarehouseRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {
    private static final Set<String> STAFF_ROLES = Set.of("ROLE_STORE_STAFF", "ROLE_WAREHOUSE");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{3,40}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final StoreRepository storeRepository;
    private final WarehouseRepository warehouseRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserController(UserRepository userRepository, RoleRepository roleRepository,
                               StoreRepository storeRepository, WarehouseRepository warehouseRepository,
                               PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.storeRepository = storeRepository;
        this.warehouseRepository = warehouseRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("title", "Quản lý người dùng");
        model.addAttribute("activePage", "users");
        model.addAttribute("content", "admin/users/list");
        return "admin/layout";
    }

    @GetMapping("/new")
    public String newEmployee(Model model) {
        prepareCreateModel(model, emptyForm());
        return "admin/layout";
    }

    @PostMapping("/create")
    @Transactional
    public String createEmployee(@RequestParam Map<String, String> params, Model model,
                                 RedirectAttributes redirectAttributes) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("fullName", value(params, "fullName"));
        form.put("username", value(params, "username"));
        form.put("email", value(params, "email"));
        form.put("phone", value(params, "phone"));
        form.put("roleId", value(params, "roleId"));
        form.put("storeId", value(params, "storeId"));
        form.put("warehouseId", value(params, "warehouseId"));

        String fullName = form.get("fullName").trim();
        String username = form.get("username").trim();
        String email = form.get("email").trim().toLowerCase(Locale.ROOT);
        String phone = form.get("phone").trim();
        String password = value(params, "password");
        Role role = findRole(form.get("roleId"));

        String error = null;
        if (fullName.isBlank() || username.isBlank() || email.isBlank() || password.isBlank()) {
            error = "Vui lòng điền đầy đủ họ tên, tên đăng nhập, email và mật khẩu.";
        } else if (!USERNAME_PATTERN.matcher(username).matches()) {
            error = "Tên đăng nhập dài 3–40 ký tự, chỉ gồm chữ không dấu, số, dấu chấm, gạch dưới hoặc gạch ngang.";
        } else if (!EMAIL_PATTERN.matcher(email).matches()) {
            error = "Địa chỉ email chưa đúng định dạng.";
        } else if (password.length() < 12) {
            error = "Mật khẩu khởi tạo cần có ít nhất 12 ký tự.";
        } else if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            error = "Mật khẩu không được vượt quá 72 byte UTF-8.";
        } else if (role == null || !STAFF_ROLES.contains(role.getName())) {
            error = "Chỉ được tạo tài khoản nhân viên cửa hàng hoặc nhân viên kho.";
        } else if (userRepository.findByUsernameIgnoreCase(username).isPresent()) {
            error = "Tên đăng nhập đã được sử dụng.";
        } else if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            error = "Email đã được sử dụng.";
        }

        Store store = null;
        Warehouse warehouse = null;
        if (error == null && "ROLE_STORE_STAFF".equals(role.getName())) {
            Long storeId = parseId(form.get("storeId"));
            store = storeId == null ? null : storeRepository.findById(storeId).orElse(null);
            if (store == null || !store.isActive()) error = "Hãy chọn một chi nhánh đang hoạt động.";
        } else if (error == null && "ROLE_WAREHOUSE".equals(role.getName())) {
            Long warehouseId = parseId(form.get("warehouseId"));
            warehouse = warehouseId == null ? null : warehouseRepository.findById(warehouseId).orElse(null);
            if (warehouse == null || !warehouse.isActive()) error = "Hãy chọn một kho đang hoạt động.";
        }

        if (error != null) {
            prepareCreateModel(model, form);
            model.addAttribute("error", error);
            model.addAttribute("selectedRoleId", form.get("roleId"));
            return "admin/layout";
        }

        User employee = User.builder()
                .fullName(fullName)
                .username(username)
                .email(email)
                .phone(phone.isBlank() ? null : phone)
                .password(passwordEncoder.encode(password))
                .role(role)
                .store(store)
                .warehouse(warehouse)
                .enabled(true)
                .loyaltyPoints(0)
                .authTokenVersion(0)
                .build();
        userRepository.save(employee);
        redirectAttributes.addFlashAttribute("success", "Đã tạo tài khoản nhân viên. Hãy gửi thông tin đăng nhập cho nhân viên qua kênh an toàn.");
        return "redirect:/admin/users";
    }

    @GetMapping("/edit/{id}")
    public String editUser(@PathVariable Long id, Model model) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));

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
    @Transactional
    public String saveUser(@ModelAttribute User user, @RequestParam Long roleId,
                           @RequestParam(required = false) Long storeId,
                           @RequestParam(required = false) Long warehouseId,
                           RedirectAttributes redirectAttributes) {
        User existingUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vai trò."));

        Store store = null;
        Warehouse warehouse = null;
        if ("ROLE_STORE_STAFF".equals(role.getName())) {
            store = storeId == null ? null : storeRepository.findById(storeId).orElse(null);
            if (store == null) {
                redirectAttributes.addFlashAttribute("error", "Hãy gán nhân viên cửa hàng cho một chi nhánh.");
                return "redirect:/admin/users/edit/" + existingUser.getId();
            }
        } else if ("ROLE_WAREHOUSE".equals(role.getName())) {
            warehouse = warehouseId == null ? null : warehouseRepository.findById(warehouseId).orElse(null);
            if (warehouse == null) {
                redirectAttributes.addFlashAttribute("error", "Hãy gán nhân viên kho cho một kho.");
                return "redirect:/admin/users/edit/" + existingUser.getId();
            }
        }

        existingUser.setRole(role);
        existingUser.setEnabled(user.isEnabled());
        existingUser.setFullName(user.getFullName());
        existingUser.setPhone(user.getPhone());
        existingUser.setStore(store);
        existingUser.setWarehouse(warehouse);
        userRepository.save(existingUser);
        redirectAttributes.addFlashAttribute("success", "Đã cập nhật tài khoản.");
        return "redirect:/admin/users";
    }

    @PostMapping("/toggle-status/{id}")
    public String toggleStatus(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
        redirectAttributes.addFlashAttribute("success", user.isEnabled() ? "Đã mở khóa tài khoản." : "Đã khóa tài khoản.");
        return "redirect:/admin/users";
    }

    private void prepareCreateModel(Model model, Map<String, String> form) {
        model.addAttribute("form", form);
        model.addAttribute("roles", roleRepository.findAll().stream()
                .filter(role -> STAFF_ROLES.contains(role.getName())).toList());
        model.addAttribute("stores", storeRepository.findByActiveTrue());
        model.addAttribute("warehouses", warehouseRepository.findByActiveTrue());
        model.addAttribute("title", "Tạo tài khoản nhân viên");
        model.addAttribute("activePage", "users");
        model.addAttribute("content", "admin/users/create");
    }

    private Role findRole(String id) {
        Long roleId = parseId(id);
        return roleId == null ? null : roleRepository.findById(roleId).orElse(null);
    }

    private Long parseId(String value) {
        try { return value == null || value.isBlank() ? null : Long.valueOf(value); }
        catch (NumberFormatException ignored) { return null; }
    }

    private String value(Map<String, String> values, String key) {
        String value = values.get(key);
        return value == null ? "" : value;
    }

    private Map<String, String> emptyForm() {
        Map<String, String> form = new LinkedHashMap<>();
        for (String key : List.of("fullName", "username", "email", "phone", "roleId", "storeId", "warehouseId")) {
            form.put(key, "");
        }
        return form;
    }
}

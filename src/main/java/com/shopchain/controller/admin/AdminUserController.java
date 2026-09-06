package com.shopchain.controller.admin;

import com.shopchain.entity.Role;
import com.shopchain.entity.User;
import com.shopchain.repository.RoleRepository;
import com.shopchain.repository.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public AdminUserController(UserRepository userRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
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
        model.addAttribute("title", "Chỉnh sửa người dùng");
        model.addAttribute("activePage", "users");
        model.addAttribute("content", "admin/users/form");
        return "admin/layout";
    }

    @PostMapping("/save")
    public String saveUser(@ModelAttribute User user, @RequestParam Long roleId) {
        User existingUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        existingUser.setRole(role);
        existingUser.setEnabled(user.isEnabled());
        existingUser.setFullName(user.getFullName());
        existingUser.setPhone(user.getPhone());

        userRepository.save(existingUser);
        return "redirect:/admin/users";
    }

    @GetMapping("/toggle-status/{id}")
    public String toggleStatus(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
        return "redirect:/admin/users";
    }
}

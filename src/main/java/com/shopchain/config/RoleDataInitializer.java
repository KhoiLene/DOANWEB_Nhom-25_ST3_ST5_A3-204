package com.shopchain.config;

import com.shopchain.entity.Role;
import com.shopchain.repository.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

@Component
public class RoleDataInitializer implements CommandLineRunner {
    private final RoleRepository roleRepository;

    public RoleDataInitializer(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    public void run(String... args) {
        Set<String> existing = new HashSet<>();
        roleRepository.findAll().forEach(role -> existing.add(role.getName()));
        List<Role> missing = List.of("ROLE_USER", "ROLE_WAREHOUSE", "ROLE_STORE_STAFF", "ROLE_ADMIN")
                .stream()
                .filter(name -> !existing.contains(name))
                .map(name -> Role.builder().name(name).build())
                .toList();
        if (!missing.isEmpty()) roleRepository.saveAll(missing);
    }
}
